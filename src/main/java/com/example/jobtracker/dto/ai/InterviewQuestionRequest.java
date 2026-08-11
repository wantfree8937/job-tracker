package com.example.jobtracker.dto.ai;

/**
 * AI 면접 질문 생성 요청 DTO. 값이 없는 필드는 null 가능
 * topic: "TECHNICAL" | "PORTFOLIO" | "MIXED" (null이면 MIXED)
 * difficulty: "EASY" | "NORMAL" | "HARD" (null이면 NORMAL)
 */
public record InterviewQuestionRequest(
        String companyName,
        String position,
        String region,
        String experience,
        String industry,
        String memo,
        String topic,
        String difficulty
) {
}
