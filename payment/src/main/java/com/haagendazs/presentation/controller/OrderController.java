package com.haagendazs.presentation.controller;

import com.haagendazs.common.response.ApiResponse;
import com.haagendazs.application.service.OrderService;
import com.haagendazs.application.dto.OrderCreateRequest;
import com.haagendazs.application.dto.OrderCreateResponse;
import com.haagendazs.application.dto.OrderDetailResponse;
import com.haagendazs.application.dto.OrderListResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments/workspaces/{workspaceId}/orders")
public class OrderController {

    private final OrderService orderService;

    //구독 주문
    @PostMapping("/subscription")
    public ApiResponse<OrderCreateResponse> createSubscriptionOrder(
            @RequestHeader("X-Member-Id")
            Long memberId,
            @RequestHeader("Idempotency-Key")
            String idempotencyKey,
            @PathVariable
            Long workspaceId,
            @RequestBody @Valid
            OrderCreateRequest request
    ){
        OrderCreateResponse response = orderService.createOrder(
                memberId,
                workspaceId,
                request,
                idempotencyKey
        );
        return ApiResponse.ok(response);
    }

    //본인 주문 목록 조회
    @GetMapping
    public ApiResponse<List<OrderListResponse>> getMyOrders(
            @RequestHeader("X-Member-Id")
            Long memberId,
            @PathVariable
            Long workspaceId
    ) {
        return ApiResponse.ok(orderService.getMyOrders(memberId, workspaceId));
    }

    //본인 주문 상세 조회. 해당 주문에 연결된 결제 내역도 함께 반환합니다.
    @GetMapping("/{orderNo}")
    public ApiResponse<OrderDetailResponse> getMyOrderDetail(
            @RequestHeader("X-Member-Id")
            Long memberId,
            @PathVariable
            Long workspaceId,
            @PathVariable
            String orderNo
    ) {
        return ApiResponse.ok(orderService.getMyOrderDetail(memberId, workspaceId, orderNo));
    }
}
