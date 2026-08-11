package com.example.jobtracker.service.ai;

import com.example.jobtracker.dto.ai.InterviewQuestionRequest;
import com.example.jobtracker.exception.AiRequestFailedException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiServiceTest {

    @Test
    void 딥시크_응답에서_질문_배열을_파싱한다() {
        String responseBody = """
                {"choices":[{"message":{"content":"[\\"질문1\\",\\"질문2\\"]"}}]}
                """;

        List<String> questions = AiService.parseQuestions(responseBody);

        assertThat(questions).containsExactly("질문1", "질문2");
    }

    @Test
    void content가_JSON_배열이_아니면_예외를_던진다() {
        String responseBody = """
                {"choices":[{"message":{"content":"질문 목록입니다"}}]}
                """;

        assertThatThrownBy(() -> AiService.parseQuestions(responseBody))
                .isInstanceOf(AiRequestFailedException.class);
    }

    @Test
    void 응답_형식이_깨지면_예외를_던진다() {
        assertThatThrownBy(() -> AiService.parseQuestions("이건 JSON이 아님"))
                .isInstanceOf(AiRequestFailedException.class);
    }

    @Test
    void 값이_있는_필드만_사용자_메시지에_포함한다() {
        InterviewQuestionRequest request = new InterviewQuestionRequest(
                "토스", "안드로이드 개발자", null, "신입", null, null, null, null);

        String message = AiService.buildUserMessage(request);

        assertThat(message).contains("회사명: 토스", "포지션: 안드로이드 개발자", "경력: 신입");
        assertThat(message).doesNotContain("지역:", "업종:", "메모:");
    }

    @Test
    void topic이_TECHNICAL이면_기술_면접_지시문을_포함한다() {
        InterviewQuestionRequest request = new InterviewQuestionRequest(
                null, null, null, null, null, null, "TECHNICAL", "HARD");

        String prompt = AiService.buildSystemPrompt(request, null);

        assertThat(prompt).contains("기술 면접 질문 5개").contains("도전적").contains("일반적인 면접");
    }

    @Test
    void difficulty가_ENTRY이면_신입_지시문을_포함한다() {
        InterviewQuestionRequest request = new InterviewQuestionRequest(
                null, null, null, null, null, null, null, "ENTRY");

        String prompt = AiService.buildSystemPrompt(request, null);

        assertThat(prompt).contains("난이도는 신입").contains("압박 질문").contains("성장 가능성");
    }

    @Test
    void topic과_difficulty가_없으면_MIXED와_NORMAL로_처리한다() {
        InterviewQuestionRequest request = new InterviewQuestionRequest(
                "토스", null, null, null, null, null, null, null);

        String prompt = AiService.buildSystemPrompt(request, null);

        assertThat(prompt).contains("섞어서").contains("보통").contains("채용공고 정보를 참고");
    }

    @Test
    void profileText가_있으면_이력서_기반_지시문을_포함한다() {
        InterviewQuestionRequest request = new InterviewQuestionRequest(
                null, null, null, null, null, null, "PORTFOLIO", null);

        String prompt = AiService.buildSystemPrompt(request, "TRISENSE(반응 훈련 게임, Kotlin/Compose)");

        assertThat(prompt)
                .contains("지원자의 이력서/포트폴리오: TRISENSE(반응 훈련 게임, Kotlin/Compose)")
                .contains("이 내용을 기반으로");
    }

    @Test
    void profileText가_없으면_일반적인_개발자_면접_질문을_생성한다() {
        InterviewQuestionRequest request = new InterviewQuestionRequest(
                null, null, null, null, null, null, "PORTFOLIO", null);

        String prompt = AiService.buildSystemPrompt(request, "");

        assertThat(prompt).doesNotContain("이력서/포트폴리오").contains("특정 프로젝트를 가정하지 말고");
    }
}
