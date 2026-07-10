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

    @Column("is_scheduled")
    private boolean scheduled;

    @Column("is_single_target")
    private boolean singleTarget;

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

    public static EventTypeDefinition of(String code, boolean isScheduled, boolean isSingleTarget) {
        EventTypeDefinition def = new EventTypeDefinition();
        def.code = code;
        def.scheduled = isScheduled;
        def.singleTarget = isSingleTarget;
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
