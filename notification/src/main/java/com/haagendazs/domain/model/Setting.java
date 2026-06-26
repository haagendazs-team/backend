package com.haagendazs.domain.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Getter
@Table("settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Setting {

    @Id
    private Long id;

    @Column("member_id")
    private Long memberId;

    @Column("ticket_open_alert")
    private boolean ticketOpenAlert;

    @Column("game_start_alert")
    private boolean gameStartAlert;

    @Column("payment_alert")
    private boolean paymentAlert;

    @Column("chat_mention_alert")
    private boolean chatMentionAlert;

    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;

    public static Setting createDefault(Long memberId) {
        Setting setting = new Setting();
        setting.memberId = memberId;
        setting.ticketOpenAlert = true;
        setting.gameStartAlert = true;
        setting.paymentAlert = true;
        setting.chatMentionAlert = true;
        return setting;
    }

    public void update(boolean ticketOpenAlert, boolean gameStartAlert, boolean paymentAlert, boolean chatMentionAlert) {
        this.ticketOpenAlert = ticketOpenAlert;
        this.gameStartAlert = gameStartAlert;
        this.paymentAlert = paymentAlert;
        this.chatMentionAlert = chatMentionAlert;
    }

    public boolean isEnabledFor(String eventTypeCode) {
        return switch (eventTypeCode) {
            case "TICKET_OPEN" -> ticketOpenAlert;
            case "GAME_START" -> gameStartAlert;
            case "PAYMENT_COMPLETED" -> paymentAlert;
            case "CHAT_MENTION", "CHAT_INVITED" -> chatMentionAlert;
            default -> true;
        };
    }
}
