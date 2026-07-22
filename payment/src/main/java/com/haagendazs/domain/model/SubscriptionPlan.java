package com.haagendazs.domain.model;

import com.haagendazs.domain.model.PlanStatus;
import com.haagendazs.domain.model.PlanType;
import com.haagendazs.domain.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "subscription_plan")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionPlan extends BaseEntity {

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
    @Column(nullable = false)
    private int searchableDays;

    //구독상품상태
    @Column(nullable = false)
    private PlanStatus status;
}
