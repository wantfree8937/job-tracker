# 작업: 전체 공고 탭 화면 캡처 + 확인

환경: 프론트 http://localhost:5173 (Vite 실행 중), 백엔드 http://localhost:8080 (Spring 실행 중, 수집 공고 11건 DB에 있음)

## 수행할 것
1. Playwright(headless)로 http://localhost:5173 접속
2. collect@test.com / password123 로그인
3. **"전체 공고" 탭 클릭** → 화면 캡처: C:\Users\pey21\projects\job-tracker\docs\screenshot-collected.png
   - 보이는 것 텍스트 요약: "공고 불러오기" 버튼, source 필터, 수집 공고 카드들(회사/포지션/source 뱃지), 스크랩 버튼
4. 첫 공고 카드의 "스크랩" 버튼 클릭 → 동작 확인 (내 공고로 가져왔어요 메시지 또는 버튼 상태 변화) — 화면 상태 텍스트 요약
5. 결과 보고

## 주의
- 뷰포트 1280x800
- 서버/코드 수정 금지 (캡처·확인 전용)
