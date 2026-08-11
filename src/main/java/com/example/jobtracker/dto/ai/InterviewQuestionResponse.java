package com.example.jobtracker.dto.ai;

import java.util.List;

/** AI가 생성한 예상 면접 질문 목록 (usedResume: 질문 생성에 이력서가 실제 반영됐는지 여부) */
public record InterviewQuestionResponse(List<String> questions, boolean usedResume) {
}
