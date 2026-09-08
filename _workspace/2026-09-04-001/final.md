## 목적

Terraform으로 만든 AWS 인프라에 코드를 올리는 경로가 없다. 지금은 사람이 직접 이미지를 빌드해 ECR에 올리고 EC2에 들어가 컨테이너를 갈아끼운다. 이 과정을 GitHub Actions로 옮겨 main에 머지하면 배포가 끝나는 상태로 만든다.

장기 AWS 자격증명을 GitHub에 두지 않는 것을 전제로 한다. OIDC로 역할을 맡는다. 배포용 역할과 인프라 조회용 역할을 나눠 매 푸시에 쓰이는 권한을 좁게 유지한다.

- 작업 브랜치: `feature/deploy`

## 작업 범위

- [ ] GitHub OIDC provider와 역할 2개 (배포용, terraform plan용)
- [ ] CI: 백엔드 Gradle 빌드, 프론트 lint와 프로덕션 빌드
- [ ] 백엔드 배포: ECR push 후 SSM Run Command로 컨테이너 교체
- [ ] 프론트 배포: S3 업로드와 CloudFront 무효화
- [ ] Terraform: PR에 plan 게시 (apply는 로컬 유지)
- [ ] 헬스체크 실패 시 직전 이미지로 롤백

## 완료 조건

- [ ] main 머지만으로 백엔드와 프론트가 배포된다.
- [ ] GitHub에 장기 AWS 키를 저장하지 않는다.
- [ ] 배포 역할로는 인프라를 변경할 수 없다.
- [ ] 배포 실패 시 이전 이미지로 자동 복구된다.
- [ ] PR에서 인프라 변경 내용을 머지 전에 확인할 수 있다.

## 제외 범위

- Terraform apply 자동화 (plan까지만)
- 스테이징 환경
- 무중단 배포 (컨테이너 교체 중 짧은 중단 허용)

<!-- HUMANIZE-SUMMARY v1.6.1
run_id: 2026-09-04-001
mode: 보수 (conservative) / route_hint: light
metrics:
  char_in: 807
  char_out: 800
  change_rate: 2.6%
  self_check: 6/6
  grade: A
categories:  # before → after
  C-11 연결어미 뒤 쉼표: 2 → 0
  D-1 결산 lexicon: 0 → 0
  A-8 이중 피동: 0 → 0
  H-1 문두 접속사: 0 → 0
  J-1 본문 볼드: 0 → 0
self_check:
  - 고유명사·수치·인용·내용 앵커 100% 보존: OK (Terraform·AWS·ECR·EC2·SSM·OIDC·GitHub Actions·CloudFront·S3·main·feature/deploy·terraform plan/apply·Gradle·lint 전부 원형)
  - 변경률 30% 이하: OK (2.6%)
  - 장르 이탈 없음: OK (GitHub 이슈 리포트체 유지)
  - register 보존: OK (평서 '-한다' 체 유지, 격식 상향 없음)
  - S1 잔존 0건: OK
  - 인공 표현 추가 없음: OK (내용 추가·삭제 0, 마크다운 구조·체크박스 구 형태 그대로)
highlights:
  - id: C-11
    before: "이 과정을 GitHub Actions로 옮겨, main에 머지하면 배포가 끝나는 상태로 만든다."
    after: "이 과정을 GitHub Actions로 옮겨 main에 머지하면 배포가 끝나는 상태로 만든다."
  - id: C-11 + E-1
    before: "OIDC로 역할을 맡고, 배포용 역할과 인프라 조회용 역할을 나눠 매 푸시에 쓰이는 권한을 좁게 유지한다."
    after: "OIDC로 역할을 맡는다. 배포용 역할과 인프라 조회용 역할을 나눠 매 푸시에 쓰이는 권한을 좁게 유지한다."
residual_findings: (없음)
grade_reason: "A — S1 잔존 0건, 자체검증 6항 통과. 원문이 이미 사람 글에 가까워(route_hint light) 변경률이 A 기준 하한(10%)보다 낮으나, 손댈 근거가 있는 구간이 C-11 2건뿐이라 보수 강도상 추가 편집은 과윤문."
-->
