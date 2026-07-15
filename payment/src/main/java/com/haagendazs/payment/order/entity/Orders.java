package com.haagendazs.payment.order.entity;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.payment.global.BaseEntity;
import com.haagendazs.payment.global.PaymentErrorCode;
import com.haagendazs.payment.order.enums.OrderStatus;
import com.haagendazs.payment.order.enums.OrderType;
import com.haagendazs.payment.product.entity.OrderItems;
import jakarta.persistence.*;

import java.time.LocalDateTime;

import java.util.ArrayList;

import java.util.List;

import lombok.*;

@Entity
@Table(
        name = "orders",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_orders_member_workspace_idempotency",
                        columnNames = {"member_id", "workspace_id", "idempotency_key"}
                )
        }
)
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

    //주문 생성 멱등키. 같은 checkout 시도에서 중복 주문 생성을 막기 위해 사용합니다.
    @Column(name = "idempotency_key", length = 300)
    private String idempotencyKey;

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

    public void complete() {
        this.orderStatus = OrderStatus.PAID;
    }

    public void markProcessing() {
        this.orderStatus = OrderStatus.PROCESSING;
    }

    public void markRetryScheduled() {
        this.orderStatus = OrderStatus.RETRY_SCHEDULED;
    }

    public void fail() {
        this.orderStatus = OrderStatus.FAILED;
    }

    public void markReconcileRequired() {
        this.orderStatus = OrderStatus.RECONCILE_REQUIRED;
    }

    //구독상품의 상품 id 추출
    public Long getSubscriptionProductId() {
        return this.orderItems.stream()
                .filter(item -> "SUBSCRIPTION".equals(item.getItemType())) // 주문 당시 타입 체크
                .map(OrderItems::getProductId)
                .findFirst()
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PRODUCT_NOT_FOUND));
        // 💡 구독 결제 메서드인데 구독 상품이 없으면 문제가 있는 것이므로 예외 처리
    }
}
