-- 결제 실패 알림 이벤트 타입 추가
INSERT INTO notification.event_types
    (code, stream_key, is_scheduled, is_single_target, member_id_field, scheduled_at_field, scheduled_offset_minutes)
VALUES
    ('PAYMENT_FAILED', 'notif:stream:payment.failed', false, true, 'memberId', null, 0)
ON CONFLICT (code) DO NOTHING;
