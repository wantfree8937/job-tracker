package com.example.jobtracker.service.ai;

import com.example.jobtracker.dto.ai.InterviewQuestionRequest;
import com.example.jobtracker.dto.ai.InterviewQuestionResponse;
import com.example.jobtracker.entity.user.ResumeFile;
import com.example.jobtracker.entity.user.User;
import com.example.jobtracker.exception.AiRequestFailedException;
import com.example.jobtracker.exception.InvalidCredentialsException;
import com.example.jobtracker.repository.user.ResumeFileRepository;
import com.example.jobtracker.repository.user.UserRepository;
import com.sun.net.httpserver.HttpServer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiServiceTest {

    private static final InterviewQuestionRequest EMPTY_REQUEST =
            new InterviewQuestionRequest(null, null, null, null, null, null, null, null, null);

    private HttpServer httpServer;

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    // 지정한 JSON 본문을 200으로 응답하는 로컬 HTTP 서버를 띄우고 baseUrl을 반환한다 (opencode-go API 목업)
    private String startMockApiServer(String responseJson) throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        httpServer.createContext("/chat/completions", exchange -> {
            byte[] body = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        httpServer.start();
        return "http://localhost:" + httpServer.getAddress().getPort();
    }

    private User userWithProfile(String email, String profileText) {
        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setProfileText(profileText);
        return user;
    }

    @Test
    void API_키가_없으면_사용자_조회_없이_예외를_던진다() {
        UserRepository userRepository = mock(UserRepository.class);
        ResumeFileRepository resumeFileRepository = mock(ResumeFileRepository.class);
        AiService aiService = new AiService("", "http://localhost", userRepository, resumeFileRepository);

        assertThatThrownBy(() -> aiService.generateQuestions("a@a.com", EMPTY_REQUEST))
                .isInstanceOf(AiRequestFailedException.class);
    }

    @Test
    void 존재하지_않는_이메일이면_예외를_던진다() {
        UserRepository userRepository = mock(UserRepository.class);
        ResumeFileRepository resumeFileRepository = mock(ResumeFileRepository.class);
        when(userRepository.findByEmail("nobody@a.com")).thenReturn(Optional.empty());
        AiService aiService = new AiService("test-key", "http://localhost", userRepository, resumeFileRepository);

        assertThatThrownBy(() -> aiService.generateQuestions("nobody@a.com", EMPTY_REQUEST))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void 정상_호출시_질문_목록을_반환한다() throws Exception {
        String apiResponse = """
                {"choices":[{"message":{"content":"{\\"usedResume\\": true, \\"questions\\": [\\"q1\\", \\"q2\\"]}"}}]}
                """;
        String baseUrl = startMockApiServer(apiResponse);

        UserRepository userRepository = mock(UserRepository.class);
        ResumeFileRepository resumeFileRepository = mock(ResumeFileRepository.class);
        when(userRepository.findByEmail("a@a.com"))
                .thenReturn(Optional.of(userWithProfile("a@a.com", "TRISENSE(Kotlin/Compose)")));
        AiService aiService = new AiService("test-key", baseUrl, userRepository, resumeFileRepository);

        InterviewQuestionResponse response = aiService.generateQuestions("a@a.com", EMPTY_REQUEST);

        assertThat(response.questions()).containsExactly("q1", "q2");
        assertThat(response.usedResume()).isTrue();
    }

    @Test
    void 이력서_텍스트가_없으면_저장된_파일에서_텍스트를_추출해_사용한다() throws Exception {
        String apiResponse = """
                {"choices":[{"message":{"content":"{\\"usedResume\\": true, \\"questions\\": [\\"q1\\"]}"}}]}
                """;
        String baseUrl = startMockApiServer(apiResponse);

        UserRepository userRepository = mock(UserRepository.class);
        ResumeFileRepository resumeFileRepository = mock(ResumeFileRepository.class);
        when(userRepository.findByEmail("a@a.com"))
                .thenReturn(Optional.of(userWithProfile("a@a.com", null)));
        ResumeFile file = new ResumeFile();
        file.setFileName("이력서.pdf");
        file.setFileType("application/pdf");
        file.setContent(pdfBytes("Backend Developer"));
        when(resumeFileRepository.findByUserIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(file));
        AiService aiService = new AiService("test-key", baseUrl, userRepository, resumeFileRepository);

        InterviewQuestionResponse response = aiService.generateQuestions("a@a.com", EMPTY_REQUEST);

        assertThat(response.questions()).containsExactly("q1");
    }

    @Test
    void API_호출이_실패하면_예외를_던진다() {
        UserRepository userRepository = mock(UserRepository.class);
        ResumeFileRepository resumeFileRepository = mock(ResumeFileRepository.class);
        when(userRepository.findByEmail("a@a.com"))
                .thenReturn(Optional.of(userWithProfile("a@a.com", null)));
        when(resumeFileRepository.findByUserIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        // 아무도 듣고 있지 않은 포트로 연결 시도 -> 네트워크 예외 유도
        AiService aiService = new AiService("test-key", "http://localhost:1", userRepository, resumeFileRepository);

        assertThatThrownBy(() -> aiService.generateQuestions("a@a.com", EMPTY_REQUEST))
                .isInstanceOf(AiRequestFailedException.class);
    }

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
    void topic이_TECHNICAL이고_이력서가_있으면_이력서_기술_스택_기반_지시문을_포함한다() {
        InterviewQuestionRequest request = new InterviewQuestionRequest(
                null, null, null, null, null, null, "TECHNICAL", null, null);

        String prompt = AiService.buildSystemPrompt(request, "Kotlin/Compose");

        assertThat(prompt).contains("이력서에 언급된 기술 스택 위주로");
    }

    @Test
    void topic이_MIXED이고_이력서가_있으면_이력서_기반_프로젝트_질문_지시문을_포함한다() {
        InterviewQuestionRequest request = new InterviewQuestionRequest(
                null, null, null, null, null, null, "MIXED", null, null);

        String prompt = AiService.buildSystemPrompt(request, "Kotlin/Compose");

        assertThat(prompt).contains("이력서 기반 프로젝트 경험 질문");
    }

    @Test
    void topic이_MIXED이고_이력서가_없으면_기본_혼합_지시문을_포함한다() {
        InterviewQuestionRequest request = new InterviewQuestionRequest(
                null, null, null, null, null, null, "MIXED", null, null);

        String prompt = AiService.buildSystemPrompt(request, null);

        assertThat(prompt).contains("기술 역량 질문과 프로젝트 경험 질문을 섞어서");
    }

    @Test
    void difficulty가_EASY이면_쉬운_난이도_지시문을_포함한다() {
        InterviewQuestionRequest request = new InterviewQuestionRequest(
                null, null, null, null, null, null, null, "EASY", null);

        String prompt = AiService.buildSystemPrompt(request, null);

        assertThat(prompt).contains("난이도는 쉬움");
    }

    @Test
    void difficulty가_NORMAL이면_보통_난이도_지시문을_포함한다() {
        InterviewQuestionRequest request = new InterviewQuestionRequest(
                null, null, null, null, null, null, null, "NORMAL", null);

        String prompt = AiService.buildSystemPrompt(request, null);

        assertThat(prompt).contains("난이도는 보통");
    }

    @Test
    void profileText가_있으면_무의미한_이력서_무시_지시문을_포함한다() {
        InterviewQuestionRequest request = new InterviewQuestionRequest(
                null, null, null, null, null, null, null, null, null);

        String prompt = AiService.buildSystemPrompt(request, "TRISENSE(반응 훈련 게임, Kotlin/Compose)");

        assertThat(prompt).contains("무의미하다고 판단되면");
    }

    @Test
    void 채용공고_정보가_있으면_참고_지시문을_포함한다() {
        InterviewQuestionRequest request = new InterviewQuestionRequest(
                "토스", "백엔드 개발자", null, null, null, null, null, null, null);

        String prompt = AiService.buildSystemPrompt(request, "TRISENSE(반응 훈련 게임, Kotlin/Compose)");

        assertThat(prompt).contains("아래 채용공고 정보를 참고해서");
    }

    @Test
    void 질문_5개를_정상_파싱한다() {
        String responseBody = """
                {"choices":[{"message":{"content":"{\\"usedResume\\": false, \\"questions\\": [\\"q1\\", \\"q2\\", \\"q3\\", \\"q4\\", \\"q5\\"]}"}}]}
                """;

        InterviewQuestionResponse response = AiService.parseQuestions(responseBody, false);

        assertThat(response.questions()).hasSize(5).containsExactly("q1", "q2", "q3", "q4", "q5");
    }

    @Test
    void questions가_빈_배열이면_예외를_던진다() {
        String responseBody = """
                {"choices":[{"message":{"content":"{\\"usedResume\\": true, \\"questions\\": []}"}}]}
                """;

        assertThatThrownBy(() -> AiService.parseQuestions(responseBody, false))
                .isInstanceOf(AiRequestFailedException.class);
    }

    @Test
    void 마크다운_코드블록으로_감싸진_응답은_파싱에_실패해_예외를_던진다() {
        String responseBody = """
                {"choices":[{"message":{"content":"```json\\n{\\"usedResume\\": true, \\"questions\\": [\\"질문1\\"]}\\n```"}}]}
                """;

        assertThatThrownBy(() -> AiService.parseQuestions(responseBody, false))
                .isInstanceOf(AiRequestFailedException.class);
    }

    @Test
    void 원티드_상세_JSON에서_자격요건_주요업무_우대사항을_추출한다() {
        String json = """
                {"job":{"detail":{"requirements":"Java 3년 이상","main_tasks":"백엔드 API 개발","preferred_points":"Spring 경험"}}}
                """;

        String result = AiService.parseWantedJobDetail(json);

        assertThat(result).contains("자격요건: Java 3년 이상", "주요업무: 백엔드 API 개발", "우대사항: Spring 경험");
    }

    @Test
    void 원티드_상세_필드가_모두_없으면_null을_반환한다() {
        String json = """
                {"job":{"detail":{}}}
                """;

        assertThat(AiService.parseWantedJobDetail(json)).isNull();
    }

    @Test
    void 원티드_상세_JSON이_깨지면_null을_반환한다() {
        assertThat(AiService.parseWantedJobDetail("이건 JSON이 아님")).isNull();
    }

    @Test
    void 잡코리아_HTML에서_상세_내용_텍스트를_추출한다() {
        String html = "<html><body><div id=\"detail-content\">지원자격: 신입/경력 무관, 주요업무: 백엔드 개발</div></body></html>";

        String result = AiService.parseJobKoreaJobDetail(html);

        assertThat(result).contains("지원자격", "주요업무");
    }

    @Test
    void 잡코리아_HTML에_상세_영역이_없으면_null을_반환한다() {
        String html = "<html><body><div id=\"other\">배너 이미지 공고</div></body></html>";

        assertThat(AiService.parseJobKoreaJobDetail(html)).isNull();
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
