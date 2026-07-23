package com.haagendazs.domain.model;

import com.haagendazs.domain.model.BaseEntity;
import com.haagendazs.domain.model.PaymentRetryJobStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(
        name = "payment_retry_jobs",
        indexes = {
                @Index(
                        name = "idx_payment_retry_jobs_due",
                        columnList = "status,next_retry_at"
                )
        }
)
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentRetryJob extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private String orderNo;

    @Column(nullable = false)
    private Long billingId;

    @Column(nullable = false)
    private Integer retryCount;

    @Column(nullable = false)
    private Integer maxRetryCount;

    @Column(nullable = false, name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentRetryJobStatus status;

    @Column(nullable = true)
    private String lastErrorCode;

    @Column(nullable = true)
    private String lastErrorMessage;

    @Column(nullable = true)
    private LocalDateTime lastTriedAt;

    public void markRunning(LocalDateTime now) {
        this.status = PaymentRetryJobStatus.RUNNING;
        this.lastTriedAt = now;
    }

    public void reschedule(
            LocalDateTime nextRetryAt,
            String lastErrorCode,
            String lastErrorMessage
    ) {
        this.retryCount += 1;
        this.nextRetryAt = nextRetryAt;
        this.status = PaymentRetryJobStatus.SCHEDULED;
        this.lastErrorCode = lastErrorCode;
        this.lastErrorMessage = truncate(lastErrorMessage);
    }

    public void succeed() {
        this.status = PaymentRetryJobStatus.SUCCEEDED;
    }

    public void fail(
            String lastErrorCode,
            String lastErrorMessage
    ) {
        this.retryCount += 1;
        this.status = PaymentRetryJobStatus.FAILED;
        this.lastErrorCode = lastErrorCode;
        this.lastErrorMessage = truncate(lastErrorMessage);
    }

    public boolean canRetry() {
        return retryCount < maxRetryCount;
    }

    private String truncate(String message) {
        if (message == null || message.length() <= 500) {
            return message;
        }

        return message.substring(0, 500);
    }
}
