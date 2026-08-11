package com.example.jobtracker.dto.ai;

/**
 * AI 면접 질문 생성 요청 DTO. 값이 없는 필드는 null 가능
 * topic: "TECHNICAL" | "MOTIVE"(지원동기·인성) | "MIXED" (null이면 MIXED)
 * difficulty: "EASY" | "NORMAL" | "HARD" (null이면 NORMAL)
 * url: 채용공고 URL (선택, 있으면 실시간 크롤링해 자격요건/주요업무를 질문 생성에 반영)
 */
public record InterviewQuestionRequest(
        String companyName,
        String position,
        String region,
        String experience,
        String industry,
        String memo,
        String topic,
        String difficulty,
        String url
) {
}
