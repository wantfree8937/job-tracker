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

/** 딥시크 API로 채용공고 정보 기반 예상 면접 질문을 생성한다 (API 키 노출 방지용 백엔드 프록시) */
@Slf4j
@Service
public class AiService {

    private static final String SYSTEM_PROMPT = """
            너는 한국 IT 기업의 면접관이다. 지원자는 신입 안드로이드 개발자로,
            Kotlin/Compose/Clean Architecture를 공부했고 포트폴리오는
            TRISENSE(반응 훈련 게임, 1인 출시, MVI+Room+Hilt), 딱지금(최저가 추적, 4인 팀, FCM+Firestore+Koin),
            머니로그(ML Kit OCR 가계부)가 있다.
            아래 채용공고 정보를 보고 예상 면접 질문 5개를 한국어로 생성해라.
            질문은 반드시 JSON 문자열 배열로만 응답해라 (마크다운/설명 금지).
            """;

    private final String apiKey;
    private final RestClient restClient;

    public AiService(@Value("${deepseek.api-key}") String apiKey,
                      @Value("${deepseek.base-url}") String baseUrl) {
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
                            "model", "deepseek-chat",
                            "messages", List.of(
                                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                                    Map.of("role", "user", "content", buildUserMessage(request))
                            )
                    ))
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.warn("딥시크 API 호출 실패: {}", e.getMessage());
            throw new AiRequestFailedException();
        }

        return new InterviewQuestionResponse(parseQuestions(responseBody));
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

    // 딥시크 응답(choices[0].message.content)에서 질문 JSON 배열을 추출한다 (실제 호출 없이 파싱만 테스트하기 위해 분리)
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
