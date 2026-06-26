package com.haagendazs.domain.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Getter
@Table("event_types")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventTypeDefinition implements Persistable<String> {

    @Id
    private String code;

    @Column("stream_key")
    private String streamKey;

    @Column("is_scheduled")
    private boolean scheduled;

    @Column("is_single_target")
    private boolean singleTarget;

    @Column("member_id_field")
    private String memberIdField;

    @Column("scheduled_at_field")
    private String scheduledAtField;

    @Column("scheduled_offset_minutes")
    private int scheduledOffsetMinutes;

    @Column("is_enabled")
    private boolean enabled;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Transient
    private boolean newEntity;

    public static EventTypeDefinition of(String code, String streamKey,
                                          boolean isScheduled, boolean isSingleTarget,
                                          String memberIdField, String scheduledAtField,
                                          int scheduledOffsetMinutes) {
        EventTypeDefinition def = new EventTypeDefinition();
        def.code = code;
        def.streamKey = streamKey;
        def.scheduled = isScheduled;
        def.singleTarget = isSingleTarget;
        def.memberIdField = memberIdField;
        def.scheduledAtField = scheduledAtField;
        def.scheduledOffsetMinutes = scheduledOffsetMinutes;
        def.enabled = true;
        def.newEntity = true;
        return def;
    }

    @Override
    public String getId() {
        return code;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }
}
