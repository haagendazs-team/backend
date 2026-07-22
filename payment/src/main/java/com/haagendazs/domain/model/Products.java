package com.haagendazs.domain.model;

import com.haagendazs.domain.model.BaseEntity;
import com.haagendazs.domain.model.ProductStatus;
import com.haagendazs.domain.model.ProductType;
import jakarta.persistence.*;

import lombok.*;

@Entity
@Table(name = "products")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Products extends BaseEntity {
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

    @Column(nullable = false)
    private Long product_detail_id;
}
