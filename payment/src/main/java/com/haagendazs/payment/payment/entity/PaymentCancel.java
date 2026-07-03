package com.haagendazs.payment.payment.entity;

import com.haagendazs.payment.payment.enums.CancelReason;
import com.haagendazs.payment.payment.enums.CancelStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

//결제취소이력
@Entity
@Table(name = "payment_cancel")
@Getter
@Builder
@AllArgsConstructor
public class PaymentCancel {

    //취소이력id
    @Id
    @GeneratedValue
    private Long id;

    //결제id
    @Column(nullable = false)
    private Long paymentId;

    //취소금액
    @Column(nullable = false)
    private Long cancelAmount;

    //취소사유
    @Column(nullable = false)
    private CancelReason cancelReason;

    //취소처리상태
    @Column(nullable = true)
    private CancelStatus cancelStatus;

    //취소완료시각
    @Column(nullable = true)
    private LocalDateTime canceledAt;

    //이벤트 발생 시각
    @Column(nullable = false)
    private LocalDateTime eventedAt;
}
