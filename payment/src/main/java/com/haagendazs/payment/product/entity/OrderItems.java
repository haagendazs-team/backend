package com.haagendazs.payment.product.entity;

import com.haagendazs.payment.global.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

public class OrderItems extends BaseEntity {

    //주문상품목록id
    @Id
    @GeneratedValue
    private Long id;

    //주문id
    @Column(nullable = false)
    private Long orderId;

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
}
