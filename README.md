# Haagendazs

---

> **Haagendazs**는 클라우드 협업 플랫폼입니다.  <br />
> 개발 기간: 2026.06.24 ~ 2026.07.24(1달)

## _intro._

---

프로젝트 주요 기능은 다음과 같습니다.

| 기능    | 설명                                 |
|-------|------------------------------------|
| 🎫 회원 | 회원 기능                              |
| 💳 결제 | Toss Payments 기반 결제 시스템, 결제 무결성 확보 |
| 💬 채팅 | -                                  |
| 🔔 알림 | 주요 이벤트 실시간 알림 (SSE, Email)         |

<br />

## _Member._

|                                                               **강정훈**                                                               |                                                                 **고정국**                                                                 |                                                                **김준영**                                                                |                                                                   **강상욱**                                                                   |                                                                 
|:-----------------------------------------------------------------------------------------------------------------------------------:|:---------------------------------------------------------------------------------------------------------------------------------------:|:-------------------------------------------------------------------------------------------------------------------------------------:|:-------------------------------------------------------------------------------------------------------------------------------------------:|
| [<img src="https://avatars.githubusercontent.com/u/105915960?v=4" height=130 width=130><br/>  @JHkoder](https://github.com/JHkoder) | [<img src="https://avatars.githubusercontent.com/u/126741397?v=4" height=130 width=130> <br/> @jeonggugo](https://github.com/jeonggugo) | [<img src="https://avatars.githubusercontent.com/u/133593957?v=4" height=130 width=130> <br/> @zzimzzim](https://github.com/zzimzzim) | [<img src="https://avatars.githubusercontent.com/u/148408469?v=4" height=120 width=130> <br/> @sangwookkhu](https://github.com/sangwookkhu) |
|                                                                 알림                                                                  |                                                                   채팅                                                                    |                                                                  결제                                                                   |                                                                     회원                                                                      |                           

<br />

## _Stack_.

| Category          | Technology                                |
|-------------------|-------------------------------------------|
| Language          | Java 25                                   |
| Framework         | Spring Boot 4.0.3 · Spring Cloud 2025.1.x |
| Database          | PostgreSQL 18 (R2DBC + JDBC/Flyway)       |
| Cache / Pub-Sub   | Redis 8                                   |
| Message Broker    | Apache Kafka 4.0 (KRaft)                  |
| Search            | Elasticsearch 8.13                        |
| Service Discovery | Eureka (Spring Cloud Netflix)             |
| API Gateway       | Spring Cloud Gateway                      |
| Build             | Gradle (Multi-module)                     |

---

## _Modules._

| Module          | Port | Description                           |
|-----------------|------|---------------------------------------|
| `config-server` | 8888 | 중앙 설정 서버                              |
| `discovery`     | 8761 | Eureka 서비스 레지스트리                      |
| `gateway`       | 8080 | API Gateway (JWT 검증, 라우팅, Rate Limit) |
| `member`        | 8084 | 회원 서비스                                |
| `payment`       | 8083 | 결제 서비스                                |
| `notification`  | 8081 | 알림 서비스 (SSE · Redis Streams · Kafka)  |
| `chat`          | 8085 | 채팅 서비스                                |
| `common`        | —    | 공통 예외 · 응답 모듈                         |

## _Architecture._

```
Client
  │
  ▼
Gateway :8080  (JWT 검증 · Rate Limit · 라우팅)
  │
  ├── member       :8084  ─── PostgreSQL ── Kafka
  ├── payment      :8083  ─── PostgreSQL ── Kafka
  ├── notification :8081  ─── PostgreSQL ── Redis Streams/Pub-Sub ── Kafka
  └── chat         :8085  ─── PostgreSQL ── Redis ── Kafka

Boot order: config-server → discovery → [services]
```

### _AWS Scale-Out._

EC2 + Auto Scaling Group 기반. 기본 인스턴스 1개, 부하에 따라 선택적 확장.  
자세한 내용 → [docs/aws-scale-out-architecture.md](docs/aws-scale-out-architecture.md)

---

## License

This project is for educational purposes.  
© 2026 Haagendazs Team. All rights reserved.
