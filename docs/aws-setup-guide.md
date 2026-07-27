# AWS 설정 가이드

---

## 전체 구조 한눈에 보기

```
인터넷
  │
  ▼
Route53 (도메인: jhkoder.shop)
  │
  ▼
ALB — HTTPS 443 포트로 받아서 안쪽으로 전달
  │
  ▼
┌─────────────────────────────────────────────────────┐
│  VPC (10.0.0.0/16)                                  │
│                                                     │
│  Public Subnet (10.0.1.0/24)                        │
│  ├── ALB                                            │
│  └── NAT Gateway  ← ECS가 ECR/CloudWatch 접근할때  │
│                                                     │
│  Private Subnet (10.0.2.0/24)                       │
│  ├── ECS Fargate                                    │
│  │   ├── gateway      (8080)                        │
│  │   ├── config-server (8888)                       │
│  │   ├── discovery    (8761)                        │
│  │   ├── member       (8084)  ← cpu 2 메모리 4GB      │
│  │   ├── payment      (8083)  ← cpu 2 메모리 4GB      │
│  │   ├── chat         (8085)  ← cpu 2 메모리 4GB      │
│  │   └── notification (8081)  ← cpu 2 메모리 4GB      │
│  │                                                  │
│  ├── EC2 t3.medium  ← Kafka 브로커                    │
│  │                                                  │
│  ├── PostgreSQL (db.t3.micro)                       │
│  └── ElastiCache Redis (cache.t3.micro)             │
└─────────────────────────────────────────────────────┘
```

---

## STEP 1 — VPC 만들기

> VPC = 우리만의 가상 인터넷망

### 콘솔 순서
1. AWS 콘솔 → **VPC** 서비스 이동
2. **VPC 생성** 클릭
3. 아래처럼 입력

| 항목 | 값 |
|------|-----|
| 이름 | `haagendazs-vpc` |
| IPv4 CIDR | `10.0.0.0/16` |

4. **서브넷 2개 생성** (VPC 생성 후)

| 이름 | 가용 영역 | CIDR |
|------|----------|------|
| `haagendazs-public-subnet` | ap-northeast-2a | `10.0.1.0/24` |
| `haagendazs-private-subnet` | ap-northeast-2a | `10.0.2.0/24` |

5. **인터넷 게이트웨이** 생성 → `haagendazs-igw` → VPC에 연결
6. **라우팅 테이블** 설정
   - Public 라우팅 테이블: `0.0.0.0/0` → 인터넷 게이트웨이
   - Private 라우팅 테이블: `0.0.0.0/0` → NAT Gateway (아래에서 만듦)

7. **NAT Gateway** 생성
   - 서브넷: `haagendazs-public-subnet`
   - Elastic IP 새로 할당

---

## STEP 2 — 보안 그룹 만들기

> 보안 그룹 = 누가 어디로 접근할 수 있는지 규칙

### 2-1. ALB 보안 그룹 (`haagendazs-alb-sg`)

| 방향 | 포트 | 출처 |
|------|------|------|
| 인바운드 | 443 (HTTPS) | 0.0.0.0/0 (전체) |
| 인바운드 | 80 (HTTP) | 0.0.0.0/0 (전체, 443으로 리다이렉트용) |
| 아웃바운드 | 전체 | 전체 |

### 2-2. ECS 보안 그룹 (`haagendazs-ecs-sg`)

| 방향 | 포트 | 출처 |
|------|------|------|
| 인바운드 | 8080 | haagendazs-alb-sg (ALB만 gateway에 접근) |
| 인바운드 | 8081~8888 | haagendazs-ecs-sg (서비스끼리 통신) |
| 아웃바운드 | 전체 | 전체 |

### 2-3. Kafka EC2 보안 그룹 (`haagendazs-kafka-sg`)

| 방향 | 포트 | 출처 |
|------|------|------|
| 인바운드 | 9092 | haagendazs-ecs-sg |
| 인바운드 | 29092 | haagendazs-ecs-sg |
| 아웃바운드 | 전체 | 전체 |

### 2-4. RDS 보안 그룹 (`haagendazs-rds-sg`)

| 방향 | 포트 | 출처 |
|------|------|------|
| 인바운드 | 5432 | haagendazs-ecs-sg |
| 아웃바운드 | 전체 | 전체 |

### 2-5. Redis 보안 그룹 (`haagendazs-redis-sg`)

| 방향 | 포트 | 출처 |
|------|------|------|
| 인바운드 | 6379 | haagendazs-ecs-sg |
| 아웃바운드 | 전체 | 전체 |

