# SSE 24,000 CCU 달성 트러블슈팅

## 핵심 성과

MacBook Air M2(로컬 Docker) 환경에서 SSE 동시 연결을 **200 → 24,000 CCU(120배)** 까지 확장하였으며,<br/>
로컬 검증 환경에서 운영 목표의 4배 규모까지 안정성을 검증환경을 확보하였습니다.



| 환경 | 모델 | CPU | RAM | 목표 CCU | 달성 CCU |
|---|---|---|---|---|---|
| AWS (운영) | t4g.medium | 2 vCPU | 4 GB | 6,000 | — |
| Local (개발) | MacBook Air M2 | 8 CPU | 16 GB | 24,000 | **24,000** |

> **목표 설정 근거**: CPU 및 메모리 스펙 기준으로 약 4배 규모의 하드웨어 차이를 고려하여, <br/>
> 로컬에서 24K CCU를 달성하면 운영 환경의 목표치(6K CCU)를 검증할 수 있다고 판단하였습니다.<br/>
> 단, 로컬은 macOS VM 위 Docker이므로 Linux 커널 네트워크 스택과 동일하지 않음 — 수치는 추정치.

---

## 내가 수행한 역할

- K6 부하 테스트 스크립트 설계 및 실행 (VU 24,000까지 단계적 ramping)
- Heap Dump, GC Log, Docker Stats, Netstat 등을 통해 병목 구간을 직접 분석
- OS 커널 파라미터, Docker 컨테이너 설정, JVM GC, Reactor Netty 설정을 직접 식별하고 수정
- SSE 세션 jitter, 배치 버퍼 아키텍처, Redis 설정 캐시 등 애플리케이션 레벨 최적화 설계 및 구현
- 개발 환경에서 Host 전체가 응답 불능 상태가 되는 문제를 방지하기 위해, Docker 메모리 사용량을 모니터링하여 임계치(92%) 초과 시 테스트를 자동 종료하는 `heap-guard.sh`를 직접 구현

---

## 병목 분석

SSE는 장시간 유지되는 TCP 연결이므로, 동시 연결 수가 증가할수록 파일 디스크립터(fd)와 ephemeral port를 지속적으로 점유합니다. 이 특성이 아래 모든 병목의 공통 원인이 되었습니다.

### 병목 1 — macOS fd 한계

```
macOS 기본 kern.maxfilesperproc = 10,240
ulimit -n (shell 기본) = 256

→ shell 프로세스(k6, 앱 기동)에서 256개 연결 이후 accept() 실패
```

SSE 연결 = 파일 디스크립터 소비. OS 레벨 hard limit을 올리지 않으면 k6 실행 중 즉시 포화됩니다.

### 병목 2 — Ephemeral Port 범위

```
macOS 기본 포트 범위: 49,152~65,535 = 16,383 포트
→ 단일 호스트에서 서버를 향한 동시 연결 이론상 최대 16,383
```

k6는 로컬호스트에서 Docker(127.0.0.1)로 연결 → 클라이언트 측 포트가 ephemeral 범위로 제한됩니다.

<img width="1631" height="862" alt="16.4k" src="https://github.com/user-attachments/assets/d1ec0f0c-1d3b-4e5a-8ae9-dc2d89dc7d10" />


### 병목 3 — TCP accept 큐 포화

ramp-up 구간에서 SYN 패킷이 `kern.ipc.somaxconn` 한계를 초과하면 OS가 패킷을 드롭합니다.
128(macOS 기본) → 연결 폭주 시 즉시 포화.

### 병목 4 — JVM STW (G1GC)

```
~10K 연결 시:
- SseSession × 10K 동시 존재
- Sinks.many().multicast() 내부 버퍼 × 10K
- ping 브로드캐스트 시 연결 수만큼 ServerSentEvent 객체가 순간 생성
→ G1GC Major GC STW 2~3초 발생 → 이벤트 루프 stall → SSE 청크 전송 지연 → k6 타임아웃
```

<img width="693" height="432" alt="7k,heap91%" src="https://github.com/user-attachments/assets/be3de2a6-4b8e-4f29-b001-6cfc24cb5ab5" />

### 병목 5 — DB I/O 커넥션 풀 경합

