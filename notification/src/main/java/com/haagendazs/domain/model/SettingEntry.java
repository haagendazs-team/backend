package com.haagendazs.domain.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table("setting_entries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettingEntry {

    @Id
    private Long id;

    @Column("member_id")
    private Long memberId;

    @Column("event_type_code")
    private String eventTypeCode;

    @Column("is_enabled")
    private boolean enabled;

    public static SettingEntry create(Long memberId, String eventTypeCode) {
        SettingEntry entry = new SettingEntry();
        entry.memberId = memberId;
        entry.eventTypeCode = eventTypeCode;
        entry.enabled = true;
        return entry;
    }

    public void updateEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
