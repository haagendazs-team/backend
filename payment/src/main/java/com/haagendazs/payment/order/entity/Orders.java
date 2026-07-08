package com.haagendazs.payment.order.entity;

import com.haagendazs.payment.global.BaseEntity;
import com.haagendazs.payment.order.enums.OrderStatus;
import com.haagendazs.payment.order.enums.OrderType;
import com.haagendazs.payment.product.entity.OrderItems;
import jakarta.persistence.*;

import java.time.LocalDateTime;

import java.util.ArrayList;

import java.util.List;

import lombok.*;

@Entity
@Table(name = "orders")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Orders extends BaseEntity {
    //주문id
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //멤버id
    @Column(nullable = false)
    private Long memberId;

    //워크스페이스id
    @Column
    private Long workspaceId;

    //주문번호
    @Column(nullable = false, unique = true)
    private String orderNo;

    //주문상품목록
    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItems> orderItems = new ArrayList<>();

    //총 금액
    @Column(nullable = false)
    private Long totalAmount;

    //주문상태
    @Column(nullable = false)
    private OrderStatus orderStatus;

    //주문타입
    @Column(nullable = false)
    private OrderType orderType;

    //주문시간
    @Column(nullable = false)
    private LocalDateTime orderedAt;

    //결제가능만료시간
    //주문시간 + 30분
    @Column(nullable = false)
    private LocalDateTime expiredAt;

    public void addOrderItem(OrderItems orderItem) {
        orderItems.add(orderItem);
        orderItem.assignOrder(this);
    }
}
