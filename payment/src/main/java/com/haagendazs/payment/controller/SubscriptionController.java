package com.haagendazs.payment.controller;

import com.haagendazs.common.response.ApiResponse;
import com.haagendazs.payment.subscription.service.SubscriptionService;
import com.haagendazs.payment.subscription.service.dto.ChangeSubscriptionRequest;
import com.haagendazs.payment.subscription.service.dto.ChangeSubscriptionResponse;
import com.haagendazs.payment.subscription.service.dto.GetSubscriptionResponse;
import com.haagendazs.payment.subscription.service.dto.GetsubscriptionPeriodsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments/workspaces/{workspaceId}/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    //워크스페이스의 현재 구독 조회
    @GetMapping
    public ApiResponse<GetSubscriptionResponse> getSubscriptions(
            @PathVariable
            Long workspaceId
    ) {
        return ApiResponse.ok(subscriptionService.getWorkspaceSubscription(workspaceId));
    }

    //워크스페이스의 구독 이력 조회
    @GetMapping("/history")
    public ApiResponse<List<GetsubscriptionPeriodsResponse>> getsubscriptionPeriods(
            @PathVariable
            Long workspaceId
    ) {
        return ApiResponse.ok(subscriptionService.getSubscriptionPeriods(workspaceId));
    }

    //워크스페이스의 구독 플랜 변경
    @PostMapping("/plan-change")
    public ApiResponse<ChangeSubscriptionResponse> changeSubscription(
            @RequestHeader("X-Member-Id")
            Long memberId,
            @PathVariable
            Long workspaceId,
            @RequestBody @Valid
            ChangeSubscriptionRequest changeSubscriptionRequest
    ){
        ChangeSubscriptionResponse response =
                subscriptionService.changeSubscription(memberId, workspaceId, changeSubscriptionRequest);

        return ApiResponse.ok(response);
    }

}
