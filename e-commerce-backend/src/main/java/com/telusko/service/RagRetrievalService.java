package com.telusko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The retrieval half of RAG: find the indexed records that are closest in meaning to a question.
 * <p>
 * Every AI feature in the app - the customer assistant, product search, recommendations, the cart
 * helper - needs the same two things: only show a user what they are allowed to see, and drop
 * matches that are not actually relevant. Keeping that in one place means there is exactly one
 * file to edit when the rules change, and no feature can accidentally skip them.
 * <p>
 * Access control is expressed as a vector-store filter rather than a Java {@code .filter()} on the
 * results. That matters for correctness, not just speed: filtering afterwards means topK is spent
 * on documents the user may not see, so a user whose nearest neighbours all belong to someone else
 * would get an empty answer even when relevant public documents exist.
 */
@Service
@RequiredArgsConstructor
public class RagRetrievalService {

    private final VectorStore vectorStore;
    private static final double MIN_SIMILARITY = 0.3;
    private static final double CATALOG_MIN_SIMILARITY = 0.2;

    public List<Document> searchCatalog(String query, int topK) {
        if (topK <= 0) topK = 12;

        FilterExpressionBuilder b = new FilterExpressionBuilder();

        // Discontinued products are excluded here rather than after the search. Soft-deleting a
        // product hides it from the catalog listing, but smart search kept offering it, so a
        // shopper could open a product page for something the store had withdrawn.
        Filter.Expression filter = b.and(
                b.and(b.eq("type", "PRODUCT"), b.eq("visibility", "PUBLIC")),
                b.eq("active", true)
        ).build();

        List<Document> matches = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(CATALOG_MIN_SIMILARITY)
                        .filterExpression(filter)
                        .build());

        return observed("catalog-search", matches);
    }
    private List<Document> observed(String feature, List<Document> matches) {
        List<Document> safe = matches == null ? List.of() : matches;
        return safe;
    }
    /**
     * Joins matches into one block of text ready to drop into a prompt.
     * Returns an empty string when nothing matched, which callers use to decide whether to fall
     * back to general knowledge instead of pretending they have grounding.
     */
    public String asContext(List<Document> matches) {
        if (matches == null || matches.isEmpty()) {
            return "";
        }
        return matches.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n----\n"));
    }

    public List<Document> searchCatalogExcluding(String query, List<Long> excludedProductIds, int topK) {
        if (topK <= 0) topK = 5;

        FilterExpressionBuilder b = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op filter = b.and(
                b.eq("type", "PRODUCT"),
                b.eq("active", true)
        );

        if (excludedProductIds != null && !excludedProductIds.isEmpty()) {
            filter = b.and(filter, b.nin("productId", excludedProductIds.stream()
                    .map(id -> (Object) id)
                    .toList()));
        }

        List<Document> matches = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(MIN_SIMILARITY)
                        .filterExpression(filter.build())
                        .build());

        return observed("recommendations", matches);
    }
    public List<Document> searchForUser(String query, String userEmail, boolean isAdmin, int topK) {
        if (topK <= 0) topK = 5;

        FilterExpressionBuilder b = new FilterExpressionBuilder();

        SearchRequest.Builder request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(MIN_SIMILARITY);

        if (!isAdmin) {
            // "public, or mine" - the whole access rule in one expression.
            Filter.Expression onlyWhatTheyMaySee = b.or(
                    b.eq("visibility", "PUBLIC"),
                    b.eq("userEmail", userEmail == null ? "" : userEmail)
            ).build();
            request.filterExpression(onlyWhatTheyMaySee);
        }

        List<Document> matches = vectorStore.similaritySearch(request.build());
        return observed("assistant", matches);
    }
}
