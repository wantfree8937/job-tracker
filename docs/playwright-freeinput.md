# 작업: 관심 분야 자유 입력 검증 (단순 버전)

환경: 프론트 http://localhost:5173, 백엔드 http://localhost:8080

## 순서 (스크린샷 2장만)
1. http://localhost:5173 접속 → userA@test.com / password123 로그인
2. "관심 분야" 버튼 클릭 → 모달 확인 → 직접 입력창에 "AI 개발" 입력 → [추가] 클릭
3. **스크린샷 1**: C:\Users\pey21\projects\job-tracker\docs\screenshot-keywords-input.png (태그로 추가된 모습)
4. [저장하고 공고 불러오기] 클릭 → 결과 메시지 텍스트 읽어서 보고 (예: "N건을 가져왔어요")
5. 모달 닫고 → "전체 공고" 탭 → "내 관심 공고" 토글 클릭
6. **스크린샷 2**: C:\Users\pey21\projects\job-tracker\docs\screenshot-freeinput.png
7. 보이는 공고 몇 개 회사명/제목 보고

## 주의
- 뷰포트 1280x800, 서버/코드 수정 금지
- 가능한 빨리 진행 (턴 절약): 각 단계 검증은 최소한으로
