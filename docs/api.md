# Job Tracker API 명세

3번 프로젝트(안드로이드 앱)가 사용하는 API 계약서입니다.
베이스 URL: `https://job-tracker-so4v.onrender.com`

- 모든 요청/응답은 JSON (UTF-8)
- 인증: `Authorization: Bearer <accessToken>` 헤더
- 에러 응답: `{ "message": "..." , "timestamp": "..." }` (HTTP 상태코드와 함께)

---

## 1. 인증

### 회원가입
```
POST /api/auth/signup
Body: { "email": "a@b.com", "password": "password123", "nickname": "이름" }
201: { "id": 1, "email": "a@b.com", "nickname": "이름", "createdAt": "...", "keywords": [] }
400: 이메일 형식 오류 / 비밀번호 8자 미만 / 닉네임 2~20자 외
409: 이미 가입된 이메일
```

### 로그인
```
POST /api/auth/login
Body: { "email": "a@b.com", "password": "password123" }
200: { "accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 3600 }
401: 이메일 또는 비밀번호 불일치
```

### 내 정보
```
GET /api/auth/me
200: { "id": 1, "email": "a@b.com", "nickname": "이름", "createdAt": "...", "keywords": ["백엔드", "안드로이드"] }
```

### 관심 분야 설정
```
PUT /api/auth/me/keywords
Body: { "keywords": ["백엔드", "안드로이드"] }
200: { "id": 1, ..., "keywords": ["백엔드", "안드로이드"] }
```

### 헬스체크 (배포 감시용, 인증 불필요)
```
GET /api/health
200: { "status": "ok" }
```

---

## 2. 내 공고 (JobPosting)

### 목록 (상태/키워드 필터)
```
GET /api/jobs?status=APPLIED&keyword=백엔드
200: [
  {
    "id": 1,
    "companyName": "카카오",
    "position": "백엔드 개발자",
    "link": "https://...",
    "deadline": "2026-08-31",
    "status": "WISH",
    "memo": "...",
    "createdAt": "...",
    "source": "잡코리아"   // 스크랩 시에만
  }
]
```
- status: `WISH | APPLIED | INTERVIEW | OFFER | REJECTED` (전체: 생략)
- keyword: 회사명/포지션 부분 일치

### 상세
```
GET /api/jobs/{id}
200: 공고 1건 (위 형식)
404: 내 공고가 아님 (존재 숨김)
```

### 추가
```
POST /api/jobs
Body: { "companyName": "카카오", "position": "백엔드 개발자", "link": "https://...", "deadline": "2026-08-31", "status": "WISH", "memo": "" }
201: 생성된 공고
```

### 수정 (상태 변경 포함)
```
PATCH /api/jobs/{id}
Body: 부분 필드 가능 — { "status": "APPLIED" } 만 보내도 됨
200: 수정된 공고
```

### 삭제
```
DELETE /api/jobs/{id}
204: 성공
```

### 통계 (대시보드)
```
GET /api/jobs/stats
200: { "WISH": 3, "APPLIED": 2, "INTERVIEW": 1, "OFFER": 1, "REJECTED": 0 }
```

---

## 3. 수집 공고 (CollectedJob)

### 목록 (필터/검색)
```
GET /api/jobs/collected?source=잡코리아&keyword=네트워크&mine=true&searchField=title
200: [
  {
    "id": 145,
    "company": "㈜지엔텔",
    "title": "이동통신 네트워크 엔지니어",
    "url": "https://www.jobkorea.co.kr/...",
    "source": "잡코리아",
    "jobKey": "잡코리아:49615709",
    "createdAt": "...",
    "scrapedByMe": false,        // 내가 스크랩했는지
    "region": "대전",            // 지역 (nullable)
    "experience": "경력무관",     // 경력 (nullable)
    "industry": "통신·방송"       // 업종 (nullable)
  }
]
```
- source: `잡코리아 | 원티드` (전체: 생략)
- keyword: 회사명/제목 부분 일치
- mine: `true`면 내 관심 공고(내 키워드 매칭)만
- searchField: `company | title | all` (기본 all) — 검색 대상 필드

### 스크랩 (→ 내 공고로)
```
POST /api/jobs/collected/{id}/scrap
201: 내 공고(JobPosting) 형식 반환
409: 이미 스크랩한 공고
```

### 링크 자동 채우기 (OpenGraph)
```
POST /api/jobs/preview
Body: { "url": "https://www.jobkorea.co.kr/Recruit/GI_Read/123" }
200: { "company": "㈜지엔텔", "position": "이동통신 네트워크 엔지니어", "memo": "" }
```

### 키워드 즉시 검색·수집
```
POST /api/jobs/collect/search
Body: { "keyword": "네트워크" }
200: { "keyword": "네트워크", "collected": 27, "skipped": 68 }
```
- 원티드(30건) + 잡코리아(3페이지)에서 수집 → DB 저장
- collected: 새로 저장된 수 / skipped: 이미 있거나 필터 제외된 수
- 제목 또는 회사명에 키워드 포함 공고만 저장

### 전체 사용자 키워드 합집합 (크론/앱용, 인증 불필요)
```
GET /api/jobs/collect/keywords
200: ["백엔드", "안드로이드", "마케팅"]
```

---

## 4. 지원 상태 코드

| 코드 | 라벨 |
|---|---|
| WISH | 지원 예정 |
| APPLIED | 지원함 |
| INTERVIEW | 면접 |
| OFFER | 합격 |
| REJECTED | 불합격 |

요청 시 대소문자 무시 (`applied` → APPLIED)

---

## 5. 참고 (앱 개발 시)

- **무료 티어 스핀다운**: 15분 무접속 시 서버가 잠듦 → 첫 요청 50초+ 지연될 수 있음. 앱은 로딩 UI + 타임아웃 여유(90초+) 필요
- **데모 계정**: `final@test.com` / `password123`
- **daily-collect**: 매일 17시 GitHub Actions가 사용자 키워드로 공고 자동 수집 (앱에서 별도 처리 불필요)
