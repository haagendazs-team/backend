import ws from 'k6/ws';
import { check } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

export const wsDisconnectRate = new Rate('ws_disconnect_rate');
export const messageDeliveryLatency = new Trend('message_delivery_latency');
export const stompConnectMs = new Trend('stomp_connect_ms');
export const stompRoundtripMs = new Trend('stomp_message_roundtrip_ms');
export const stompMsgSent = new Counter('stomp_messages_sent');
export const stompMsgConfirmed = new Counter('stomp_messages_confirmed');
export const stompSubSuccess = new Counter('stomp_subscribe_success');
export const stompSubFailed = new Counter('stomp_subscribe_failed');

const BASE_URL = __ENV.TARGET_HOST || 'http://localhost:8085';
const WS_URL = BASE_URL.replace(/^http/, 'ws') + '/ws';

// 인증: JWT 미완성으로 임시 memberId 헤더 사용
// TODO: 팀 JWT 유틸 완성되면 Authorization: Bearer 방식으로 교체
function buildParams(memberId, tagName) {
    const headers = {};
    if (memberId) headers['memberId'] = String(memberId);
    return { headers, tags: { name: tagName } };
}

function createFrame(command, headers = {}, body = '') {
    let frame = `${command}\n`;
    for (const [key, value] of Object.entries(headers)) {
        frame += `${key}:${value}\n`;
    }
    frame += `\n${body}\x00`;
    return frame;
}

export function connectChatServer(memberId, options = {}, onMessageCallback, onConnectedCallback) {
    const tagName = (options.tags && options.tags.name) || 'chat_connect_websocket';
    const params = buildParams(memberId, tagName);

    // CONNECT 프레임 전송 시점부터 측정
    let connectFrameSentTime = null;

    return ws.connect(WS_URL, params, function (socket) {
        let receivedMsgCount = 0;

        socket.on('open', function () {
            const connectFrame = createFrame('CONNECT', {
                'accept-version': '1.1,1.2',
                Authorization: 'Bearer temp-token',
                'heart-beat': '10000,10000',
                host: 'localhost',
                memberId: String(memberId),   // 임시 인증 (JWT 대체)
            });
            connectFrameSentTime = Date.now();
            socket.send(connectFrame);
        });

        socket.on('message', function (data) {
            if (data.startsWith('CONNECTED')) {
                if (connectFrameSentTime) {
                    stompConnectMs.add(Date.now() - connectFrameSentTime);
                }
                check(data, { 'stomp connected': (r) => r.startsWith('CONNECTED') });
                if (onConnectedCallback) onConnectedCallback(socket);
                return;
            }

            if (data.startsWith('MESSAGE')) {
                stompMsgConfirmed.add(1);
                receivedMsgCount++;

                const parts = data.split('\n\n');
                if (parts.length >= 2) {
                    const bodyString = parts[1].replace(/\0/g, '');
                    try {
                        const parsedBody = JSON.parse(bodyString);
                        if (parsedBody.sendAt) {
                            // 서버가 돌려준 sendAt(로컬시간 문자열)을 로컬 기준으로 파싱
                            const sentTime = new Date(parsedBody.sendAt).getTime();
                            const latency = Date.now() - sentTime;
                            messageDeliveryLatency.add(latency);
                            stompRoundtripMs.add(latency);
                        }
                    } catch (e) { /* ignore */ }
                }

                if (receivedMsgCount >= 3) {
                    disconnectChatServer(socket);
                }
            }

            if (onMessageCallback) onMessageCallback(data);
        });

        let hasError = false;
        socket.on('close', function () {
            if (!hasError) wsDisconnectRate.add(0);
        });
        socket.on('error', function () {
            hasError = true;
            wsDisconnectRate.add(1);
        });
    });
}

export function subscribeRoom(socket, subscriptionId, channelId) {
    try {
        const subscribeFrame = createFrame('SUBSCRIBE', {
            id: subscriptionId,
            destination: `/topic/${channelId}`,
        });
        socket.send(subscribeFrame);
        stompSubSuccess.add(1);
    } catch (e) {
        stompSubFailed.add(1);
    }
}

export function sendChatMessage(socket, channelId, payload) {
    const now = new Date();
    // 로컬 시간을 ISO 형식으로 (시간대 정보 없이) — 서버 LocalDateTime과 맞춤
    const localIso = new Date(now.getTime() - now.getTimezoneOffset() * 60000)
            .toISOString()
            .slice(0, -1);   // 끝의 'Z' 제거 (UTC 표시 제거)
    payload.sendAt = localIso;

    const sendFrame = createFrame('SEND', {
        destination: `/publish/${channelId}`,
        'content-type': 'application/json',
    }, JSON.stringify(payload));
    socket.send(sendFrame);
    stompMsgSent.add(1);
}

export function disconnectChatServer(socket) {
    const disconnectFrame = createFrame('DISCONNECT');
    socket.send(disconnectFrame);
    socket.close();
}
