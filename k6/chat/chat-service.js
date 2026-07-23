import http from 'k6/http';

const BASE_URL = __ENV.TARGET_HOST || 'http://localhost:8085';

// 인증: JWT 미완성으로 임시 X-Member-Id 헤더 사용
// TODO: 팀 JWT 유틸 완성되면 Authorization: Bearer 방식으로 교체
function getHeaders(memberId, tagName) {
    const headers = { 'Content-Type': 'application/json' };
    if (memberId) headers['X-Member-Id'] = String(memberId);
    return { headers, tags: { name: tagName } };
}

/* =========================================================================
 * 채팅 REST API (HTTP) — haagendazs
 * ========================================================================= */

// 메시지 목록 조회 (커서 페이지네이션)
// cursor 없으면 최신부터, 있으면 그 messageId 이전 메시지
export function getChatHistory(memberId, channelId, cursor) {
    let url = `${BASE_URL}/channels/${channelId}/messages?size=30`;
    if (cursor) url += `&cursor=${cursor}`;
    return http.get(url, getHeaders(memberId, 'chat_history_get'));
}

// 읽음 처리 (PATCH + lastReadMessageId body)
export function readMessage(memberId, channelId, lastReadMessageId) {
    const url = `${BASE_URL}/channels/${channelId}/read`;
    const body = JSON.stringify({ lastReadMessageId: lastReadMessageId });
    return http.patch(url, body, getHeaders(memberId, 'chat_message_read_patch'));
}

// 메시지 삭제 (soft delete)
export function deleteMessage(memberId, channelId, messageId) {
    const url = `${BASE_URL}/channels/${channelId}/messages/${messageId}`;
    return http.del(url, null, getHeaders(memberId, 'chat_message_delete'));
}

// 메시지 수정
export function updateMessage(memberId, channelId, messageId, content) {
    const url = `${BASE_URL}/channels/${channelId}/messages/${messageId}`;
    const body = JSON.stringify({ content: content });
    return http.patch(url, body, getHeaders(memberId, 'chat_message_update'));
}