---

## STEP 3 — RDS 만들기

> RDS = AWS가 관리해주는 PostgreSQL

1. AWS 콘솔 → **RDS** → **데이터베이스 생성**
2. 아래처럼 설정

| 항목 | 값 |
|------|-----|
| 엔진 | PostgreSQL 18 |
| 템플릿 | 프리 티어 |
| DB 인스턴스 식별자 | `haagendazs-postgres` |
| 인스턴스 클래스 | `db.t3.micro` |
| 스토리지 | 20GB gp3 |
| DB 이름 | `haagendazs` |
| 사용자 이름 | 환경변수로 관리 |
| VPC | `haagendazs-vpc` |
| 서브넷 그룹 | private subnet |
| 보안 그룹 | `haagendazs-rds-sg` |
| 퍼블릭 액세스 | **아니요** |

3. 생성 후 **엔드포인트 주소** 복사해두기 (나중에 ECS 환경변수에 입력)

---

## STEP 4 — ElastiCache Redis 만들기

1. AWS 콘솔 → **ElastiCache** → **Redis OSS 캐시 생성**
2. 아래처럼 설정

| 항목 | 값 |
|------|-----|
| 클러스터 이름 | `haagendazs-redis` |
| 노드 유형 | `cache.t3.micro` |
| 복제본 수 | 0 (포트폴리오용) |
| 서브넷 그룹 | private subnet |
| 보안 그룹 | `haagendazs-redis-sg` |
| 최대 메모리 정책 | `allkeys-lru` |

3. 생성 후 **기본 엔드포인트** 복사해두기

---

## STEP 5 — Kafka EC2 만들기

> SSH 없이 운영하지만, Kafka는 초기 설정 1회만 SSM Session Manager로 접속

### 5-1. EC2 생성

1. AWS 콘솔 → **EC2** → **인스턴스 시작**

| 항목 | 값 |
|------|-----|
| 이름 | `haagendazs-kafka` |
| AMI | Amazon Linux 2023 |
| 인스턴스 유형 | `t3.medium` |
| 키 페어 | **키 페어 없음** (SSM으로 접속) |
| VPC | `haagendazs-vpc` |
| 서브넷 | `haagendazs-private-subnet` |
| 퍼블릭 IP 자동 할당 | **비활성화** |
| 보안 그룹 | `haagendazs-kafka-sg` |
| IAM 인스턴스 프로파일 | `SSMRole` (아래에서 만듦) |

### 5-2. SSM 역할 만들기 (최초 1회)

1. IAM → 역할 → 역할 생성
2. 신뢰할 수 있는 개체: **EC2**
3. 정책 추가: `AmazonSSMManagedInstanceCore`
4. 역할 이름: `SSMRole`
5. EC2에 이 역할 연결

### 5-3. Kafka 설치 (SSM Session Manager로 접속)

EC2 콘솔 → 인스턴스 선택 → **연결** → **Session Manager** 탭 → **연결**

```bash
# Java 설치
sudo yum install -y java-21-amazon-corretto

# Kafka 다운로드
cd /opt
sudo wget https://downloads.apache.org/kafka/4.0.0/kafka_2.13-4.0.0.tgz
sudo tar -xzf kafka_2.13-4.0.0.tgz
sudo mv kafka_2.13-4.0.0 kafka

# KRaft 설정 파일 수정
sudo tee /opt/kafka/config/kraft/server.properties > /dev/null <<'EOF'
node.id=1
process.roles=broker,controller
listeners=INTERNAL://:29092,EXTERNAL://:9092,CONTROLLER://:9093
advertised.listeners=INTERNAL://10.0.2.x:29092,EXTERNAL://10.0.2.x:9092
# ↑ 10.0.2.x 는 이 EC2의 프라이빗 IP로 교체
listener.security.protocol.map=INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT
inter.broker.listener.name=INTERNAL
controller.listener.names=CONTROLLER
controller.quorum.voters=1@localhost:9093
log.dirs=/var/lib/kafka/data
auto.create.topics.enable=false
offsets.topic.replication.factor=1
transaction.state.log.replication.factor=1
transaction.state.log.min.isr=1
KAFKA_HEAP_OPTS="-Xmx512m -Xms256m"
EOF

# 데이터 디렉토리 생성
sudo mkdir -p /var/lib/kafka/data

# 클러스터 ID 생성 및 포맷
CLUSTER_ID=$(/opt/kafka/bin/kafka-storage.sh random-uuid)
sudo /opt/kafka/bin/kafka-storage.sh format -t $CLUSTER_ID -c /opt/kafka/config/kraft/server.properties

# systemd 서비스 등록
sudo tee /etc/systemd/system/kafka.service > /dev/null <<'EOF'
[Unit]
Description=Apache Kafka
After=network.target

[Service]
Type=simple
ExecStart=/opt/kafka/bin/kafka-server-start.sh /opt/kafka/config/kraft/server.properties
ExecStop=/opt/kafka/bin/kafka-server-stop.sh
Restart=on-failure
Environment=KAFKA_HEAP_OPTS=-Xmx512m -Xms256m

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable kafka
sudo systemctl start kafka

# 토픽 생성 (서비스에서 사용하는 토픽들)
/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic member.notif.push.v1 --partitions 3 --replication-factor 1
/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic chat.notif.push.v1 --partitions 3 --replication-factor 1
/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic payment.notif.push.v1 --partitions 3 --replication-factor 1
/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic member.notif.email-cert.v1 --partitions 1 --replication-factor 1
/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic member.created.v1 --partitions 3 --replication-factor 1
```

