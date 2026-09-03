# 1. 전체 아키텍처
architecture.mmd 참고
---

# 2. 네트워크 설계

| 구분            | Terraform / 리소스명          |           CIDR | 역할                         | 외부 접근     |
| ------------- | ------------------------- | -------------: | -------------------------- | --------- |
| VPC           | `swimming-prod-vpc`       |  `10.0.0.0/16` | 전체 네트워크                    | X         |
| Public A      | `swimming-prod-public-a`  |  `10.0.1.0/24` | Nginx, NAT Instance        | IGW       |
| Private App A | `swimming-prod-app-a`     | `10.0.11.0/24` | Spring Boot                | NAT/VPCE만 |
| Private DB A  | `swimming-prod-db-a`      | `10.0.21.0/24` | RDS subnet group           | X         |
| Private DB C  | `swimming-prod-db-c`      | `10.0.22.0/24` | RDS subnet group           | X         |
| IGW           | `swimming-prod-igw`       |              - | Public subnet 인터넷          | O         |
| Public RT     | `swimming-prod-public-rt` |              - | `0.0.0.0/0 → IGW`          | O         |
| App RT        | `swimming-prod-app-rt`    |              - | `0.0.0.0/0 → NAT Instance` | 간접        |
| DB RT         | `swimming-prod-db-rt`     |              - | 기본 local route만            | X         |

---

# 3. Security Group 설계

## `swimming-prod-nginx-sg`

| 방향       | Protocol | Port | 대상/Source              | 이유                         |
| -------- | -------- | ---: | ---------------------- | -------------------------- |
| Inbound  | TCP      |  443 | `0.0.0.0/0`            | HTTPS                      |
| Inbound  | TCP      |   80 | `0.0.0.0/0`            | HTTP→HTTPS / Let's Encrypt |
| Outbound | TCP      | 8080 | `swimming-prod-api-sg` | Spring 호출                  |

**8080을 인터넷에 절대 열지 않습니다.**

---

## `swimming-prod-api-sg`

| 방향       | Protocol | Port | 대상/Source                |
| -------- | -------- | ---: | ------------------------ |
| Inbound  | TCP      | 8080 | `swimming-prod-nginx-sg` |
| Outbound | TCP      | 5432 | `swimming-prod-db-sg`    |
| Outbound | TCP      |  443 | 외부 HTTPS / 추후 VPCE       |

가능한 부분은 IP가 아니라 **SG → SG 참조**로 제한합니다.

---

## `swimming-prod-db-sg`

| 방향       | Protocol | Port | 대상/Source                       |
| -------- | -------- | ---: | ------------------------------- |
| Inbound  | TCP      | 5432 | **`swimming-prod-api-sg` only** |
| Outbound | 기본       |    - | 보통 기본값 사용 가능                    |

---

# 4. NAT Instance 보안 설계

## NAT SG

`swimming-prod-nat-sg`

| 방향       | Protocol | Port | Source/Destination |
| -------- | -------- | ---: | ------------------ |
| Inbound  | TCP      |  443 | `10.0.11.0/24`     |
| Outbound | TCP      |  443 | `0.0.0.0/0`        |

### 중요한 NAT 설정

NAT Instance에는 반드시:

```text
Source/Destination Check = false
```

가 필요합니다.

일반 EC2는 자신이 source/destination이 아닌 패킷을 전달하지 못하도록 검사하는데, NAT는 다른 인스턴스의 패킷을 전달해야 하므로 이 검사를 꺼야 합니다. AWS 공식 NAT Instance 절차에도 이 설정이 명시되어 있습니다. ([AWS Documentation][2])

---

# 5. NAT에서 Google만 허용할 수 있나?

여기서는 한계가 있습니다.

Security Group은:

```text
google.com
accounts.google.com
oauth2.googleapis.com
```

같은 **domain name 기반 정책을 걸 수 없습니다.**

IP/CIDR + Port 기반입니다.

Google IP 범위가 변할 수 있으므로:

```text
443 → 특정 Google IP 몇 개
```

로 고정하는 것도 실무적으로 불편합니다.

따라서 지금은:

```text
Source = Private App Subnet
Destination Port = 443
```

정도로 제한하는 게 비용/복잡도 대비 적절합니다.

향후 정말 강력한 egress allowlist가 필요하면 AWS Network Firewall이나 HTTP proxy 같은 별도 계층을 고려할 수 있지만, 현재 사이드 프로젝트에서는 과합니다.

---

# 6. Nginx EC2

| 항목          | 설계                        |
| ----------- | ------------------------- |
| Name        | `swimming-prod-nginx`     |
| Subnet      | `swimming-prod-public-a`  |
| Public IPv4 | EIP만 사용                   |
| EIP         | `swimming-prod-nginx-eip` |
| SG          | `swimming-prod-nginx-sg`  |
| 역할          | HTTPS/WSS reverse proxy   |
| Spring 연결   | Private IP/DNS            |
| SSH         | Public 22 비권장             |
| TLS         | Let's Encrypt             |
| Application | 설치하지 않음                   |

---

# 7. Spring EC2

| 항목             | 설계                     |
| -------------- | ---------------------- |
| Name           | `swimming-prod-api`    |
| Subnet         | `swimming-prod-app-a`  |
| Public IP      | **없음**                 |
| SG             | `swimming-prod-api-sg` |
| Service        | Spring Boot            |
| Listen         | `:8080`                |
| Inbound        | Nginx SG only          |
| DB             | PostgreSQL :5432       |
| 외부 API         | NAT Instance           |
| Bedrock        | 추후 VPCE                |
| AWS credential | Instance Role          |
| SSH            | Public 접근 없음           |

