package com.haagendazs.payment.controller;

import com.haagendazs.common.response.ApiResponse;
import com.haagendazs.payment.payment.service.BillingMethodService;
import com.haagendazs.payment.payment.service.BillingPaymentService;
import com.haagendazs.payment.payment.service.dto.BillingMethodIssueRequest;
import com.haagendazs.payment.payment.service.dto.BillingMethodPrepareResponse;
import com.haagendazs.payment.payment.service.dto.BillingPaymentRequest;
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

    //자동결제 결제수단 빌링키 발급
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

    //등록된 자동결제 결제수단으로 결제
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
