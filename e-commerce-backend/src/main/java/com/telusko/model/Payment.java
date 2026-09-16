package com.telusko.model;

import jakarta.persistence.*;
import lombok.*;
import com.telusko.enums.PaymentMethod;
import com.telusko.enums.PaymentStatus;

import java.math.BigDecimal;

@Entity
@Table(name = "payments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private PaymentMethod method = PaymentMethod.CARD;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    private String currency = "INR";

    private String provider;
    private String providerRef;
    private String failureReason;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;
}
