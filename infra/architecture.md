# 1. 전체 아키텍처
architecture.mmd 참고

리소스 이름은 `swimming-prod-*` 규칙을 따릅니다. Terraform 에서는
`${project_name}-${environment}` 로 만들어지므로 `environment = "prod"` 입니다.

현재는 **EC2 1대**가 Nginx 와 Spring Boot 를 함께 실행합니다. Nginx 는
`127.0.0.1:8080` 으로 프록시하므로 8080 은 어떤 경로로도 외부에 노출되지
않습니다. Nginx / Spring 을 별도 인스턴스로 분리하고 NAT Instance 를 두는
구성은 §12 에 향후 계획으로 정리해 두었습니다.

---

# 2. 네트워크 설계

| 구분           | Terraform / 리소스명       |           CIDR | 역할                        | 외부 접근 |
| ------------ | --------------------- | -------------: | ------------------------- | ----- |
| VPC          | `swimming-prod-vpc`   |  `10.0.0.0/16` | 전체 네트워크                   | X     |
| Public A     | `swimming-prod-public-a` |  `10.0.1.0/24` | Nginx + Spring Boot EC2   | IGW   |
| Public B     | `swimming-prod-public-b` |  `10.0.2.0/24` | 예비 (현재 인스턴스 없음)           | IGW   |
| Private DB A | `swimming-prod-db-a`  | `10.0.21.0/24` | RDS subnet group          | X     |
| Private DB B | `swimming-prod-db-b`  | `10.0.22.0/24` | RDS subnet group          | X     |
| IGW          | `swimming-prod-igw`   |              - | Public subnet 인터넷         | O     |
| Public RT    | `swimming-prod-public-rt` |           - | `0.0.0.0/0 → IGW`         | O     |
| DB RT        | `swimming-prod-db-rt` |              - | 기본 local route만            | X     |
| S3 Endpoint  | `swimming-prod-s3-vpce` |            - | Gateway, Public RT 에 연결   | X     |

서브넷 이름의 AZ 접미사는 리전이 실제로 반환하는 AZ 목록의 앞 두 개에서
가져옵니다. `ap-northeast-2` 에서는 `a`, `b` 가 됩니다.

**예약 대역**: `10.0.11.0/24`, `10.0.12.0/24` 는 향후 Private App 서브넷용으로
비워 둡니다. 지금 만들지 않지만 대역은 선점해 둡니다.

## S3 Gateway Endpoint

ECR 이미지 레이어는 실제로 S3 에서 내려받습니다. Gateway Endpoint 는 **요금이
없고**, 이미지 pull 과 CloudWatch Agent 업로드를 인터넷 게이트웨이 대신 AWS
내부 경로로 보냅니다. 나중에 애플리케이션이 NAT Instance 뒤 private 서브넷으로
이동해도 이 경로가 그대로 유효하다는 점이 더 중요합니다.

---

# 3. Security Group 설계

현재 인스턴스 1대가 Nginx 와 Spring 을 함께 실행하므로 SG 도 하나입니다.
호스트를 분리할 때 이 그룹을 `nginx-sg` / `api-sg` 로 쪼개면 되고, 규칙 자체는
그대로 옮겨집니다.

## `swimming-prod-app-sg`

| 방향       | Protocol | Port | 대상/Source              | 이유                                            |
| -------- | -------- | ---: | ---------------------- | --------------------------------------------- |
| Inbound  | TCP      |  443 | `0.0.0.0/0`            | HTTPS / WSS                                   |
| Inbound  | TCP      |   80 | `0.0.0.0/0`            | HTTP→HTTPS / Let's Encrypt HTTP-01            |
| Outbound | TCP      |  443 | `0.0.0.0/0`            | ECR, SSM, Secrets Manager, CloudWatch, S3 VPCE, ACME, Google OAuth, LLM API |
| Outbound | TCP      |   80 | `0.0.0.0/0`            | 패키지 저장소 미러, ACME OCSP                         |
| Outbound | UDP/TCP  |   53 | `10.0.0.0/16`          | VPC DNS resolver                              |
| Outbound | UDP      |  123 | `169.254.169.123/32`   | Amazon Time Sync Service                      |
| Outbound | TCP      | 5432 | `swimming-prod-db-sg`  | PostgreSQL                                    |

**8080 은 인바운드에 존재하지 않습니다.** Nginx 가 루프백으로 프록시합니다.

