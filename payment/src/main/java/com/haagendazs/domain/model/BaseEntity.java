package com.haagendazs.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;

import java.time.LocalDateTime;

import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/*
[공통 엔티티]모든 엔티티의 공통 속성(생성일, 수정일, 삭제여부)관리
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class) //시간 자동 기록 리스너
public abstract class BaseEntity {
    @CreatedDate //생성 시각 자동 저장
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate //수정 시각 자동 저장
    private LocalDateTime updatedAt;

    //등록 시간과 수정 시간을 비교해 수정된 적이 있으면 true 반환
    public boolean isModified() {
        if (createdAt == null || updatedAt == null) {
            return false;
        }
        // 수정 시각이 등록 시각보다 이후라면 '수정됨'으로 판단합니다.
        return updatedAt.isAfter(createdAt);
    }
}