---

## STEP 6 — ECR 리포지터리 만들기

> ECR = Docker 이미지를 저장하는 창고

1. AWS 콘솔 → **ECR** → **리포지터리 생성**
2. **서비스별로 7개 생성** (모두 프라이빗)

```
haagendazs/gateway
haagendazs/config-server
haagendazs/discovery
haagendazs/member
haagendazs/payment
haagendazs/chat
haagendazs/notification
```

---

## STEP 7 — ECS 클러스터 만들기

### 7-1. 클러스터 생성

1. AWS 콘솔 → **ECS** → **클러스터** → **클러스터 생성**

| 항목 | 값 |
|------|-----|
| 클러스터 이름 | `haagendazs-cluster` |
| 인프라 | **AWS Fargate (서버리스)** |

### 7-2. ECS 태스크 실행 역할 (최초 1회)

IAM → 역할 → `ecsTaskExecutionRole` 확인
없으면 생성:
- 신뢰할 수 있는 개체: `ecs-tasks.amazonaws.com`
- 정책: `AmazonECSTaskExecutionRolePolicy`

### 7-3. 태스크 정의 생성 (서비스별로 반복)

1. ECS → **태스크 정의** → **새 태스크 정의 생성**

**notification 예시** (가장 크고 복잡한 서비스):

| 항목 | 값 |
|------|-----|
| 태스크 정의 이름 | `haagendazs-notification` |
| 컨테이너 이름 | `notification` |
| 이미지 URI | `{계정ID}.dkr.ecr.ap-northeast-2.amazonaws.com/haagendazs/notification:latest` |
| CPU | 4 vCPU |
| 메모리 | 4096 MB |
| 포트 | 8081 |
| ulimits nofile | soft 65535 / hard 65535 |

**나머지 서비스** (gateway 제외):

| 서비스 | CPU | 메모리 | 포트 |
|--------|-----|--------|------|
| config-server | 0.5 vCPU | 512 MB | 8888 |
| discovery | 0.5 vCPU | 512 MB | 8761 |
| gateway | 0.5 vCPU | 512 MB | 8080 |
| member | 0.5 vCPU | 512 MB | 8084 |
| payment | 0.5 vCPU | 512 MB | 8083 |
| chat | 0.5 vCPU | 512 MB | 8085 |

**환경변수** (모든 서비스 공통, Secrets Manager 또는 직접 입력):

```
POSTGRES_HOST     = {RDS 엔드포인트}
POSTGRES_PORT     = 5432
POSTGRES_DB       = haagendazs
POSTGRES_USER     = {값}
POSTGRES_PASSWORD = {값}
REDIS_HOST        = {ElastiCache 엔드포인트}
REDIS_PORT        = 6379
REDIS_PASSWORD    = {값}
KAFKA_BOOTSTRAP_SERVERS = {Kafka EC2 프라이빗 IP}:9092
EUREKA_SERVER_URI = http://discovery.haagendazs.local:8761/eureka
CONFIG_SERVER_URI = http://config-server.haagendazs.local:8888
JWT_SECRET        = {값}
```

### 7-4. ECS Service Discovery (서비스끼리 이름으로 찾기)

1. **Cloud Map** → 네임스페이스 생성
   - 이름: `haagendazs.local`
   - 유형: DNS 프라이빗 (VPC 내부용)