**egress 를 좁힐 때 53 과 123 을 반드시 함께 열어야 합니다.** 이름 해석은 VPC
resolver 로, 시간 동기화는 Amazon Time Sync 로 나가는데 둘 다 security group
egress 규칙의 적용을 받습니다. 443/80 만 열면 부팅 스크립트부터 실패합니다.

가능한 부분은 IP 가 아니라 **SG → SG 참조**로 제한합니다.

---

## `swimming-prod-db-sg`

| 방향       | Protocol | Port | 대상/Source                       |
| -------- | -------- | ---: | ------------------------------- |
| Inbound  | TCP      | 5432 | **`swimming-prod-app-sg` only** |
| Outbound | -        |    - | **규칙 없음**                       |

RDS 는 스스로 아웃바운드 연결을 열지 않고 security group 은 stateful 이므로
응답 트래픽에 egress 규칙이 필요하지 않습니다. 그래서 egress 를 비워 둡니다.

---

# 4. 외부 egress 통제 수준

Security Group 은

```text
google.com
accounts.google.com
oauth2.googleapis.com
```

같은 **domain name 기반 정책을 걸 수 없습니다.** IP/CIDR + Port 기반입니다.
Google IP 범위는 변하므로 특정 IP 몇 개로 고정하는 것도 실무적으로 불편합니다.

따라서 지금은 **포트 단위 제한**(443/80/53/123/5432 만 허용)까지가 비용·복잡도
대비 적절한 선입니다. 도메인 단위 allowlist 가 정말 필요해지면 AWS Network
Firewall 이나 HTTP forward proxy 같은 별도 계층이 필요하지만, 현재 규모에서는
과합니다.

---

# 5. 애플리케이션 EC2

| 항목             | 설계                          |
| -------------- | --------------------------- |
| Name           | `swimming-prod-app`         |
| Subnet         | `swimming-prod-public-a`    |
| Public IPv4    | 부팅 시 임시 IP → EIP 로 대체 (§5 하단 참고) |
| EIP            | `swimming-prod-app-eip`     |
| SG             | `swimming-prod-app-sg`      |
| 역할             | HTTPS/WSS reverse proxy + Spring Boot 컨테이너 |
| Nginx → Spring | `127.0.0.1:8080`            |
| TLS            | Let's Encrypt (certbot, AL2023 dnf 패키지)      |
| 인증서 발급         | apply 후 `enable-tls` 1회 수동 실행 |
| 갱신             | `certbot-renew.timer` (1일 2회, 성공 시 nginx reload) |
| IMDS           | IMDSv2 강제 (`http_tokens = required`) |
| SSH            | **없음.** 키 페어 없음, 22 미개방     |
| 접근             | SSM Session Manager         |
| AWS credential | Instance Role               |
| 외부 API         | IGW 직접 (현재 NAT 없음)          |
| Bedrock        | 추후 VPCE                     |

## 부팅 시 퍼블릭 IP가 필요합니다

`associate_public_ip_address = true` 입니다. 서브넷의
`map_public_ip_on_launch` 는 `false` 로 두고 인스턴스 단위로만 켭니다.

IGW 는 퍼블릭 IPv4 가 **이미 있는** 인스턴스의 트래픽만 라우팅하는데, EIP 는
인스턴스가 `running` 이 된 뒤에 연결됩니다. 그 시점이 cloud-init 이 user data
를 시작하는 때와 거의 같아서, 임시 IP 가 없으면 부트스트랩 6번째 줄의
`dnf update -y` 가 실패하고 `set -e` 로 전체가 중단됩니다. S3 게이트웨이
엔드포인트는 S3 만 커버하므로 `cdn.amazonlinux.com`·`pypi.org`·`github.com`
접근을 대신해 주지 못합니다.

EIP 가 연결되면 임시 주소는 해제되고 EIP 로 대체됩니다. 최종 상태는 EIP 하나만
노출되는 것으로 동일합니다.

## AMI 는 고정합니다

`data.aws_ami` 가 항상 최신 AL2023 을 고르고 `ami` 변경은 인스턴스 교체를
강제합니다. 그대로 두면 몇 달 뒤 평범한 apply 가 인스턴스를 날리고 그 안의
Let's Encrypt 인증서·`backend.env`·받아둔 이미지가 함께 사라집니다. 그래서
`lifecycle { ignore_changes = [ami] }` 로 막고, 교체는 명시적으로만 합니다.

