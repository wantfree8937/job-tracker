# 작업: 개선된 UI 스크린샷 캡처 (기존 파일 덮어쓰기)

환경: 프론트 http://localhost:5173 (Vite 실행 중), 백엔드 http://localhost:8080 (Spring 실행 중)

## 수행할 것
1. Playwright(headless)로 http://localhost:5173 접속
2. **로그인 화면** 캡처 → C:\Users\pey21\projects\job-tracker\docs\screenshot-login.png (기존 파일 덮어쓰기)
   - 화면 요소 텍스트 요약 (제목, 태그라인, 버튼 등 — 그라데이션 배경 보이는지)
3. final@test.com / password123 로그인
4. **목록 화면** 캡처 → docs/screenshot-list.png (덮어쓰기)
   - 통계 카드(이모지 포함), 공고 카드, 필터 칩 등 보이는 것 텍스트 요약
5. "공고 추가" 버튼 클릭 → 모달 완전히 표시될 때까지 잠시 대기(500ms) → **모달 화면** 캡처 → docs/screenshot-modal.png (덮어쓰기)
6. 결과 보고: 각 단계 성공 여부 + 화면에 보이는 주요 텍스트 (한국어 그대로)

## 주의
- 스크린샷은 1280x800 뷰포트로
- 모달 캡처 시 스크롤바가 보이면 모달 안의 취소/저장 버튼이 보이도록 스크롤하거나, 버튼이 보이는 상태로 캡처
- 서버/코드 수정 금지 (캡처 전용)
