# 🎯 Job Tracker — 채용 공고 지원 관리

취업 준비생이 **채용 공고를 스크랩하고 지원 현황을 한눈에 관리**하는 웹 서비스입니다.

> **왜 만들었나요?** — 취업 준비하면서 실제로 필요해서 만들었습니다.
> 공고를 어디에 지원했는지, 지금 면접 단계인지, 어떤 회사가 합격했는지 기억하기 어려워서,
> 지원 상태를 직접 관리할 수 있는 서비스가 있으면 좋겠다고 생각했습니다.

## 📸 화면

![로그인](docs/screenshot-login.png)

![공고 목록](docs/screenshot-list.png)

![공고 추가](docs/screenshot-modal.png)

## ✨ 주요 기능

| 기능 | 설명 |
|---|---|
| 🔐 회원가입 / 로그인 | JWT 기반 인증 (Spring Security) |
| 📝 공고 스크랩 | 회사명·포지션·링크·마감일·메모로 공고 저장 |
| 🎢 지원 상태 관리 | 지원 예정 → 지원함 → 면접 → 합격 / 불합격 (상태 변경) |
| 🔍 검색 & 필터 | 회사명/포지션 키워드 검색 + 상태별 필터 |
| 📊 통계 대시보드 | 상태별 지원 현황 한눈에 보기 |

## 🛠️ 기술 스택

```
백엔드:  Spring Boot 4.1 · Spring Security + JWT · Spring Data JPA · PostgreSQL
프론트:  React 19 · Vite · TypeScript · React Router
테스트:  JUnit + MockMvc · JaCoCo (백엔드 커버리지 97%)
         Vitest + Testing Library (프론트 커버리지 73%)
         Playwright (실화면 자동 검증)
```

## 🗂️ 프로젝트 구조

```
job-tracker/
├── src/main/java/com/example/jobtracker/
│   ├── controller/        # API 엔드포인트 (auth, job)
│   ├── service/           # 비즈니스 로직
│   ├── repository/        # DB 접근
│   ├── entity/            # JPA 엔티티 (users, job_postings)
│   ├── dto/               # 요청/응답 객체 (Entity 직접 노출 방지)
│   ├── security/          # JWT 필터, SecurityConfig
│   └── exception/         # 전역 예외 처리
└── frontend/
    ├── src/pages/         # LoginPage, JobListPage
    ├── src/components/    # Header, StatusBadge, JobFormModal
    └── src/api.ts         # fetch 래퍼 (토큰 관리)
```

## 🚀 실행 방법

### 백엔드 (Spring Boot, 8080)

```bash
# 1. PostgreSQL에 DB 생성
createdb job_tracker

# 2. 환경변수 설정 (JWT 시크릿 — 32바이트 이상)
# Windows: setx JWT_SECRET "여기에-긴-랜덤-문자열"

# 3. 실행
mvn spring-boot:run
```

### 프론트 (Vite, 5173)

```bash
cd frontend
npm install
npm run dev
```

접속: http://localhost:5173

## 🧪 테스트

```bash
# 백엔드: 테스트 + 커버리지 리포트 (target/site/jacoco)
mvn test

# 프론트: 테스트 + 커버리지 리포트 (frontend/coverage)
cd frontend
npm test
npm run coverage
```

| 영역 | 테스트 수 | 커버리지 |
|---|---|---|
| 백엔드 (Java) | 22개 | 라인 97% |
| 프론트 (TS) | 21개 | 라인 73% |

## 📡 주요 API

| Method | URL | 설명 |
|---|---|---|
| POST | /api/auth/signup | 회원가입 |
| POST | /api/auth/login | 로그인 (JWT 발급) |
| GET | /api/auth/me | 내 정보 |
| GET | /api/jobs | 내 공고 목록 (?status=&keyword=) |
| POST | /api/jobs | 공고 저장 |
| PATCH | /api/jobs/{id} | 공고 수정 (상태 변경 포함) |
| DELETE | /api/jobs/{id} | 공고 삭제 |
| GET | /api/jobs/stats | 상태별 통계 |

## 🔒 보안

- 비밀번호는 **BCrypt로 암호화**하여 저장 (평문 저장 금지)
- 모든 API는 **JWT 토큰 필수** (회원가입/로그인 제외)
- **내 공고만** 조회/수정/삭제 가능 — 남의 공고 접근 시 404 (존재 여부도 숨김)
- 설정 파일(application.yml)은 git에 커밋하지 않음 — `application.example.yml` 양식 제공