```bash
terraform apply -replace=aws_instance.app
```

인증서는 인스턴스에 있으므로 교체 후 `enable-tls` 를 다시 실행해야 합니다.
Let's Encrypt 는 같은 도메인에 주당 5회 발급 제한이 있습니다.

## API 인증서는 부팅 시점에 발급할 수 없습니다

Let's Encrypt HTTP-01 은 호스트명이 **이미** 이 인스턴스의 EIP 로 해석되어야
검증에 성공합니다. EIP 는 apply 가 만들어 내므로 user data 안에서는 아직 DNS
가 없습니다. 그래서 부팅 시에는 80 포트 평문으로만 뜨고, 발급은 A 레코드를
건 다음 SSM 으로 한 번 실행합니다.

```bash
sudo /opt/swimming/bin/enable-tls <API 호스트명> <이메일>
```

이 스크립트가 인증서 발급 → TLS server 블록 + HTTP 301 리다이렉트로 nginx
설정 교체 → reload → 갱신 타이머 활성화까지 합니다.

`/.well-known/acme-challenge/` 는 TLS 적용 후에도 80 포트에 남겨 둡니다.
갱신이 같은 경로를 쓰므로 **80 을 닫으면 갱신이 실패합니다.**

애플리케이션 기동은 user data 에 넣지 않습니다. 인프라 apply 가 Flyway 를
실행시키는 상황을 만들지 않기 위해서입니다.

---

# 6. RDS 설계

| 항목                  | 권장값                             |
| ------------------- | ------------------------------- |
| Identifier          | `swimming-prod-db`              |
| Engine              | PostgreSQL 17                   |
| Publicly accessible | **false**                       |
| Storage encryption  | **true**                        |
| SG                  | `swimming-prod-db-sg`           |
| Subnet Group        | `swimming-prod-db-subnet-group` |
| Subnets             | DB A + DB B                     |
| Multi-AZ            | 초기에는 false                      |
| Backup retention    | 코드 기본 7일 / **현재 계정 1일** (아래 참고)     |
| Deletion protection | true + Terraform `prevent_destroy` |
| Port                | 5432                            |
| 마스터 암호              | RDS 관리 (Secrets Manager)        |
| pgvector            | PostgreSQL extension            |
| Internet route      | 없음                              |

## Free plan 제약

이 AWS 계정은 Free plan 이라 RDS 백업 보존 기간에 상한이 있습니다. 코드 기본값
7일로 만들면 `CreateDBInstance` 가 `FreeTierRestrictionError` 로 거절합니다.
그래서 `prod.auto.tfvars` 에서 `db_backup_retention_days = 1` 로 낮춰 둡니다.
**계정 플랜을 올린 뒤 7일로 되돌리는 것이 맞습니다.** 1일 보존은 하루 전
시점으로만 복구할 수 있다는 뜻입니다.

애플리케이션 계정은 마스터 계정과 분리합니다. RDS 관리 마스터 시크릿을 그대로
애플리케이션 자격증명으로 쓰지 않습니다.

---

# 7. S3

| 항목            | 설계                       |
| ------------- | ------------------------ |
| Bucket        | `swimming-prod-web`      |
| Public Access | **4개 항목 모두 Block**       |
| Ownership     | `BucketOwnerEnforced` (ACL 비활성) |
| 접근            | CloudFront OAC만          |
| Encryption    | SSE-S3 + bucket key      |
| Versioning    | Enabled                  |
| 용도            | Web static + video       |
| 정책            | OAC read + 비TLS 접근 Deny  |

버킷 이름은 전역 유일해야 하므로 `web_bucket_name` 변수로 덮어쓸 수 있습니다.

## 미디어 버킷을 분리하는 이유

| 항목            | 설계                                   |
| ------------- | ------------------------------------ |
| Bucket        | `swimming-prod-media`                |
| 용도            | place 배경 영상·음악 등 팀이 넣는 에셋            |
| 키 접두사         | `media/` — `/media/*` 경로와 1:1 로 맞춘다  |
| Public Access | 4개 항목 모두 Block, CloudFront OAC 만     |
| Versioning    | **끔**                                |
| Lifecycle     | 미완료 멀티파트 7일 후 정리                     |

