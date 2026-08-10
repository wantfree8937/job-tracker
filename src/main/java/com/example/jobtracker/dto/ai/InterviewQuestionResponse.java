package com.example.jobtracker.dto.ai;

import java.util.List;

/** AI가 생성한 예상 면접 질문 목록 */
public record InterviewQuestionResponse(List<String> questions) {
}
