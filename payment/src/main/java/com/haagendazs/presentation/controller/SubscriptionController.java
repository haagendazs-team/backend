package com.haagendazs.presentation.controller;

import com.haagendazs.common.response.ApiResponse;
import com.haagendazs.application.service.SubscriptionService;
import com.haagendazs.application.dto.ChangeSubscriptionRequest;
import com.haagendazs.application.dto.ChangeSubscriptionResponse;
import com.haagendazs.application.dto.GetSubscriptionResponse;
import com.haagendazs.application.dto.GetsubscriptionPeriodsResponse;
import com.haagendazs.application.dto.ScheduledPlanChangeResponse;
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

    //워크스페이스의 예약된 구독 플랜 변경 조회
    @GetMapping("/plan-change")
    public ApiResponse<ScheduledPlanChangeResponse> getScheduledPlanChange(
            @PathVariable
            Long workspaceId
    ) {
        return ApiResponse.ok(subscriptionService.getScheduledPlanChange(workspaceId));
    }

    //워크스페이스의 예약된 구독 플랜 변경 취소
    @DeleteMapping("/plan-change")
    public ApiResponse<Void> cancelScheduledPlanChange(
            @RequestHeader("X-Member-Id")
            Long memberId,
            @PathVariable
            Long workspaceId
    ) {
        subscriptionService.cancelScheduledPlanChange(memberId, workspaceId);

        return ApiResponse.ok();
    }

}
