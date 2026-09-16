package com.telusko.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.telusko.dto.CreateProductRequest;
import com.telusko.dto.ProductResponseDto;
import com.telusko.dto.UpdateProductRequest;
import com.telusko.model.Category;
import com.telusko.model.Product;
import com.telusko.repository.CategoryRepository;
import com.telusko.repository.ProductRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final CloudinaryService cloudinary;
    private final AppVectorStoreService appVectors;

    private static final int SEARCH_CANDIDATE_POOL = 12;

    private final RagRetrievalService ragRetrieval;
    private final ResourceLoader resourceLoader;
    private final AIImageGeneratorService aiImageGenerator;

    /**
     * Memory-free client. Product search is a one-shot call, and the conversational client's
     * memory advisor rejects calls that have no conversation id.
     */
    @Qualifier("oneShotChatClient")
    private final ChatClient chatClient;
    public List<ProductResponseDto> semanticSearchProducts(String userQuery) {
        // An empty search box has nothing to embed. This reached the vector store and came back as
        // a 500, which is the wrong answer to "you searched for nothing".
        if (userQuery == null || userQuery.isBlank()) {
            return List.of();
        }

        try {
            // Read as a stream, not a File. getFile() only works while the prompt sits on disk in
            // target/classes, which is the case when the app runs from an IDE and never once it is
            // packaged: inside a jar the resource has no file path, so getFile() throws and every
            // search answered 500.
            String promptTemplate = resourceLoader
                    .getResource("classpath:prompts/product-search-prompt.st")
                    .getContentAsString(StandardCharsets.UTF_8);

            // Catalog-only retrieval. This previously searched the whole store unfiltered, so a
            // plain product query could pull another customer's order, cart or address into the
            // context sent to the model.
            List<Document> candidates =
                    ragRetrieval.searchCatalog(userQuery, SEARCH_CANDIDATE_POOL);

            // Nothing in the catalog is close to the query, so the model has nothing to choose
            // from. Asking it anyway spent a request to be told the same thing, and left it free to
            // answer with ids that were never offered - which then resolved to real products the
            // shopper had not searched for.
            if (candidates.isEmpty()) {
                return List.of();
            }

            String context = ragRetrieval.asContext(candidates);

            Map<String, Object> variables = new HashMap<>();
            variables.put("userQuery", userQuery);
            variables.put("context", context);

            PromptTemplate template = PromptTemplate.builder()
                    .template(promptTemplate)
                    .variables(variables)
                    .build();

            // Temperature zero, because search results have to be repeatable. On the configured
            // default the same query came back in a different order between refreshes, and
            // borderline products appeared and disappeared - which reads as a broken search rather
            // than a creative one. This is selection, not writing; there is nothing to vary.
            Prompt prompt = new Prompt(
                    template.createMessage(),
                    ChatOptions.builder().temperature(0.0).build());

            // Timed like every other AI feature, so the dashboard's per-feature panels cover search
            // too. Retrieval is already measured separately as "catalog-search"; this is the
            // re-ranking call that turns those candidates into the results the shopper sees, and
            // without it a slow or failing search would show up only as anonymous model latency.
            Generation generation = chatClient.prompt(prompt)
                    .call()
                    .chatResponse()
                    .getResult();

            String modelOutput = generation.getOutput().getText();

            BeanOutputConverter<List<Long>> outputConverter =
                    new BeanOutputConverter<>(new ParameterizedTypeReference<>() {});

            // convert returns null when the reply is not the JSON array we asked for. Dereferencing
            // that turned a chatty model into a 500 for the shopper.
            List<Long> converted = outputConverter.convert(modelOutput);

            List<Long> productIds = converted == null ? List.<Long>of() : converted.stream()
                    .filter(id -> id != null && id > 0)
                    .toList();

            if (productIds.isEmpty()) {
                return List.of();
            }

            Map<Long, Product> byId = new HashMap<>();
            products.findActiveByIdWithCategory(productIds)
                    .forEach(p -> byId.put(p.getId(), p));

            // Walk the model's ids, not the rows the database returned. The model ranked them best
            // match first and "where id in (:ids)" gives back whatever order Postgres finds
            // convenient, so the ranking was being thrown away before it reached the shopper.
            return productIds.stream()
                    .distinct()
                    .map(byId::get)
                    .filter(Objects::nonNull)
                    .map(this::toDto)
                    .toList();

        } catch (IOException e) {
            throw new RuntimeException("Failed to process query", e);
        } catch (Exception e) {
            throw new RuntimeException("AI semantic search failed", e);
        }
    }


    public Page<ProductResponseDto> getAllProducts(int page, int limit) {

        if (page < 1) page = 1;
        if (limit < 1) limit = 10;

        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.ASC, "name"));

        return products.findByActiveTrue(pageable)
                .map(this::toDto);
    }

    // Public so any other feature that needs to return products uses the same shape the REST
    // endpoints do, instead of maintaining a second mapping that could drift.
    public ProductResponseDto toDto(Product p) {
        Category c = p.getCategory();

        return ProductResponseDto.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .stockQty(p.getStockQty())
                .sku(p.getSku())
                .active(p.getActive())
                .mainImage(p.getMainImage())
                .subImages(p.getSubImages())
                .categoryId(c != null ? c.getId() : null)
                .categoryName(c != null ? c.getName() : null)
                .build();
    }


    public ProductResponseDto createProduct(CreateProductRequest req,
                                            MultipartFile mainImage,
                                            List<MultipartFile> subImages) {

        Category category = categories.findById(req.getCategory())
                .orElseThrow(() ->
                        new IllegalArgumentException("Category not found with id: " + req.getCategory()));

        String mainImageUrl = cloudinary.uploadToCloudinary(mainImage, "products/main");

        List<String> subImageUrls = new ArrayList<>();
        if (subImages != null) {
            for (int i = 0; i < subImages.size() && i < 4; i++) {
                MultipartFile file = subImages.get(i);
                if (!file.isEmpty()) {
                    subImageUrls.add(cloudinary.uploadToCloudinary(file, "products/sub"));
                }
            }
        }

        Product product = Product.builder()
                .name(req.getName())
                .description(req.getDescription())
                .price(req.getPrice())
                .stockQty(req.getStock())
                .sku("SKU-" + UUID.randomUUID().toString()
                        .substring(0, 8).toUpperCase())
                .active(true)
                .mainImage(mainImageUrl)
                .subImages(subImageUrls)
                .category(category)
                .build();

        Product saved = products.save(product);

        appVectors.indexProduct(saved);

        return toDto(saved);
    }

    public ProductResponseDto getProductById(Long productId) {
        Product product = products.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Product not found with id: " + productId));
        return toDto(product);
    }

    public void deleteProduct(Long productId) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Product not found with id: " + productId));
        product.setActive(false);
        products.save(product);
        appVectors.indexProduct(product);
    }

    public ProductResponseDto updateProduct(Long productId,
                                            UpdateProductRequest req,
                                            MultipartFile mainImage,
                                            List<MultipartFile> subImages) {

        Product product = products.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Product not found with id: " + productId));

        if (req.getName() != null && !req.getName().isBlank()) {
            product.setName(req.getName());
        }
        if (req.getDescription() != null) {
            product.setDescription(req.getDescription());
        }
        if (req.getPrice() != null) {
            product.setPrice(req.getPrice());
        }
        if (req.getStock() != null) {
            product.setStockQty(req.getStock());
        }
        if (req.getActive() != null) {
            product.setActive(req.getActive());
        }
        if (req.getCategory() != null) {
            Category category = categories.findById(req.getCategory())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Category not found with id: " + req.getCategory()));
            product.setCategory(category);
        }

        if (mainImage != null && !mainImage.isEmpty()) {
            String mainUrl = cloudinary.uploadToCloudinary(mainImage, "products/main");
            product.setMainImage(mainUrl);
        }

        if (subImages != null && !subImages.isEmpty()) {
            List<String> urls = new ArrayList<>();
            for (int i = 0; i < subImages.size() && i < 4; i++) {
                MultipartFile file = subImages.get(i);
                if (!file.isEmpty()) {
                    urls.add(cloudinary.uploadToCloudinary(file, "products/sub"));
                }
            }
            if (!urls.isEmpty()) {
                product.setSubImages(urls);
            }
        }

        Product saved = products.save(product);
        appVectors.indexProduct(saved);


        return toDto(saved);
    }

    public Page<ProductResponseDto> getProductsByCategory(Long categoryId,
                                                          int page,
                                                          int limit) {
        if (page < 1) page = 1;
        if (limit < 1) limit = 5;

        categories.findByIdAndActiveTrue(categoryId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Category not found with id: " + categoryId));

        Pageable pageable = PageRequest.of(page - 1, limit,
                Sort.by(Sort.Direction.ASC, "name"));

        return products.findByCategoryIdAndActiveTrue(categoryId, pageable)
                .map(this::toDto);
    }

    /**
     * Keyword search across the catalog.
     * <p>
     * A plain SQL match on name, description and category name, case insensitive. It finds what the
     * shopper literally typed: "mouse" finds the Mouse, and "something for my desk" finds nothing,
     * because no product contains those words. That limitation is the whole reason semantic search
     * exists, and this is the method it replaces.
     */
    public List<ProductResponseDto> searchProducts(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        return products.searchActive(query.trim()).stream()
                .map(this::toDto)
                .toList();
    }

    public String generateDescription(String name, String category, String brand) {
        String descPrompt = String.format("""
                Write a concise and professional product description for an e-commerce listing.
                
                Brand: %s
                Product Name: %s
                Category: %s
                
                Rules:
                - Keep it simple, engaging, and customer-friendly.
                - Mention the brand naturally (only once).
                - Highlight 2-3 primary features or benefits.
                - Avoid technical jargon.
                - Maximum 250 characters.
                """, brand, name, category);


        return Objects.requireNonNull(chatClient.prompt(descPrompt)
                        .call()
                        .chatResponse())
                .getResult()
                .getOutput()
                .getText();

    }

    public byte[] generateImage(String name, String category, String description, String brand) {
        String imagePrompt = String.format("""
                Generate a highly realistic, professional-grade e-commerce product image.
                
                Product Details:
                - Category: %s
                - Name: '%s'
                - Description: %s
                
                Requirements:
                  - Use a clean, minimalistic, white or very light grey background.
                  - Ensure the product is well-lit with soft, natural-looking lighting.
                  - Add realistic shadows and soft reflections to ground the product naturally.
                  - No humans, watermarks, QR codes, or extra text overlays should be visible.
                  - Add a subtle, realistic brand logo on the product itself (printed/engraved/stitched), using the brand name: "%s".
                  - The logo must look like part of the real product packaging/design (not floating text).
                  - Keep the logo small and natural (not dominating the image).
                  - Showcase the product from its most flattering angle that highlights key features.
                  - Ensure the product occupies a prominent position in the frame, centered or slightly off-centered.
                  - Maintain a high resolution and sharpness, ensuring all textures, colors, and details are clear.
                  - Follow the typical visual style of top e-commerce websites like Amazon, Flipkart, or Shopify.
                  - Make the product appear life-like and professionally photographed in a studio setup.
                  - The final image should look immediately ready for use on an e-commerce website without further editing.
                """, category, name, description, brand);

        return aiImageGenerator.generateImage(imagePrompt);
    }

}
