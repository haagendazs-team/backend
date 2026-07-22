package com.haagendazs.presentation.controller;

import com.haagendazs.common.response.ApiResponse;
import com.haagendazs.application.service.BillingCheckoutService;
import com.haagendazs.application.service.BillingMethodService;
import com.haagendazs.application.service.BillingPaymentService;
import com.haagendazs.application.dto.BillingMethodIssueAndPayRequest;
import com.haagendazs.application.dto.BillingMethodIssueRequest;
import com.haagendazs.application.dto.BillingMethodPrepareResponse;
import com.haagendazs.application.dto.BillingPaymentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final BillingMethodService billingMethodService;
    private final BillingPaymentService billingPaymentService;
    private final BillingCheckoutService billingCheckoutService;

    //자동결제 결제수단 등록 준비
    @PostMapping("/billing-methods/prepare")
    public ApiResponse<BillingMethodPrepareResponse> prepareBillingMethodRegistration(
            @RequestHeader("X-Member-Id")
            Long memberId
    ){
        BillingMethodPrepareResponse response =
                billingMethodService.prepareRegistration(memberId);

        return ApiResponse.ok(response);
    }

    //자동결제 결제수단만 등록합니다. 마이페이지 등 주문 결제와 분리된 등록 흐름에서 사용합니다.
    @PostMapping("/billing-methods/confirm")
    public ApiResponse<Void> issueBillingMethod(
            @RequestHeader("X-Member-Id")
            Long memberId,
            @RequestBody @Valid
            BillingMethodIssueRequest request
    ) {
        billingMethodService.issueBillingMethod(memberId, request);
        return ApiResponse.ok();
    }

    //주문 중 자동결제 결제수단을 등록한 뒤, 같은 요청에서 해당 주문까지 결제합니다.
    @PostMapping("/billing-methods/confirm-and-pay")
    public ApiResponse<Void> issueBillingMethodAndPay(
            @RequestHeader("X-Member-Id")
            Long memberId,
            @RequestBody @Valid
            BillingMethodIssueAndPayRequest request
    ) {
        billingCheckoutService.issueBillingMethodAndPay(memberId, request);
        return ApiResponse.ok();
    }

    //자동결제 결제수단 삭제
    @DeleteMapping("/billing-methods/{billingId}")
    public ApiResponse<Void> deleteBillingMethod(
            @RequestHeader("X-Member-Id")
            Long memberId,
            @PathVariable
            Long billingId
    ) {
        billingMethodService.deleteBillingMethod(memberId, billingId);
        return ApiResponse.ok();
    }

    //이미 등록된 기본 자동결제 결제수단으로 주문을 결제합니다.
    @PostMapping("/billing")
    public ApiResponse<Void> payWithRegisteredBillingMethod(
            @RequestHeader("X-Member-Id")
            Long memberId,
            @RequestBody @Valid
            BillingPaymentRequest request
    ) {
        billingPaymentService.payWithRegisteredBillingMethod(memberId, request);
        return ApiResponse.ok();
    }
}
