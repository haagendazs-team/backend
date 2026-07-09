package com.haagendazs.payment.subscription.service.dto;

import com.haagendazs.payment.order.enums.OrderType;
import com.haagendazs.payment.subscription.enums.SubscriptionChangeAction;

public record ChangeSubscriptionResponse(
        SubscriptionChangeAction action,
        Long orderId,
        String orderNo,
        String orderName,
        Long amount,
        OrderType orderType,
        String customerKey
) {
    public static ChangeSubscriptionResponse scheduled() {
        return new ChangeSubscriptionResponse(
                SubscriptionChangeAction.SCHEDULED,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static ChangeSubscriptionResponse paymentRequired(
            Long orderId,
            String orderNo,
            String orderName,
            Long amount,
            OrderType orderType,
            String customerKey
    ) {
        return new ChangeSubscriptionResponse(
                SubscriptionChangeAction.PAYMENT_REQUIRED,
                orderId,
                orderNo,
                orderName,
                amount,
                orderType,
                customerKey
        );
    }
}
