package com.haagendazs.payment.controller;

import com.haagendazs.common.response.ApiResponse;
import com.haagendazs.payment.payment.service.PaymentService;
import com.haagendazs.payment.payment.service.dto.BillingKeyIssueRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payment")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/billing/issue")
    public ApiResponse<Void> requestBillingKeyIssue(
            @RequestHeader("X-Member-Id")
            Long memberId,
            @RequestBody @Valid
            BillingKeyIssueRequest request
    ){
        paymentService.billingKeyIssue(memberId, request);
        return ApiResponse.ok();
    }
}