프론트 배포가 `aws s3 sync --delete` 를 쓰기 때문에, 빌드 산출물이 아닌 파일을 같은
버킷에 두면 **다음 배포에서 삭제됩니다.** 이것이 버킷을 나누는 첫 번째 이유다.

**버저닝을 끕니다.** 영상은 파일이 크고 다시 올릴 수 있습니다. 버저닝을 켜고 만료 규칙이
없으면 교체할 때마다 옛 버전이 영구히 쌓입니다. 대신 **미완료 멀티파트 정리 규칙은 반드시
둡니다** — 대용량 업로드가 중간에 끊기면 조각이 남아 계속 과금되는데, 일반 객체 목록에는
보이지 않아 알아채기 어렵습니다.

**사용자 업로드는 이 버킷에 두지 않습니다.** 접근 통제 방식이 달라서(백엔드가 발급하는
presigned URL, CloudFront 공개 경로 아님) 그 기능이 생길 때 별도 버킷으로 설계합니다.

---

# 8. CloudFront

| 항목               | 설계                          |
| ---------------- | --------------------------- |
| Name/Tag         | `swimming-prod-web-cdn`     |
| Origin           | `swimming-prod-web`         |
| Origin Access    | OAC (sigv4, always)         |
| Viewer protocol  | HTTPS redirect              |
| Cache policy     | Managed-CachingOptimized    |
| Response headers | Managed-SecurityHeadersPolicy |
| SPA routing      | CloudFront Function (기본 비헤이비어 전용) |
| API              | `/api/*` → EC2 오리진, **캐시 없음**       |
| Media            | `/media/*` → S3 미디어 오리진, **캐시함**    |
| TLS              | ACM, `TLSv1.2_2021`, sni-only |
| ACM region       | **us-east-1**               |
| Price class      | PriceClass_200              |
| Domain           | `swimming-now.kro.kr`       |
| S3 direct access | 차단                          |

OAC 를 사용하면 S3 origin 접근을 이 배포 하나로 제한할 수 있습니다. 버킷 정책의
`AWS:SourceArn` 조건이 그 역할을 합니다. ([AWS Documentation][4])

## ACM 인증서는 2단계로 적용합니다

도메인이 Route 53 이 아니라 외부 등록기관에 있어 Terraform 이 검증 레코드를
자동으로 발행할 수 없습니다. 그래서 `aws_acm_certificate_validation` 을 **쓰지
않습니다.** 넣으면 검증 레코드가 없는 채로 apply 가 timeout 까지 멈춥니다.

1. `web_domain_name` 을 지정하고 `attach_web_domain = false` 로 apply
   → 인증서 생성(검증 대기), CloudFront 는 기본 도메인으로 동작
2. `acm_validation_record` 출력의 CNAME 을 내도메인.한국에 등록하고 발급 완료 대기
3. `attach_web_domain = true` 로 재apply → alias 와 인증서 연결

## API 를 같은 배포에 통합합니다

`/api/*` 를 같은 CloudFront 배포에 붙여 브라우저 기준 **동일 오리진**으로
만듭니다. 그래서 CORS 도, 크로스사이트 쿠키(SameSite) 문제도 생기지 않습니다.
프론트는 `/api` 상대경로를 그대로 쓰고, 리프레시 쿠키는 `SameSite=Lax` 로
충분합니다.

| 항목                | 값                                    |
| ----------------- | ------------------------------------ |
| Path pattern      | `/api/*`                             |
| Origin            | API 호스트명 (EIP 가 아니라 도메인)             |
| Origin protocol   | `https-only` (오리진 인증서를 CloudFront 가 검증) |
| Viewer protocol   | `https-only`                         |
| Cache policy      | **`Managed-CachingDisabled`**        |
| Origin request    | `Managed-AllViewer`                  |
| Methods           | GET/HEAD/OPTIONS/PUT/POST/PATCH/DELETE |

**캐시 정책이 핵심입니다.** 기본 비헤이비어가 쓰는 `CachingOptimized` 를 API 에
적용하면 CloudFront 가 `Authorization` 과 `Cookie` 를 오리진에 전달하지 않고
응답을 캐싱합니다. 한 사용자의 응답이 다른 사용자에게 나가는 사고로 이어집니다.
`CachingDisabled` + `AllViewer` 조합이 이 둘을 동시에 막습니다.

