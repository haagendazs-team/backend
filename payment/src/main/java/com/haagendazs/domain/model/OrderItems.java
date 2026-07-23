package com.haagendazs.domain.model;

import com.haagendazs.domain.model.BaseEntity;
import com.haagendazs.domain.model.Orders;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "order_items")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItems extends BaseEntity {

    //주문상품목록id
    @Id
    @GeneratedValue
    private Long id;

    //주문
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Orders order;

    //상품id
    @Column(nullable = false)
    private Long productId;

    //주문당시상품명
    @Column(nullable = false)
    private String itemName;

    //주문당시상품타입
    @Column(nullable = false)
    private String itemType;

    //주문당시상품가격
    @Column(nullable = false)
    private Long unitPrice;

    //수량
    @Column(nullable = false)
    private Long quantity;

    //총가격
    @Column(nullable = false)
    private Long totalPrice;

    public void assignOrder(Orders order) {
        this.order = order;
    }
}
