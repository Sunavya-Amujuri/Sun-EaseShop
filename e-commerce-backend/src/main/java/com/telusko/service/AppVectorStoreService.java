package com.telusko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import com.telusko.model.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AppVectorStoreService {

    private final VectorStore vectorStore;

    /**
     * Writes one document per entity using a stable id such as "PRODUCT-12".
     * <p>
     * This is what keeps the store clean. PgVector inserts with
     * {@code ON CONFLICT (id) DO UPDATE}, so re-indexing the same entity overwrites its row
     * instead of adding another copy. Without a stable id every save generated a fresh UUID,
     * so editing a product five times left five documents behind - four of them stale - and
     * retrieval would happily feed the model outdated prices and stock levels.
     */
    private void upsert(String type, Object entityId, String content, Map<String, Object> meta) {
        vectorStore.add(List.of(new Document(documentId(type, entityId), content, meta)));
    }

    private String documentId(String type, Object entityId) {
        return type + "-" + entityId;
    }

    /** Drops an entity's document, for when the entity itself is gone. */
    public void remove(String type, Object entityId) {
        vectorStore.delete(List.of(documentId(type, entityId)));
    }
    private String safe(String s) {
        return s != null ? s : "";
    }
    public void indexProduct(Product p) {
        String content = """
                Product #%d: %s
                Description: %s
                Category: %s
                Price: %s
                Stock quantity: %d
                Active: %s
                """.formatted(
                p.getId(),
                p.getName(),
                safe(p.getDescription()),
                p.getCategory() != null ? p.getCategory().getName() : "None",
                p.getPrice(),
                p.getStockQty(),
                p.getActive()
        );

        Map<String, Object> meta = new HashMap<>();
        meta.put("type", "PRODUCT");
        meta.put("productId", p.getId());
        meta.put("visibility", "PUBLIC");  // <- public catalog
        // no userEmail
        // Recommendations narrow by category and skip discontinued items, so both need to be
        // filterable metadata rather than something the model has to infer from the text.
        meta.put("categoryId", p.getCategory() != null ? p.getCategory().getId() : 0L);
        meta.put("active", p.getActive() != null ? p.getActive() : false);

        upsert("PRODUCT", p.getId(), content, meta);
    }
    public void indexCategory(Category c) {
        if (c == null) return;

        String content = """
        Category #%d: %s
        Description: %s
        Active: %s
        """.formatted(
                c.getId(),
                safe(c.getName()),
                safe(c.getDescription()),
                c.getActive()
        );

        Map<String, Object> meta = new HashMap<>();
        meta.put("type", "CATEGORY");
        meta.put("categoryId", c.getId());
        meta.put("visibility", "PUBLIC");   // 🟢 visible to everyone

        upsert("CATEGORY", c.getId(), content, meta);
    }
//indexes customer orders into the vector store as private documents, enabling secure, personalized AI recommendations based on purchase history.
    public void indexOrder(Order o) {
        StringBuilder itemsText = new StringBuilder();
        if (o.getItems() != null) {
            o.getItems().forEach(oi -> itemsText.append(
                    "Item: " + oi.getProductName() +
                            ", qty=" + oi.getQuantity() +
                            ", lineTotal=" + oi.getLineTotal() + "\n"
            ));
        }

        String content = """
                Order #%d (%s)
                User id: %d
                Status: %s
                Total amount: %s
                Placed at: %s
                Items:
                %s
                """.formatted(
                o.getId(),
                o.getOrderNumber(),
                o.getUser().getId(),
                o.getStatus(),
                o.getTotalAmount(),
                o.getPlacedAt(),
                itemsText
        );

        Map<String, Object> meta = new HashMap<>();
        meta.put("type", "ORDER");
        meta.put("orderId", o.getId());
        meta.put("userId", o.getUser().getId());
        meta.put("userEmail", o.getUser().getEmail());
        meta.put("visibility", "PRIVATE");   // <- only this user (and admins)
        meta.put("status", o.getStatus() != null ? o.getStatus().name() : "");

        upsert("ORDER", o.getId(), content, meta);
    }

    public void indexCart(Cart cart) {
        if (cart.getUser() == null) return;

        StringBuilder itemsText = new StringBuilder();
        if (cart.getItems() != null && !cart.getItems().isEmpty()) {
            cart.getItems().forEach(ci -> itemsText.append(
                    "Item: " + ci.getProduct().getName() +
                            " (productId=" + ci.getProduct().getId() + ")" +
                            ", qty=" + ci.getQuantity() +
                            ", unitPrice=" + ci.getUnitPrice() +
                            ", lineTotal=" + ci.getUnitPrice()
                            .multiply(BigDecimal.valueOf(ci.getQuantity())) +
                            "\n"
            ));
        } else {
            itemsText.append("Cart is currently empty.\n");
        }

        String content = """
            Cart for user %s (userId=%d)
            Total amount: %s
            Items:
            %s
            """.formatted(
                cart.getUser().getEmail(),
                cart.getUser().getId(),
                cart.getTotalAmount(),
                itemsText
        );

        Map<String, Object> meta = new HashMap<>();
        meta.put("type", "CART");
        meta.put("cartId", cart.getId());
        meta.put("userId", cart.getUser().getId());
        meta.put("userEmail", cart.getUser().getEmail());
        meta.put("visibility", "PRIVATE");

        upsert("CART", cart.getId(), content, meta);
    }

    public void indexAddress(Address a) {
        if (a == null || a.getUser() == null) return;

        String content = """
            Address #%d for user %s (userId=%d)
            Label: %s
            Line1: %s
            Line2: %s
            City: %s
            State: %s
            Country: %s
            Pincode: %s
            """.formatted(
                a.getId(),
                a.getUser().getEmail(),
                a.getUser().getId(),
                safe(a.getLabel()),
                safe(a.getLine1()),
                safe(a.getLine2()),
                safe(a.getCity()),
                safe(a.getState()),
                safe(a.getCountry()),
                safe(a.getPinCode())
        );

        Map<String, Object> meta = new HashMap<>();
        meta.put("type", "ADDRESS");
        meta.put("addressId", a.getId());
        meta.put("userId", a.getUser().getId());
        meta.put("userEmail", a.getUser().getEmail());
        meta.put("label", safe(a.getLabel()));
        meta.put("isDefault", a.getIsDefault() != null ? a.getIsDefault() : false);
        meta.put("visibility", "PRIVATE");  // 🔒 only for that user (or admin)

        upsert("ADDRESS", a.getId(), content, meta);
    }
    public void indexCoupon(Coupon c) {
        String content = """
                Coupon #%d with code %s
                Discount type: %s
                Discount amount: %s
                Minimum order amount: %s
                Active: %s
                Valid from %s to %s
                """.formatted(
                c.getId(), c.getCode(), c.getDiscountType(),
                c.getDiscountAmount(), c.getMinimumOrderAmount(),
                c.getActive(),
                c.getStartDate(), c.getExpiryDate()
        );

        Map<String, Object> meta = new HashMap<>();
        meta.put("type", "COUPON");
        meta.put("couponId", c.getId());
        meta.put("code", c.getCode());
        meta.put("visibility", "PUBLIC");   // everyone can know coupon info

        upsert("COUPON", c.getId(), content, meta);
    }

    public void indexTicket(SupportTicket t) {
        String content = """
                Support ticket #%d
                Subject: %s
                Description: %s
                Status: %s
                Category: %s
                Priority: %s
                Summary: %s
                Related order id: %s
                Assigned to: %s
                """.formatted(
                t.getId(),
                t.getSubject(),
                safe(t.getDescription()),
                t.getStatus(),
                t.getCategory() != null ? t.getCategory() : "not triaged",
                t.getPriority() != null ? t.getPriority() : "not triaged",
                safe(t.getAiSummary()),
                t.getOrder() != null ? t.getOrder().getId() : "none",
                t.getAssignedToEmail() != null ? t.getAssignedToEmail() : "not assigned"
        );

        Map<String, Object> meta = new HashMap<>();
        meta.put("type", "TICKET");
        meta.put("ticketId", t.getId());
        meta.put("userId", t.getUser().getId());
        meta.put("userEmail", t.getUser().getEmail());
        meta.put("status", t.getStatus().name());
        meta.put("visibility", "PRIVATE");
        meta.put("priority", t.getPriority() != null ? t.getPriority().name() : "");
        meta.put("category", t.getCategory() != null ? t.getCategory().name() : "");

        upsert("TICKET", t.getId(), content, meta);
    }
}