**리다이렉트를 쓰지 않습니다.** `redirect-to-https` 로 두면 HTTP POST 가 301 을
받고 요청 본문이 사라집니다. `https-only` 는 대신 403 을 돌려줍니다.

## SPA 폴백은 CloudFront Function 으로 합니다

`custom_error_response` 는 **배포 전체에 적용되고 비헤이비어별로 나눌 수
없습니다.** 403/404 를 `index.html` 로 매핑하면 백엔드의 404·403 ProblemDetail
응답까지 HTML + 200 으로 바뀌어 API 가 망가집니다.

그래서 `swimming-prod-spa-router` viewer-request 함수를 **기본 비헤이비어에만**
붙입니다. 확장자가 없는 경로만 `/index.html` 로 재작성하므로 `/api/*` 는 함수를
거치지 않고, 실제로 없는 정적 파일은 index.html 로 가려지지 않고 자기 상태
코드를 그대로 돌려줍니다.

## API 호스트명은 여전히 필요합니다

통합해도 사라지지 않습니다.

- CloudFront 커스텀 오리진은 **IP 를 받지 않고 도메인만** 받습니다.
- `https-only` 라 CloudFront 가 오리진 인증서를 검증하므로 **certbot 이 그대로
  필요합니다.**

다만 이 호스트명은 사용자에게 노출되지 않는 내부 경로가 됩니다. 동시에 API 가
CloudFront 를 우회해 직접 접근 가능한 상태로 남으므로, 나중에 app-sg 의 443
인바운드를 CloudFront 관리형 접두사 목록
(`com.amazonaws.global.cloudfront.origin-facing`) 으로 좁힐 수 있습니다. 80 은
ACME 갱신 때문에 열어 두어야 합니다.

---

# 9. 외부 DNS 설계

`내도메인.한국`(kro.kr)에서 관리합니다.

| 호스트명                   | 레코드                          | 대상         | 용도            |
| --------------------- | ---------------------------- | ---------- | ------------- |
| `swimming-now.kro.kr`       | CNAME | CloudFront | prod frontend (사용자가 보는 주소) |
| `api.swimming-now.kro.kr`   | A     | EC2 EIP    | CloudFront 의 `/api/*` 오리진   |
| `_xxxx.swimming-now.kro.kr` | CNAME | ACM 검증값    | 인증서 발급·갱신                 |

## kro.kr 운용 메모

**하위 이름 CNAME 등록이 가능한 것을 확인했습니다.** 따라서 ACM DNS 검증
(`_1a2b3c….swimming-now.kro.kr` CNAME)과 CloudFront alias 연결이 계획대로
동작합니다. 프론트 호스트명은 apex 가 아니므로 CloudFront 를 CNAME 으로 직접
가리킬 수 있습니다.

**API 오리진은 하위 이름으로 둡니다.** `api.swimming-now.kro.kr` 에 A 레코드를
걸어 EC2 EIP 를 가리킵니다. CNAME 은 IP 를 가리킬 수 없으므로 반드시 A 여야
합니다. 이 이름은 CloudFront 가 오리진에 접속할 때만 쓰이고 사용자에게는
노출되지 않습니다. 여기 TLS 는 Nginx + Let's Encrypt HTTP-01 이라 A 레코드만
있으면 되고 ACM 검증 레코드는 필요 없습니다.

**인증서 발급 후 검증 레코드를 지우지 않습니다.** ACM 은 갱신 시에도 같은
레코드로 재검증하므로, 삭제하면 자동 갱신이 실패합니다.

---

# 10. 배포 경로

| 대상       | 경로                                                    |
| -------- | ----------------------------------------------------- |
| Backend  | ECR (immutable tag) → SSM 으로 EC2 접속 → Docker Compose   |
| Frontend | Vite build → S3 업로드 → CloudFront invalidation          |
| Secret   | Secrets Manager 컨테이너를 Terraform 밖에서 채움                |
| 스키마      | Flyway. Terraform 은 테이블을 만들지 않음                        |

## Terraform state

| 항목      | 값                                                       |
| ------- | ------------------------------------------------------- |
| Bucket  | `swimming-prod-tfstate-223910471789`                    |
| Key     | `prod/terraform.tfstate`                                |
| Region  | `ap-northeast-2`                                        |
| 잠금      | S3 네이티브 (`use_lockfile`). DynamoDB 테이블 없음               |
| 보호      | 버저닝, SSE-S3, Public Access Block 4종, TLS-only 정책        |
| 수명주기    | 비현행 버전 90일 만료, 중단된 멀티파트 7일 정리                          |
| 생성 주체   | **AWS CLI (Terraform 밖)**                               |