---

# 8. RDS 설계

| 항목                  | 권장값                             |
| ------------------- | ------------------------------- |
| Identifier          | `swimming-prod-db`              |
| Engine              | PostgreSQL                      |
| Publicly accessible | **false**                       |
| Storage encryption  | **true**                        |
| SG                  | `swimming-prod-db-sg`           |
| Subnet Group        | `swimming-prod-db-subnet-group` |
| Subnets             | DB A + DB C                     |
| Multi-AZ            | 초기에는 false                      |
| Backup retention    | 7 days 정도                       |
| Deletion protection | prod=true 권장                    |
| Port                | 5432                            |
| pgvector            | PostgreSQL extension            |
| Internet route      | 없음                              |

---

# 9. S3 + CloudFront

## S3

| 항목            | 설계                  |
| ------------- | ------------------- |
| Bucket        | `swimming-prod-web` |
| Public Access | **모두 Block**        |
| ACL           | 사용하지 않음             |
| 접근            | CloudFront OAC만     |
| Encryption    | SSE-S3 또는 필요 시 KMS  |
| 용도            | Web static + video  |
| Versioning    | 필요에 따라              |
---

# 10. CloudFront

| 항목               | 설계                      |
| ---------------- | ----------------------- |
| Name/Tag         | `swimming-prod-web-cdn` |
| Origin           | `swimming-prod-web`     |
| Origin Access    | OAC                     |
| HTTP             | HTTPS redirect          |
| TLS              | ACM                     |
| ACM region       | **us-east-1**           |
| Domain           | `app.example.com`       |
| S3 direct access | 차단                      |

CloudFront private content에는 OAC를 사용하여 S3 origin 접근을 CloudFront로 제한할 수 있습니다. ([AWS Documentation][4])

---

# 11. 외부 DNS 설계

`내도메인.한국`에서 관리:

| DNS                   | 현재         | 향후            |
| --------------------- | ---------- | ------------- |
| `app.example.com`     | CloudFront | prod frontend |
| `api.example.com`     | Nginx EIP  | prod API      |
| `dev.example.com`     | 없음         | dev frontend  |
| `dev-api.example.com` | 없음         | dev API       |

---

# 13. 향후 Bedrock 설계

나중에 추가:

| 리소스             | 이름                                       |
| --------------- | ---------------------------------------- |
| VPC Endpoint    | `swimming-prod-bedrock-runtime-vpce`     |
| Endpoint SG     | `swimming-prod-bedrock-vpce-sg`          |
| Service         | `com.amazonaws.<region>.bedrock-runtime` |
| Type            | Interface                                |
| Private DNS     | true                                     |
| inbound         | 443 from `swimming-prod-api-sg` only     |
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

Bedrock Endpoint 자체의 기본 policy는 전체 접근을 허용할 수 있기 때문에, 실제 도입 시에는 custom Endpoint Policy로 **Principal / Action / Resource를 제한**하는 게 좋습니다. AWS Bedrock VPCE가 이런 정책 제한을 공식 지원합니다. ([AWS Documentation][1])

Private DNS를 쓰려면 지금 VPC부터:

```text
enableDnsSupport   = true
enableDnsHostnames = true
```

로 만들어두세요. Interface Endpoint의 Private DNS에도 두 옵션이 필요합니다. ([AWS Documentation][5])

---

# 14. Terraform 리소스 목록

Terraform 관점에서 지금 필요한 리소스를 정리하면:

| 영역                 | Terraform Resource                     | 생성 시점 |
| ------------------ | -------------------------------------- | ----- |
| VPC                | `aws_vpc`                              | 지금    |
| Subnet             | `aws_subnet` ×4                        | 지금    |
| IGW                | `aws_internet_gateway`                 | 지금    |
| Routing            | `aws_route_table`                      | 지금    |
| Association        | `aws_route_table_association`          | 지금    |
| Nginx SG           | `aws_security_group`                   | 지금    |
| API SG             | `aws_security_group`                   | 지금    |
| DB SG              | `aws_security_group`                   | 지금    |
| NAT SG             | `aws_security_group`                   | 지금    |
| Nginx              | `aws_instance`                         | 지금    |
| Nginx EIP          | `aws_eip`                              | 지금    |
| NAT Instance       | `aws_instance`                         | 지금    |
| NAT EIP            | `aws_eip`                              | 지금    |
| NAT Source/Dest    | EC2 `source_dest_check=false`          | 지금    |
| Spring EC2         | `aws_instance`                         | 지금    |
| IAM Role           | `aws_iam_role`                         | 지금    |
| Instance Profile   | `aws_iam_instance_profile`             | 지금    |
| RDS subnet group   | `aws_db_subnet_group`                  | 지금    |
| RDS                | `aws_db_instance`                      | 지금    |
| S3                 | `aws_s3_bucket`                        | 지금    |
| S3 public block    | `aws_s3_bucket_public_access_block`    | 지금    |
| Bucket Policy      | `aws_s3_bucket_policy`                 | 지금    |
| CloudFront OAC     | `aws_cloudfront_origin_access_control` | 지금    |
| CloudFront         | `aws_cloudfront_distribution`          | 지금    |
| ACM                | `aws_acm_certificate`                  | 지금    |
| Bedrock VPCE       | `aws_vpc_endpoint`                     | 나중    |
| Bedrock SG         | `aws_security_group`                   | 나중    |
| Bedrock IAM policy | `aws_iam_policy`                       | 나중    |


