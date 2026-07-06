package com.haagendazs.payment.Subscription.entity;

import com.haagendazs.payment.Subscription.enums.PlanStatus;
import com.haagendazs.payment.Subscription.enums.PlanType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "subscription_plans")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //구독플랜이름
    @Column(nullable = false)
    private String name;

    //플랜타입(등급)
    @Column(nullable = false)
    private PlanType type;

    //구독기간
    @Column(nullable = false)
    private int durationDays = 30;

    //플랜이 제공하는 혜택

    //구독상품상태
    @Column(nullable = false)
    private PlanStatus status;
}