2. 각 ECS 서비스 생성 시 서비스 검색 활성화
   - `config-server.haagendazs.local`
   - `discovery.haagendazs.local`
   - `gateway.haagendazs.local` 등

### 7-5. ECS 서비스 생성 (서비스별로 반복)

1. ECS → 클러스터 → **서비스 생성**

| 항목 | 값 |
|------|-----|
| 컴퓨팅 옵션 | Fargate |
| 서비스 이름 | `haagendazs-{서비스명}` |
| 태스크 정의 | `haagendazs-{서비스명}` |
| 원하는 태스크 수 | 1 (포트폴리오) |
| VPC | haagendazs-vpc |
| 서브넷 | private-subnet |
| 보안 그룹 | haagendazs-ecs-sg |
| 퍼블릭 IP 자동 할당 | **끄기** |

**gateway 서비스만** ALB 타깃 그룹 연결 (아래 STEP 8 이후)

---

## STEP 8 — ALB 만들기

1. EC2 콘솔 → **로드 밸런서** → **로드 밸런서 생성** → Application Load Balancer

| 항목 | 값 |
|------|-----|
| 이름 | `haagendazs-alb` |
| 체계 | 인터넷 경계 |
| VPC | haagendazs-vpc |
| 서브넷 | public-subnet |
| 보안 그룹 | haagendazs-alb-sg |

2. **타깃 그룹 생성**

| 항목 | 값 |
|------|-----|
| 이름 | `haagendazs-gateway-tg` |
| 대상 유형 | IP 주소 |
| 포트 | 8080 |
| 헬스체크 경로 | `/actuator/health` |

3. **리스너 설정**
   - 80 → 443 리다이렉트
   - 443 → haagendazs-gateway-tg 포워드
   - HTTPS 인증서: ACM에서 발급 (Route53 도메인 연결 후)

---

## STEP 9 — GitHub Actions 설정

### 9-1. GitHub 시크릿 등록

저장소 → Settings → Secrets and variables → Actions

| 시크릿 이름 | 값 |
|------------|-----|
| (없음 — OIDC 사용) | |

### 9-2. GitHub 변수 등록

| 변수 이름 | 값 |
|----------|-----|
| `AWS_DEPLOY_ROLE_ARN` | `arn:aws:iam::{계정ID}:role/GithubActionsDeployRole` |

### 9-3. OIDC 역할 만들기 (GitHub → AWS 인증, 비밀키 없이)

1. IAM → 자격 증명 공급자 → 공급자 추가
   - 유형: OpenID Connect
   - URL: `https://token.actions.githubusercontent.com`
   - 대상: `sts.amazonaws.com`

2. IAM → 역할 → `GithubActionsDeployRole` 생성
   - 신뢰할 수 있는 개체: 위에서 만든 OIDC 공급자
   - 조건: `repo:JHKoder/haagendazs-backend:ref:refs/heads/develop`
   - 정책:
     ```
     AmazonECS_FullAccess
     AmazonEC2ContainerRegistryFullAccess
     ```

---

## STEP 10 — CloudWatch 로그 그룹 만들기

> SSH 없는 환경에서 유일한 로그 확인 수단

1. CloudWatch → 로그 그룹 → **로그 그룹 생성** (서비스별로 7개)

```
/ecs/haagendazs-gateway
/ecs/haagendazs-config-server
/ecs/haagendazs-discovery
/ecs/haagendazs-member
/ecs/haagendazs-payment
/ecs/haagendazs-chat
/ecs/haagendazs-notification
```

2. 각 태스크 정의에 로그 드라이버 설정 (태스크 정의 생성 시)

```json
"logConfiguration": {
  "logDriver": "awslogs",
  "options": {
    "awslogs-group": "/ecs/haagendazs-notification",
    "awslogs-region": "ap-northeast-2",
    "awslogs-stream-prefix": "ecs"
  }
}
```

---

## 배포 흐름 요약

```
develop 브랜치에 push
        │
        ▼
GitHub Actions (cicd.yml)
  1. 변경된 서비스 감지
  2. 빌드 (./gradlew bootJar)
  3. 테스트 통과
  4. Docker 이미지 빌드
        │
        ▼
ECR에 이미지 push
  태그: {서비스명}:{git SHA 앞 12자리}
        │
        ▼
ECS 태스크 정의 업데이트
  새 이미지 태그로 교체
        │
        ▼
ECS Rolling Update
  새 태스크 헬스체크 통과 → 구 태스크 종료
        │
        ▼
CloudWatch Logs에서 확인
  aws logs tail /ecs/haagendazs-{서비스명} --follow
```
