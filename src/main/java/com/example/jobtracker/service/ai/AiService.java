package com.example.jobtracker.service.ai;

import com.example.jobtracker.dto.ai.InterviewQuestionRequest;
import com.example.jobtracker.dto.ai.InterviewQuestionResponse;
import com.example.jobtracker.entity.user.User;
import com.example.jobtracker.exception.AiRequestFailedException;
import com.example.jobtracker.exception.InvalidCredentialsException;
import com.example.jobtracker.repository.user.UserRepository;
import com.example.jobtracker.util.ResumeTextExtractor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** opencode-go API로 채용공고 정보(+ 사용자 이력서) 기반 예상 면접 질문을 생성한다 (API 키 노출 방지용 백엔드 프록시) */
@Slf4j
@Service
public class AiService {

    private static final String INTRO = "너는 한국 IT 기업의 면접관이다.\n";

    private static final String TECHNICAL_INSTRUCTION =
            "지원자의 기술 역량을 검증하는 기술 면접 질문 5개를 생성해라.";
    private static final String TECHNICAL_WITH_PROFILE_INSTRUCTION =
            "지원자의 이력서에 언급된 기술 스택 위주로, 일반적인 기술 질문도 섞어서 기술 면접 질문 5개를 생성해라.";
    private static final String PORTFOLIO_INSTRUCTION =
            "지원자의 프로젝트 경험을 검증하는 질문 5개를 생성해라 (구현 결정/트러블슈팅/협업 경험 위주, 특정 프로젝트를 가정하지 말고 일반적으로 질문해라).";
    private static final String PORTFOLIO_WITH_PROFILE_INSTRUCTION =
            "이 내용을 기반으로 프로젝트 경험/구현 결정/트러블슈팅을 검증하는 질문 5개를 생성해라.";
    private static final String MIXED_INSTRUCTION =
            "기술 역량 질문과 프로젝트 경험 질문을 섞어서 5개 생성해라.";
    private static final String MIXED_WITH_PROFILE_INSTRUCTION =
            "이력서 기반 프로젝트 경험 질문과 일반 기술 역량 질문을 섞어서 5개 생성해라.";
    private static final String NO_PROFILE_WITH_JOB_INSTRUCTION =
            "이력서 정보가 없으니, 채용공고 정보(회사/포지션/요구사항)를 바탕으로 지원 동기, 회사/업종 이해도, 포지션 요구사항에 대한 준비, 직무 관련 경험 유무 등을 묻는 질문 5개를 생성해라.";
    private static final String MISMATCH_INSTRUCTION =
            "채용공고와 이력서/포트폴리오가 직무/기술 스택 기준으로 전혀 무관하다고 판단되면, 이력서 내용은 무시하고 채용공고 정보(회사/포지션/요구사항)만으로 질문을 구성해라.";

    private static final String ENTRY_INSTRUCTION =
            "난이도는 신입: 기술 질문은 지원자가 사용한 기술의 기본 개념 수준으로만 묻고, 프로젝트 경험(무엇을 했는지, 왜 그렇게 했는지, 어떤 어려움을 겪었고 어떻게 해결했는지)과 협업 경험, 성향, 문제 해결 태도, 성장 가능성 위주로 질문해라. 압박 질문이나 시니어급 질문은 금지한다.";
    private static final String EASY_INSTRUCTION = "난이도는 쉬움: 기초 개념이나 개인 경험을 묻는 쉬운 질문으로 구성해라.";
    private static final String NORMAL_INSTRUCTION =
            "난이도는 보통: 신입이 답할 수 있는 실무 기반 질문으로 구성해라 (프로젝트 경험, 기본 개념, 기술 스택 활용 위주).";
    private static final String HARD_INSTRUCTION =
            "난이도는 어려움: 신입에게 도전적이되 답변 가능한 수준의 질문으로 구성해라 (경험 기반 트러블슈팅, 설계 의도, 협업 경험 위주). 시니어급의 과도하게 어려운 질문은 피해라.";

