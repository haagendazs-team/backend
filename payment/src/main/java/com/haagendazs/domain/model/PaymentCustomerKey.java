package com.haagendazs.domain.model;

import com.haagendazs.domain.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "payment_customer_keys",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payment_customer_key_member_id",
                        columnNames = "member_id"
                ),
                @UniqueConstraint(
                        name = "uk_payment_customer_key_hash",
                        columnNames = "customer_key_hash"
                )
        }
)
public class PaymentCustomerKey extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 다른 도메인의 식별자. FK 걸지 않음.
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    // customerKey 원문은 복호화가 필요하므로 암호화 저장
    @Column(name = "customer_key_encrypted", nullable = false, length = 500)
    private String customerKeyEncrypted;

    // 중복 검사용. HMAC-SHA256 등으로 생성
    @Column(name = "customer_key_hash", nullable = false, length = 100)
    private String customerKeyHash;

    private PaymentCustomerKey(
            Long memberId,
            String customerKeyEncrypted,
            String customerKeyHash
    ) {
        this.memberId = memberId;
        this.customerKeyEncrypted = customerKeyEncrypted;
        this.customerKeyHash = customerKeyHash;
    }

    public static PaymentCustomerKey create(
            Long memberId,
            String customerKeyEncrypted,
            String customerKeyHash
    ) {
        return new PaymentCustomerKey(
                memberId,
                customerKeyEncrypted,
                customerKeyHash
        );
    }
}
