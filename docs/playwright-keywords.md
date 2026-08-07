# 작업: 관심 분야 UI 화면 검증 + 스크린샷

환경: 프론트 http://localhost:5173 (Vite 실행 중), 백엔드 http://localhost:8080 (Spring 실행 중, 수집 공고 67건)

## 수행할 것
1. Playwright(headless)로 http://localhost:5173 접속
2. userB@test.com / password123 로그인 (관심 분야 [백엔드, 서버] 설정된 사용자)
3. 헤더의 **"관심 분야"** 버튼 클릭 → 모달 확인 → 스크린샷: C:\Users\pey21\projects\job-tracker\docs\screenshot-keywords.png
   - 모달에 후보 칩들(안드로이드/iOS/백엔드 등)과 선택 상태, 저장 버튼 보이는지 텍스트 요약
4. 모달 닫기 → **"전체 공고" 탭 클릭** → 상단의 **"전체 공고 | 내 관심 공고"** 토글 확인
5. **"내 관심 공고"** 클릭 → 공고 목록이 백엔드/서버 관련만 나오는지 확인 → 스크린샷: docs/screenshot-mine.png
   - 보이는 공고 수/회사명/포지션 텍스트 요약 (백엔드 공고가 보여야 정상)
6. 결과 보고 (각 단계 성공 여부 + 화면 텍스트)

## 주의
- 뷰포트 1280x800
- 서버/코드 수정 금지 (검증 전용)
