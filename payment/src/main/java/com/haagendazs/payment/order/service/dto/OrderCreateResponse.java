package com.haagendazs.payment.order.service.dto;

import com.haagendazs.payment.order.enums.OrderType;

public record OrderCreateResponse(
        Long orderId,
        String orderNo,
        String orderName,
        Long amount,
        OrderType orderType,
        String customerKey
        //나중에 email 추가해서 결제가 유저의 email로 발송되도록 해야함.
) {
}