```
SSE 재연결 시 replay 조회 + 팬아웃 알림 저장이 동시에 DB 풀(max=20) 경합
→ 풀 고갈 → Reactive Pipeline이 Connection Pool 반환을 기다리며 지연
```

<img width="826" height="268" alt="io" src="https://github.com/user-attachments/assets/3714e4db-890b-4fe3-9e96-86c2182d9199" />


### 병목 6 — Docker 컨테이너 ulimit

```
Docker 기본 nofile = 1,024
→ 컨테이너 내부에서 별도로 1K 연결 이후 fd 고갈 (호스트 OS 설정과 독립)
```

### 병목 7 — 호스트 자원 고갈 (10.5K 이상 멈춤)

```
증상: 10.5K 이상에서 Mac 전체 응답 없음
원인 복합:
  - k6 Go 런타임 메모리 무제한 → OOM → OS 스왑 → 전체 응답 없음
  - JVM Full GC STW → 프로세스 응답 없음
  - fd 고갈 → accept() 실패 → 연결 재시도 폭풍
```

<img width="1628" height="866" alt="스크린샷 2026-07-05 오후 2 44 04" src="https://github.com/user-attachments/assets/7f7db718-45d5-4f73-9873-3efc950e02c5" />

---

## 최적화 로드맵

```
200 → 6,000 → 10,500 → 16,400 → 24,000
```
<img width="1536" height="1024" alt="ccu-os-docker-app-loadmap" src="https://github.com/user-attachments/assets/3d28614e-6d32-4141-b4cc-207985542c80" />


### Phase 1 — OS 레벨 튜닝 (200 → 6,000)

```bash
# fd 한계 해제
ulimit -n 131072
sudo sysctl -w kern.maxfiles=131072
sudo sysctl -w kern.maxfilesperproc=131072

# TCP accept 큐
sudo sysctl -w kern.ipc.somaxconn=65535
```

### Phase 2 — JVM GC 최적화 (6,000 → 10,500)

`docker-compose.local.yml` JAVA_TOOL_OPTIONS:

```bash
JAVA_TOOL_OPTIONS=-XX:MaxMetaspaceSize=256m \
  -Xmx1g \
  -XX:SoftMaxHeapSize=800m \
  -XX:+UseZGC \
  -Dio.netty.maxDirectMemory=1073741824
```

| 옵션 | 역할 |
|---|---|
| `UseZGC` | STW < 1ms 목표 (G1GC Major GC 2~3초 대비 95% 감소) |
| `SoftMaxHeapSize=800m` | GC 빈도 조절 — 800m 초과 시 적극 수거, 1g hard limit |
| `maxDirectMemory=1073741824` | Netty off-heap 버퍼 1g 명시 (컨테이너 4g 중 할당) |

> Java 21+ 기준 `-XX:+ZGenerational`은 ZGC 기본 동작에 포함되어 별도 명시 불필요.

### Phase 3 — 애플리케이션 구조 개선 (10,500 → 16,400)

`application.yml`:

```yaml
server:
    netty:
        connection-timeout: 30s
        idle-timeout: 90s
        worker-count: 16
```


#### DB 배치 버퍼

**SSE 연결 중 멤버**: `NotificationBatchBuffer`가 200건 / 500ms 주기로 bulk insert → DB 커넥션 점유 최소화.

**SSE 미연결 멤버**: 즉시 개별 persist → 실시간성 유지.

### Phase 4 — Container Runtime 튜닝 (16,400 → 24,000)

#### macOS ephemeral 포트 범위 확장

k6가 `localhost:8081`로 TCP 연결을 맺을 때마다 커널이 클라이언트 포트를 하나씩 소비합니다.

**[before]** macOS 기본 설정
```shell
net.inet.ip.portrange.first: 49152
net.inet.ip.portrange.last:  65535
# 가용 포트 수: 65535 - 49152 = 16,383개
```

**[after]** 포트 범위 확장
```shell
sudo sysctl -w net.inet.ip.portrange.first=32768
# 가용 포트 수: 65535 - 32768 = 32,767개
```

#### Docker 컨테이너 ulimit / sysctl

`docker-compose.local.yml` — notification 서비스:

