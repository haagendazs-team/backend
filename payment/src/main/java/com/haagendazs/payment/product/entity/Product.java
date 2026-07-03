package com.haagendazs.payment.product.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductType productType;

    @Column(nullable = false)
    private Long price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    // 외래키(FK)인 경우 연관관계 매핑(@ManyToOne 등)을 권장하지만,
    // 이미지상의 단일 테이블 명세에 맞춰 기본 타입으로 작성했습니다.
    @Column(name = "subscription_plan_id")
    private Long subscriptionPlanId;
}
