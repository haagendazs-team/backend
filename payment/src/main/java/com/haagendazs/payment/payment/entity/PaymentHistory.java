package com.haagendazs.payment.payment.entity;

import com.haagendazs.payment.payment.enums.EventType;
import com.haagendazs.payment.payment.enums.PaymentStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

//결제상태변화로그
@Entity
@Table(name = "payment_history")
@Getter
@Builder
@AllArgsConstructor
public class PaymentHistory {

    //로그id
    @Id
    @GeneratedValue
    private Long id;

    //결제id
    @Column(nullable = false)
    private Long paymentId;

    //이벤트 종류
    @Column(nullable = false)
    private EventType eventType;

    //이전 상태
    @Column(nullable = true)
    private PaymentStatus beforePaymentStatus;

    //변경 후 상태
    @Column(nullable = true)
    private PaymentStatus afterPaymentStatus;

    //상세 메시지
    @Column(nullable = true)
    private String message;

    //이벤트 발생 시각
    @Column(nullable = false)
    private LocalDateTime eventedAt;
}
