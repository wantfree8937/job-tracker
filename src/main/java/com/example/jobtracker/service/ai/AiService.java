package com.example.jobtracker.service.ai;

import com.example.jobtracker.dto.ai.InterviewQuestionRequest;
import com.example.jobtracker.dto.ai.InterviewQuestionResponse;
import com.example.jobtracker.exception.AiRequestFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** opencode-go API로 채용공고 정보 기반 예상 면접 질문을 생성한다 (API 키 노출 방지용 백엔드 프록시) */
@Slf4j
@Service
public class AiService {

    private static final String INTRO = """
            너는 한국 IT 기업의 면접관이다. 지원자는 신입 안드로이드 개발자로,
            Kotlin/Compose/Clean Architecture를 공부했고 포트폴리오는
            TRISENSE(반응 훈련 게임, 1인 출시, MVI+Room+Hilt), 딱지금(최저가 추적, 4인 팀, FCM+Firestore+Koin),
            머니로그(ML Kit OCR 가계부)가 있다.
            """;

    private static final String TECHNICAL_INSTRUCTION =
            "지원자의 기술 역량을 검증하는 기술 면접 질문 5개를 생성해라 " +
            "(Kotlin/Android/Compose/Clean Architecture/데이터베이스/네트워크 등 현직 개발자 수준).";
    private static final String PORTFOLIO_INSTRUCTION =
            "지원자의 포트폴리오(TRISENSE, 딱지금, 머니로그)를 기반으로 프로젝트 경험을 검증하는 질문 5개를 생성해라 " +
            "(구현 결정/트러블슈팅/협업 경험 위주).";
    private static final String MIXED_INSTRUCTION =
            "기술 역량 질문과 포트폴리오 프로젝트 경험 질문을 섞어서 5개 생성해라.";

    private static final String EASY_INSTRUCTION = "난이도는 쉬움: 기초 개념이나 개인 경험을 묻는 쉬운 질문으로 구성해라.";
    private static final String NORMAL_INSTRUCTION = "난이도는 보통: 일반적인 수준의 질문으로 구성해라.";
    private static final String HARD_INSTRUCTION = "난이도는 어려움: 심화 개념, 트러블슈팅, 아키텍처 설계를 묻는 어려운 질문으로 구성해라.";

    private static final String OUTPUT_FORMAT_INSTRUCTION =
            "질문은 반드시 JSON 문자열 배열로만 응답해라 (마크다운/설명 금지).";

    private final String apiKey;
    private final RestClient restClient;

    public AiService(@Value("${opencode-go.api-key}") String apiKey,
                      @Value("${opencode-go.base-url}") String baseUrl) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public InterviewQuestionResponse generateQuestions(InterviewQuestionRequest request) {
        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", "deepseek-v4-flash",
                            "messages", List.of(
                                    Map.of("role", "system", "content", buildSystemPrompt(request)),
                                    Map.of("role", "user", "content", buildUserMessage(request))
                            ),
                            "max_tokens", 4000
                    ))
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.warn("opencode-go API 호출 실패: {}", e.getMessage());
            throw new AiRequestFailedException();
        }

        return new InterviewQuestionResponse(parseQuestions(responseBody));
    }

    // topic/difficulty와 채용공고 정보 유무에 따라 시스템 프롬프트를 동적으로 구성한다
    static String buildSystemPrompt(InterviewQuestionRequest request) {
        StringBuilder sb = new StringBuilder(INTRO);

        sb.append(switch (request.topic() == null ? "MIXED" : request.topic()) {
            case "TECHNICAL" -> TECHNICAL_INSTRUCTION;
            case "PORTFOLIO" -> PORTFOLIO_INSTRUCTION;
            default -> MIXED_INSTRUCTION;
        }).append("\n");

        sb.append(switch (request.difficulty() == null ? "NORMAL" : request.difficulty()) {
            case "EASY" -> EASY_INSTRUCTION;
            case "HARD" -> HARD_INSTRUCTION;
            default -> NORMAL_INSTRUCTION;
        }).append("\n");

        sb.append(hasJobInfo(request)
                ? "아래 채용공고 정보를 참고해서 질문을 생성해라.\n"
                : "특정 채용공고 없이 일반적인 면접이라고 가정해라.\n");

        sb.append(OUTPUT_FORMAT_INSTRUCTION);
        return sb.toString();
    }

    private static boolean hasJobInfo(InterviewQuestionRequest request) {
        return Stream.of(request.companyName(), request.position(), request.region(),
                        request.experience(), request.industry(), request.memo())
                .anyMatch(v -> v != null && !v.isBlank());
    }

    // 요청에 담긴 값(있는 필드만)으로 사용자 메시지를 구성한다
    static String buildUserMessage(InterviewQuestionRequest request) {
        StringBuilder sb = new StringBuilder();
        appendField(sb, "회사명", request.companyName());
        appendField(sb, "포지션", request.position());
        appendField(sb, "지역", request.region());
        appendField(sb, "경력", request.experience());
        appendField(sb, "업종", request.industry());
        appendField(sb, "메모", request.memo());
        return sb.toString();
    }

    private static void appendField(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(": ").append(value).append("\n");
        }
    }

    // opencode-go 응답(choices[0].message.content)에서 질문 JSON 배열을 추출한다 (실제 호출 없이 파싱만 테스트하기 위해 분리)
    static List<String> parseQuestions(String responseBody) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(responseBody);
            String content = root.path("choices").path(0).path("message").path("content").asText();

            List<String> questions = new ArrayList<>();
            mapper.readTree(content).forEach(node -> questions.add(node.asText()));
            if (questions.isEmpty()) {
                throw new AiRequestFailedException();
            }
            return questions;
        } catch (AiRequestFailedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("딥시크 응답 파싱 실패: {}", e.getMessage());
            throw new AiRequestFailedException();
        }
    }
}
