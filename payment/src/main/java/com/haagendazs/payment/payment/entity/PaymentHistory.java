package com.haagendazs.payment.payment.entity;

import com.haagendazs.payment.payment.enums.PaymentStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;

import lombok.*;

//결제상태변화로그
@Entity
@Table(name = "payment_history")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentHistory {

    //로그id
    @Id
    @GeneratedValue
    private Long id;

    //결제id
    @Column(nullable = false)
    private Long paymentId;

    //이전 상태
    @Column(nullable = true)
    private PaymentStatus beforeStatus;

    //변경 후 상태
    @Column(nullable = true)
    private PaymentStatus afterStatus;

    @Column(nullable = true)
    private String failCode;

    //상세 메시지
    @Column(nullable = true)
    private String message;

    //이벤트 발생 시각
    @Column(nullable = false)
    private LocalDateTime eventedAt;
}