```yaml
notification:
    mem_limit: 4g
    ulimits:
        nofile:
            soft: 65535
            hard: 65535
    sysctls:
        net.core.somaxconn: 65535
        net.ipv4.tcp_max_syn_backlog: 65535
        net.ipv4.ip_local_port_range: "32768 65535"
        net.ipv4.tcp_tw_reuse: 1
        net.ipv4.tcp_fin_timeout: 15
```

> Docker 컨테이너는 Linux 커널 네임스페이스 단위로 ulimit가 분리됩니다. 호스트 OS 설정과 독립이므로 반드시 별도 설정.

#### K6 Go 런타임 메모리 제한

```bash
export GOGC=50          # GC 트리거 임계치 50% → 메모리 점유 감소 (기본 100%)
export GOMEMLIMIT=4GiB  # OOM killer 방지 hard limit
```

1. `GOGC=50`: Go 힙이 현재 사용량의 50%만 증가해도 GC 트리거. CPU 소폭 증가 트레이드오프로 메모리 급증을 차단합니다.
2. JVM 90% 메모리 도달시 K6자동 종료

<img width="1627" height="857" alt="sse connect 24k" src="https://github.com/user-attachments/assets/2a24d874-ef7a-43fc-bb14-939279ecc50b" />

---

## 결과

### 단계별 CCU 개선

| Phase | 병목 | 주요 개선 | 달성 CCU |
|---|---|---|---|
| 기준 | — | — | 200 |
| Phase 1 | OS fd / TCP 큐 | 커널 파라미터 해제 | 6,000 |
| Phase 2 | JVM Full GC STW | ZGC 전환 | 10,500 |
| Phase 3 | thundering herd / I/O | 세션 jitter + 배치 버퍼 | 16,400 |
| Phase 4 | 포트 고갈 / 컨테이너 fd | ephemeral 포트 확장 + Docker ulimit | **24,000** |

### 설정 전/후 비교

| 항목 | 기본값 | 최적화 후 |
|---|---|---|
| `kern.maxfilesperproc` | 10,240 (macOS 기본) | 131,072 |
| `kern.ipc.somaxconn` | 128 | 65,535 |
| `net.inet.ip.portrange.first` | 49,152 (16,383포트) | 26,000 (39,535포트) |
| Docker `nofile` | 1,024 | 65,535 |
| JVM GC | G1GC (STW 2~3초) | ZGC (STW < 1ms) |
| JVM heap | 무제한 | `-Xmx1g -XX:SoftMaxHeapSize=800m` |
| Netty worker | CPU 코어 수 (기본) | 16 (M2 로컬 전용) |
| K6 `GOMEMLIMIT` | 무제한 | 4GiB |
| 알림 저장 | 건별 insert | 200건 bulk insert |

### K6 임계값 기준 통과

| 메트릭 | 목표 | 실측값 | 결과 |
|---|---|---|---|
| `sse_connect_attempts` | 24,000 | 24,000 (31.98/s) | 통과 |
| `sse_pool_exhausted` | < 10 | 0 | 통과 |
| `sse_server_disconnect` | < 10 | 0 | 통과 |

```
vus_max: 24,000
data_received: 7.1 MB (9.5 kB/s)
data_sent:     3.4 MB (4.5 kB/s)
```

---

## 결론

이번 트러블슈팅을 통해 단순히 JVM 옵션을 변경하는 수준이 아니라, 아래 계층 전체의 병목을 순차적으로 분석하고 개선하였습니다.

- **OS** — fd 한계, TCP accept 큐, ephemeral 포트 범위
- **Docker Runtime** — 컨테이너 ulimit, 내부 sysctl
- **JVM** — GC 알고리즘 전환 (G1GC → ZGC)
- **Reactor Netty** — I/O worker 스레드 수, idle-timeout
- **애플리케이션** — SSE 세션 만료 분산, DB I/O 배치 구조
- **테스트 도구** — K6 Go 런타임 메모리 제한

최종적으로 200 CCU에서 시작하여 24,000 CCU까지 **약 120배의 동시 연결 확장**을 달성하였습니다.

이를 통해 애플리케이션 레벨뿐 아니라 운영체제, 컨테이너, JVM, 네트워크 스택이 SSE 성능에 동시에 영향을 미친다는 점을 직접 확인하고, 계층별 병목 탐색이 왜 필요한지 체득하였습니다.
