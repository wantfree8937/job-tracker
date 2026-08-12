package com.example.jobtracker.service.ai;

import com.example.jobtracker.dto.ai.InterviewQuestionRequest;
import com.example.jobtracker.dto.ai.InterviewQuestionResponse;
import com.example.jobtracker.entity.user.ResumeFile;
import com.example.jobtracker.exception.AiRequestFailedException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiServiceTest {

    @Test
    void 딥시크_응답에서_질문과_usedResume을_파싱한다() {
        String responseBody = """
                {"choices":[{"message":{"content":"{\\"usedResume\\": true, \\"questions\\": [\\"질문1\\", \\"질문2\\"]}"}}]}
                """;

        InterviewQuestionResponse response = AiService.parseQuestions(responseBody, false);

        assertThat(response.questions()).containsExactly("질문1", "질문2");
        assertThat(response.usedResume()).isTrue();
    }

    @Test
    void usedResume이_없으면_hasProfile을_기본값으로_사용한다() {
        String responseBody = """
                {"choices":[{"message":{"content":"{\\"questions\\": [\\"질문1\\"]}"}}]}
                """;

        assertThat(AiService.parseQuestions(responseBody, true).usedResume()).isTrue();
        assertThat(AiService.parseQuestions(responseBody, false).usedResume()).isFalse();
    }

    @Test
    void content가_JSON_객체가_아니면_예외를_던진다() {
        String responseBody = """
                {"choices":[{"message":{"content":"질문 목록입니다"}}]}
                """;

        assertThatThrownBy(() -> AiService.parseQuestions(responseBody, false))
                .isInstanceOf(AiRequestFailedException.class);
    }

    @Test
    void 응답_형식이_깨지면_예외를_던진다() {
        assertThatThrownBy(() -> AiService.parseQuestions("이건 JSON이 아님", false))
                .isInstanceOf(AiRequestFailedException.class);
    }

    @Test
    void 값이_있는_필드만_사용자_메시지에_포함한다() {
        InterviewQuestionRequest request = new InterviewQuestionRequest(
                "토스", "안드로이드 개발자", null, "신입", null, null, null, null, null);

        String message = AiService.buildUserMessage(request);

        assertThat(message).contains("회사명: 토스", "포지션: 안드로이드 개발자", "경력: 신입");
        assertThat(message).doesNotContain("지역:", "업종:", "메모:");
    }

    @Test
    void topic이_TECHNICAL이면_기술_면접_지시문을_포함한다() {
        InterviewQuestionRequest request = new InterviewQuestionRequest(
                null, null, null, null, null, null, "TECHNICAL", "HARD", null);

        String prompt = AiService.buildSystemPrompt(request, null);

        assertThat(prompt).contains("기술 면접 질문 5개").contains("도전적").contains("일반적인 면접");
    }

    @Test
    void difficulty가_ENTRY이면_신입_지시문을_포함한다() {
        InterviewQuestionRequest request = new InterviewQuestionRequest(
                null, null, null, null, null, null, null, "ENTRY", null);

        String prompt = AiService.buildSystemPrompt(request, null);

        assertThat(prompt).contains("난이도는 신입").contains("압박 질문").contains("성장 가능성");
    }

    @Test
    void topic과_difficulty가_없으면_MIXED와_NORMAL로_처리한다() {
        InterviewQuestionRequest request = new InterviewQuestionRequest(
                null, null, null, null, null, null, null, null, null);

        String prompt = AiService.buildSystemPrompt(request, null);

        assertThat(prompt).contains("섞어서").contains("보통").contains("일반적인 면접");
    }

    @Test
    void profileText가_있으면_이력서_기반_지시문을_포함한다() {
        InterviewQuestionRequest request = new InterviewQuestionRequest(
                null, null, null, null, null, null, "MOTIVE", null, null);

        String prompt = AiService.buildSystemPrompt(request, "TRISENSE(반응 훈련 게임, Kotlin/Compose)");

        assertThat(prompt)
                .contains("지원자의 이력서/포트폴리오: TRISENSE(반응 훈련 게임, Kotlin/Compose)")
                .contains("이 내용을 참고해서");
    }

    @Test
    void profileText가_없으면_일반적인_개발자_면접_질문을_생성한다() {
        InterviewQuestionRequest request = new InterviewQuestionRequest(
                null, null, null, null, null, null, "MOTIVE", null, null);

        String prompt = AiService.buildSystemPrompt(request, "");

        assertThat(prompt).doesNotContain("이력서/포트폴리오").contains("기술 스택·구현 세부 질문은 지양");
    }

    @Test
    void profileText와_채용공고가_모두_있으면_무관시_이력서_무시_지시문을_포함한다() {
        InterviewQuestionRequest request = new InterviewQuestionRequest(
                "토스", null, null, null, null, null, "MOTIVE", null, null);

        String prompt = AiService.buildSystemPrompt(request, "TRISENSE(반응 훈련 게임, Kotlin/Compose)");

        assertThat(prompt).contains("전혀 무관하다고 판단되면").contains("이력서 내용은 무시");
    }

    @Test
    void profileText가_없고_채용공고가_있으면_회사_포지션_중심_질문을_생성한다() {
        InterviewQuestionRequest request = new InterviewQuestionRequest(
                "토스", "백엔드 개발자", null, null, null, null, null, null, null);

        String prompt = AiService.buildSystemPrompt(request, null);

        assertThat(prompt)
                .doesNotContain("이력서/포트폴리오")
                .contains("지원 동기", "회사/업종 이해도", "포지션 요구사항");
    }

    @Test
    void 이력서_파일_여러_개의_텍스트를_합쳐서_반환한다() throws Exception {
        ResumeFile file1 = resumeFileOf("이력서1.pdf", "application/pdf", pdfBytes("Backend Developer"));
        ResumeFile file2 = resumeFileOf("이력서2.pdf", "application/pdf", pdfBytes("Android Developer"));

        String combined = AiService.buildProfileTextFromFiles(List.of(file1, file2));

        assertThat(combined).contains("Backend Developer").contains("Android Developer");
    }

    @Test
    void 텍스트_추출에_실패한_파일은_건너뛰고_나머지를_반환한다() throws Exception {
        ResumeFile broken = resumeFileOf("resume.txt", "text/plain", "hello".getBytes());
        ResumeFile ok = resumeFileOf("이력서.pdf", "application/pdf", pdfBytes("Backend Developer"));

        String combined = AiService.buildProfileTextFromFiles(List.of(broken, ok));

        assertThat(combined).contains("Backend Developer").doesNotContain("hello");
    }

    private ResumeFile resumeFileOf(String fileName, String fileType, byte[] content) {
        ResumeFile file = new ResumeFile();
        file.setFileName(fileName);
        file.setFileType(fileType);
        file.setContent(content);
        return file;
    }

    private byte[] pdfBytes(String text) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA, 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(text);
                stream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
