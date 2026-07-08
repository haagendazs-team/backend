package com.haagendazs.payment.gift.entity;

import com.haagendazs.payment.gift.enums.GiftStatus;
import com.haagendazs.payment.global.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

import lombok.*;

@Entity
@Table(name = "gifts")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Gifts extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 선물 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GiftStatus giftStatus;

    // 선물 발송 시각
    @Column
    private LocalDateTime sentAt;

    // 선물 수령 시각
    @Column
    private LocalDateTime receivedAt;

    // 선물 취소 시각
    @Column
    private LocalDateTime canceledAt;

    // 주문 ID
    @Column(nullable = false)
    private Long orderId;

    // 선물하는 멤버 ID
    @Column(nullable = false)
    private Long sendMemberId;

    // 선물받는 멤버 ID
    @Column(nullable = false)
    private Long receiveMemberId;

}