백엔드는 자기 state 를 담을 버킷을 스스로 부트스트랩할 수 없어 CLI 로 먼저
만들었습니다. 버킷이나 버저닝을 지우면 state 손상 시 복구 경로가 사라집니다.
`terraform` 블록의 `required_version` 이 `>= 1.10.0` 인 이유도 이 잠금 방식
때문입니다.

---

# 11. 가용성과 현재 한계

명시적으로 받아들이고 가는 제약입니다.

- **단일 AZ, 단일 인스턴스.** EC2 1대가 Nginx·Spring 을 모두 담당하고 AZ 도
  하나입니다. 이 인스턴스나 AZ-a 가 죽으면 API 전체가 내려갑니다.
- **RDS Multi-AZ = false.** 서브넷 그룹은 2개 AZ 에 걸쳐 있어 나중에 true 로만
  바꾸면 되지만, 지금은 standby 가 없습니다.
- **ALB 없음.** EIP 직결이라 헬스체크 기반 교체나 무중단 배포가 없습니다.
- **WAF 없음.** API 가 CloudFront 를 거치므로 붙일 자리는 생겼지만 아직 없습니다. API 호스트명으로 CloudFront 를 우회한 직접 접근도 아직 막지 않았습니다.
- **CI/CD 없음.** 배포는 수동 절차입니다.

프론트엔드는 CloudFront + S3 라서 이 제약의 영향을 받지 않습니다.

---

# 12. 향후: Nginx / Spring 분리와 NAT Instance

트래픽이나 보안 요구가 커지면 다음 순서로 분리합니다.

| 리소스           | 이름                        | CIDR / 설정             |
| ------------- | ------------------------- | --------------------- |
| Private App A | `swimming-prod-app-a`     | `10.0.11.0/24` (예약됨)  |
| App RT        | `swimming-prod-app-rt`    | `0.0.0.0/0 → NAT`     |
| NAT Instance  | `swimming-prod-nat`       | Public A              |
| NAT EIP       | `swimming-prod-nat-eip`   | -                     |
| NAT SG        | `swimming-prod-nat-sg`    | inbound 80/443 from `10.0.11.0/24` |
| Nginx SG      | `swimming-prod-nginx-sg`  | outbound 8080 → api-sg **+ 443/80/53/123** |
| API SG        | `swimming-prod-api-sg`    | inbound 8080 from nginx-sg |

## NAT Instance 는 설정 두 가지가 모두 필요합니다

```text
Source/Destination Check = false
```

일반 EC2 는 자신이 source/destination 이 아닌 패킷을 전달하지 못하도록
검사하는데, NAT 는 다른 인스턴스의 패킷을 전달해야 하므로 이 검사를 꺼야
합니다. ([AWS Documentation][2])

여기에 더해 **인스턴스 안에서 포워딩을 직접 켜야 합니다.** 예전
`amzn-ami-vpc-nat` AMI 는 Amazon Linux 2023 에 없으므로 user data 로 설정합니다.

```bash
sysctl -w net.ipv4.ip_forward=1
iptables -t nat -A POSTROUTING -o "$PRIMARY_ENI" -j MASQUERADE
```

`source_dest_check = false` 만 끄고 이 설정을 빼면 private 서브넷은 통신
불능이 됩니다.

## 분리 시 주의점

- **Nginx SG 의 outbound 를 8080 만으로 두면 안 됩니다.** ACME 갱신, `dnf`,
  SSM Agent, CloudWatch 가 전부 outbound 443 입니다.
- **Nginx → Spring 주소를 하드코딩하지 않습니다.** Spring 인스턴스를 재생성하면
  private IP 가 바뀝니다. Terraform `templatefile` 로 주입하거나 private hosted
  zone 을 씁니다.
- NAT Instance 자체가 단일 장애점이므로 CloudWatch 알람 대상에 포함합니다.
- NAT Gateway 는 월 $45 수준, NAT Instance(t4g.nano)는 월 $3 수준입니다. 대신
  가용성과 운영 부담을 직접 떠안습니다. `t4g` 는 arm64 이므로 AMI 필터를
  x86_64 에서 바꿔야 합니다.

