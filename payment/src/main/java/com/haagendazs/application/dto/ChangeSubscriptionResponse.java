package com.haagendazs.application.dto;

import com.haagendazs.domain.model.OrderType;
import com.haagendazs.domain.model.SubscriptionChangeAction;

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