    private static final String OUTPUT_FORMAT_INSTRUCTION =
            "질문은 반드시 JSON 문자열 배열로만 응답해라 (마크다운/설명 금지).";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final Pattern WANTED_URL_ID = Pattern.compile("wanted\\.co\\.kr/wd/(\\d+)");
    private static final Pattern JOBKOREA_URL_ID = Pattern.compile("jobkorea\\.co\\.kr/Recruit/GI_Read/(\\d+)");

    private final String apiKey;
    private final RestClient restClient;
    private final UserRepository userRepository;

    public AiService(@Value("${opencode-go.api-key}") String apiKey,
                      @Value("${opencode-go.base-url}") String baseUrl,
                      UserRepository userRepository) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.userRepository = userRepository;
    }

    public InterviewQuestionResponse generateQuestions(String email, InterviewQuestionRequest request) {
        if (apiKey.isBlank()) {
            log.warn("opencode-go API 키가 설정되지 않음 (OPENCODE_GO_API_KEY 환경변수 확인 필요)");
            throw new AiRequestFailedException();
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        String systemPrompt = buildSystemPromptWithJobDetail(request, resolveProfileText(user));

        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", "deepseek-v4-flash",
                            "messages", List.of(
                                    Map.of("role", "system", "content", systemPrompt),
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

    // profileText가 비어있으면 업로드된 이력서 원본 파일에서 텍스트를 추출해 대신 사용한다
    private String resolveProfileText(User user) {
        String profileText = user.getProfileText();
        if (profileText != null && !profileText.isBlank()) {
            return profileText;
        }
        if (user.getResumeFile() == null) {
            return null;
        }
        try {
            return ResumeTextExtractor.extractResumeText(
                    user.getResumeFile(), user.getResumeFileType(), user.getResumeFileName());
        } catch (Exception e) {
            log.warn("이력서 파일 텍스트 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    // topic/difficulty와 채용공고 정보, 이력서(profileText) 유무에 따라 시스템 프롬프트를 동적으로 구성한다
    static String buildSystemPrompt(InterviewQuestionRequest request, String profileText) {
        boolean hasProfile = profileText != null && !profileText.isBlank();
        boolean hasJobInfo = hasJobInfo(request);

        StringBuilder sb = new StringBuilder(INTRO);
        if (hasProfile) {
            sb.append("지원자의 이력서/포트폴리오: ").append(profileText).append("\n");
            if (hasJobInfo) {
                sb.append(MISMATCH_INSTRUCTION).append("\n");
            }
        }

        if (!hasProfile && hasJobInfo) {
            sb.append(NO_PROFILE_WITH_JOB_INSTRUCTION).append("\n");
        } else {
            sb.append(switch (request.topic() == null ? "MIXED" : request.topic()) {
                case "TECHNICAL" -> hasProfile ? TECHNICAL_WITH_PROFILE_INSTRUCTION : TECHNICAL_INSTRUCTION;
                case "PORTFOLIO" -> hasProfile ? PORTFOLIO_WITH_PROFILE_INSTRUCTION : PORTFOLIO_INSTRUCTION;
                default -> hasProfile ? MIXED_WITH_PROFILE_INSTRUCTION : MIXED_INSTRUCTION;
            }).append("\n");
        }

        sb.append(switch (request.difficulty() == null ? "NORMAL" : request.difficulty()) {
            case "ENTRY" -> ENTRY_INSTRUCTION;
            case "EASY" -> EASY_INSTRUCTION;
            case "HARD" -> HARD_INSTRUCTION;
            default -> NORMAL_INSTRUCTION;
        }).append("\n");

        sb.append(hasJobInfo
                ? "아래 채용공고 정보를 참고해서 질문을 생성해라.\n"
                : "특정 채용공고 없이 일반적인 면접이라고 가정해라.\n");

        sb.append(OUTPUT_FORMAT_INSTRUCTION);
        return sb.toString();
    }

    // url이 있으면 채용공고를 실시간 크롤링해 자격요건/주요업무를 시스템 프롬프트에 덧붙인다. 실패하면 조용히 폴백
    private String buildSystemPromptWithJobDetail(InterviewQuestionRequest request, String profileText) {
        String prompt = buildSystemPrompt(request, profileText);
        if (request.url() == null || request.url().isBlank()) {
            return prompt;
        }
        String jobDetailText = fetchJobDetails(request.url());
        if (jobDetailText == null) {
            return prompt;
        }
        return prompt + "\n채용공고 자격요건·주요업무: " + jobDetailText
                + "\n이 공고의 자격요건과 주요업무를 중심으로 질문을 구성해라.";
    }

    // 원티드/잡코리아 공고 URL에서 자격요건/주요업무 텍스트를 가져온다. 실패/미지원 URL이면 null
    private String fetchJobDetails(String url) {
        try {
            Matcher wantedMatcher = WANTED_URL_ID.matcher(url);
            if (wantedMatcher.find()) {
                String json = fetchWithCurl("https://www.wanted.co.kr/api/v4/jobs/" + wantedMatcher.group(1), null);
                return parseWantedJobDetail(json);
            }
            Matcher jobKoreaMatcher = JOBKOREA_URL_ID.matcher(url);
            if (jobKoreaMatcher.find()) {
                String id = jobKoreaMatcher.group(1);
                String iframeUrl = "https://www.jobkorea.co.kr/Recruit/GI_Read_Comt_Ifrm?Gno=" + id
                        + "&isHiringCenter=false&hideMapView=false";
                String html = fetchWithCurl(iframeUrl, "https://www.jobkorea.co.kr/Recruit/GI_Read/" + id);
                return parseJobKoreaJobDetail(html);
            }
        } catch (Exception e) {
            log.warn("채용공고 크롤링 실패: {}", e.getMessage());
        }
        return null;
    }

    // curl로 외부 페이지를 가져온다 (자바 HTTP 클라이언트는 원티드/잡코리아 봇 감지에 걸림), 5초 제한
    private static String fetchWithCurl(String url, String referer) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("curl", "-s", "--max-time", "5",
                "-A", USER_AGENT, "-H", "Accept-Language: ko-KR,ko;q=0.9,en-US;q=0.8"));
        if (referer != null) {
            cmd.add("-H");
            cmd.add("Referer: " + referer);
        }
        cmd.add(url);
        Process process = new ProcessBuilder(cmd).start();
        String body = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor();
        return body;
    }

    // 원티드 상세 API 응답(job.detail.*)에서 자격요건/주요업무/우대사항을 추출한다 (실제 호출 없이 파싱만 테스트하기 위해 분리)
    static String parseWantedJobDetail(String json) {
        try {
            JsonNode detail = new ObjectMapper().readTree(json).path("job").path("detail");
            StringBuilder sb = new StringBuilder();
            appendField(sb, "자격요건", detail.path("requirements").asText(""));
            appendField(sb, "주요업무", detail.path("main_tasks").asText(""));
            appendField(sb, "우대사항", detail.path("preferred_points").asText(""));
            return sb.isEmpty() ? null : sb.toString();
        } catch (Exception e) {
            log.warn("원티드 공고 상세 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    private static final int JOBKOREA_DETAIL_MAX_LENGTH = 3000;

    // 잡코리아 상세 iframe HTML(#detail-content)에서 본문 텍스트를 추출한다 (배너 이미지형 공고는 빈 값)
    static String parseJobKoreaJobDetail(String html) {
        try {
            String text = Jsoup.parse(html).select("#detail-content").text();
            if (text.isBlank()) {
                return null;
            }
            return text.length() > JOBKOREA_DETAIL_MAX_LENGTH ? text.substring(0, JOBKOREA_DETAIL_MAX_LENGTH) : text;
        } catch (Exception e) {
            log.warn("잡코리아 공고 상세 파싱 실패: {}", e.getMessage());
            return null;
        }
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
