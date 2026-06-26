package com.haagendazs.domain.model;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.common.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Table("channels")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Channel {

    @Id
    private Long id;

    @Column("member_id")
    private Long memberId;

    @Column("channel_type")
    private ChannelType channelType;

    @Column("channel_target")
    private String channelTarget;

    @Column("is_enabled")
    private boolean enabled;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;

    public static Channel create(Long memberId, ChannelType channelType, String channelTarget) {
        Channel channel = new Channel();
        channel.memberId = memberId;
        channel.channelType = channelType;
        channel.channelTarget = channelTarget;
        channel.enabled = true;
        return channel;
    }

    public void validateOwner(Long memberId) {
        if (!Objects.equals(this.memberId, memberId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_CHANNEL_NOT_FOUND);
        }
    }

    public void toggle() {
        this.enabled = !this.enabled;
    }
}