---

# 13. 향후: Bedrock 설계

| 리소스             | 이름                                       |
| --------------- | ---------------------------------------- |
| VPC Endpoint    | `swimming-prod-bedrock-runtime-vpce`     |
| Endpoint SG     | `swimming-prod-bedrock-vpce-sg`          |
| Service         | `com.amazonaws.<region>.bedrock-runtime` |
| Type            | Interface                                |
| Private DNS     | true                                     |
| inbound         | 443 from `swimming-prod-app-sg` only     |
| Endpoint Policy | Spring Role + 필요한 model/action만          |

구조:

```text
Spring
  │
  │ 443
  ▼
Bedrock VPCE SG
  │
  ▼
PrivateLink
  │
  ▼
Bedrock
```

Bedrock Endpoint 자체의 기본 policy 는 전체 접근을 허용할 수 있기 때문에, 실제
도입 시에는 custom Endpoint Policy 로 **Principal / Action / Resource 를
제한**하는 게 좋습니다. ([AWS Documentation][1])

Private DNS 전제 조건인

```text
enableDnsSupport   = true
enableDnsHostnames = true
```

는 VPC 에 이미 적용되어 있습니다. ([AWS Documentation][5])

---

# 14. Terraform 리소스 목록

| 영역                 | Terraform Resource                     | 생성 시점 |
| ------------------ | -------------------------------------- | ----- |
| VPC                | `aws_vpc`                              | 지금    |
| Subnet             | `aws_subnet` ×4                        | 지금    |
| IGW                | `aws_internet_gateway`                 | 지금    |
| Routing            | `aws_route_table` ×2                   | 지금    |
| Association        | `aws_route_table_association` ×4       | 지금    |
| S3 Gateway VPCE    | `aws_vpc_endpoint`                     | 지금    |
| App SG             | `aws_security_group`                   | 지금    |
| DB SG              | `aws_security_group`                   | 지금    |
| SG 규칙              | `aws_vpc_security_group_*_rule`        | 지금    |
| App EC2            | `aws_instance`                         | 지금    |
| App EIP            | `aws_eip`                              | 지금    |
| IAM Role           | `aws_iam_role`                         | 지금    |
| IAM 인라인 정책         | `aws_iam_role_policy`                  | 지금    |
| SSM 관리형 정책 연결       | `aws_iam_role_policy_attachment`       | 지금    |
| Instance Profile   | `aws_iam_instance_profile`             | 지금    |
| ECR                | `aws_ecr_repository` + lifecycle policy | 지금    |
| Secrets 컨테이너       | `aws_secretsmanager_secret`            | 지금    |
| RDS subnet group   | `aws_db_subnet_group`                  | 지금    |
| RDS parameter group | `aws_db_parameter_group`               | 지금    |
| RDS                | `aws_db_instance`                      | 지금    |
| S3 (web)           | `aws_s3_bucket`                        | 지금    |
| S3 (media)         | `aws_s3_bucket` + lifecycle + OAC + policy | 지금 |
| S3 public block    | `aws_s3_bucket_public_access_block`    | 지금    |
| S3 ownership       | `aws_s3_bucket_ownership_controls`     | 지금    |
| S3 암호화/버저닝         | `aws_s3_bucket_server_side_encryption_configuration`, `aws_s3_bucket_versioning` | 지금 |
| Bucket Policy      | `aws_s3_bucket_policy`                 | 지금    |
| CloudFront OAC     | `aws_cloudfront_origin_access_control` | 지금    |
| CloudFront         | `aws_cloudfront_distribution`          | 지금    |
| ACM                | `aws_acm_certificate` (us-east-1 alias) | 지금    |
| CloudWatch / SNS   | log group, alarms, SNS topic           | 지금    |
| SSM Association    | `aws_ssm_association`                  | 지금    |
| Private App Subnet | `aws_subnet`                           | 나중    |
| NAT Instance       | `aws_instance` + `source_dest_check=false` | 나중 |
| NAT EIP / SG       | `aws_eip`, `aws_security_group`        | 나중    |
| Bedrock VPCE       | `aws_vpc_endpoint`                     | 나중    |
| Bedrock SG         | `aws_security_group`                   | 나중    |
| Bedrock IAM policy | `aws_iam_policy`                       | 나중    |
