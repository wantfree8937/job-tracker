package com.example.jobtracker.service.job;

import com.example.jobtracker.dto.job.JobSearchResult;
import com.example.jobtracker.entity.job.CollectedJob;
import com.example.jobtracker.entity.user.User;
import com.example.jobtracker.exception.JobSearchFailedException;
import com.example.jobtracker.repository.job.CollectedJobRepository;
import com.example.jobtracker.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 자유 키워드로 원티드/잡코리아를 즉시 검색해 수집하고, 크롤러용 전체 관심 키워드를 조회하는 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobSearchService {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final String CARD_DELIMITER = "data-sentry-component=\"CardJob\"";
    private static final Pattern JOBKOREA_ID = Pattern.compile("GI_Read/(\\d+)");
    private static final Pattern JOBKOREA_TITLE = Pattern.compile("text-typo-b1-18 text-gray900\">([^<]+)</span>");
    private static final Pattern JOBKOREA_COMPANY = Pattern.compile("<img alt=\"([^\"]+?)(?:㈜|\\(주\\)| 로고)");

    private final CollectedJobRepository collectedJobRepository;
    private final UserRepository userRepository;

    private final RestClient restClient = RestClient.builder()
            .requestFactory(timeoutRequestFactory())
            .defaultHeader("User-Agent", USER_AGENT)
            // 봇 감지 우회: 브라우저처럼 보이는 헤더 (실측 검증 2026-08-07)
            .defaultHeader("Accept", "application/json, text/plain, */*")
            .defaultHeader("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8")
            .build();

    private static SimpleClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }

    // 키워드로 원티드/잡코리아를 검색해 새 공고만 저장한다 (한쪽이 실패해도 다른 쪽은 시도, 둘 다 실패하면 예외)
    @Transactional
    public JobSearchResult search(String keyword) {
        List<CollectedJob> found = new ArrayList<>();
        boolean wantedOk = collectQuietly(found, () -> searchWanted(keyword));
        boolean jobKoreaOk = collectQuietly(found, () -> searchJobKorea(keyword));

        if (!wantedOk && !jobKoreaOk) {
            throw new JobSearchFailedException();
        }

        // 제목/회사명 어디에도 키워드가 없는 무관한 공고를 걸러낸다
        List<CollectedJob> matched = filterByTitleOrCompany(found, keyword);

        int collected = 0;
        int skipped = found.size() - matched.size();
        for (CollectedJob job : matched) {
            if (collectedJobRepository.existsByJobKey(job.getJobKey())) {
                skipped++;
                continue;
            }
            collectedJobRepository.save(job);
            collected++;
        }
        return new JobSearchResult(keyword, collected, skipped);
    }

    // 제목(title) 또는 회사명(company)에 키워드가 포함된 공고만 남긴다 (대소문자 무시)
    static List<CollectedJob> filterByTitleOrCompany(List<CollectedJob> jobs, String keyword) {
        String lowerKeyword = keyword.toLowerCase();
        return jobs.stream()
                .filter(job -> job.getTitle().toLowerCase().contains(lowerKeyword)
                        || job.getCompany().toLowerCase().contains(lowerKeyword))
                .toList();
    }

    private boolean collectQuietly(List<CollectedJob> found, java.util.function.Supplier<List<CollectedJob>> supplier) {
        try {
            found.addAll(supplier.get());
            return true;
        } catch (Exception e) {
            log.warn("공고 검색 실패: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    // curl로 외부 페이지를 가져온다 (실측 검증: 자바 HTTP 클라이언트는 원티드/잡코리아 봇 감지에 걸려 빈 결과 반환)
    private String fetchWithCurl(String url) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("curl", "-s", "--max-time", "15",
                "-A", USER_AGENT,
                "-H", "Accept-Language: ko-KR,ko;q=0.9,en-US;q=0.8",
                url);
        Process p = pb.start();
        String body = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();
        return body;
    }

    private List<CollectedJob> searchWanted(String keyword) {
        String url = "https://www.wanted.co.kr/api/v4/jobs?country=kr&query="
                + URLEncoder.encode(keyword, StandardCharsets.UTF_8) + "&limit=30";
        String json = null;
        try {
            json = fetchWithCurl(url);
        } catch (Exception e) {
            log.warn("원티드 검색 curl 실패: {}", e.getMessage());
            return List.of();
        }
        JsonNode root;
        try {
            root = new tools.jackson.databind.ObjectMapper().readTree(json);
        } catch (Exception e) {
            log.warn("원티드 응답 파싱 실패: {}", e.getMessage());
            return List.of();
        }

        List<CollectedJob> jobs = new ArrayList<>();
        if (root == null) {
            return jobs;
        }
        for (JsonNode item : root.path("data")) {
            long id = item.path("id").asLong();
            CollectedJob job = new CollectedJob();
            job.setJobKey("원티드:" + id);
            job.setTitle(item.path("position").asText());
            job.setCompany(item.path("company").path("name").asText());
            job.setUrl("https://www.wanted.co.kr/wd/" + id);
            job.setSource("원티드");
            jobs.add(job);
        }
        return jobs;
    }

    // 잡코리아 결과가 페이지당 21~22건이라 1페이지만으로는 후순위 공고가 유실됨 (실측: '네트워크' 검색 시 3페이지 소재 공고 누락)
    private static final int JOBKOREA_MAX_PAGE = 3;

    private List<CollectedJob> searchJobKorea(String keyword) {
        List<CollectedJob> jobs = new ArrayList<>();
        for (int page = 1; page <= JOBKOREA_MAX_PAGE; page++) {
            String url = jobKoreaSearchUrl(keyword, page);
            String html;
            try {
                html = fetchWithCurl(url);
            } catch (Exception e) {
                log.warn("잡코리아 검색 curl 실패 (Page_No={}): {}", page, e.getMessage());
                continue;
            }
            if (html == null) {
                continue;
            }
            jobs.addAll(parseJobKoreaCards(html));
        }
        return jobs;
    }

    static String jobKoreaSearchUrl(String keyword, int page) {
        return "https://www.jobkorea.co.kr/Search/?stext="
                + URLEncoder.encode(keyword, StandardCharsets.UTF_8) + "&tabType=recruit&careerType=1&Page_No=" + page;
    }

    private static List<CollectedJob> parseJobKoreaCards(String html) {
        List<CollectedJob> jobs = new ArrayList<>();
        String[] cards = html.split(Pattern.quote(CARD_DELIMITER));
        for (int i = 1; i < cards.length; i++) {
            String card = cards[i];
            Matcher idMatcher = JOBKOREA_ID.matcher(card);
            Matcher titleMatcher = JOBKOREA_TITLE.matcher(card);
            Matcher companyMatcher = JOBKOREA_COMPANY.matcher(card);
            if (!idMatcher.find() || !titleMatcher.find() || !companyMatcher.find()) {
                continue;
            }
            String id = idMatcher.group(1);
            CollectedJob job = new CollectedJob();
            job.setJobKey("잡코리아:" + id);
            job.setTitle(titleMatcher.group(1));
            job.setCompany(companyMatcher.group(1).trim());
            job.setUrl("https://www.jobkorea.co.kr/Recruit/GI_Read/" + id);
            job.setSource("잡코리아");
            jobs.add(job);
        }
        return jobs;
    }

    // 모든 사용자의 관심 키워드를 중복 제거해 반환한다 (크롤러용, 개인정보 없음)
    @Transactional(readOnly = true)
    public List<String> findAllKeywords() {
        return userRepository.findAll().stream()
                .map(User::getKeywords)
                .filter(keywords -> keywords != null && !keywords.isBlank())
                .flatMap(keywords -> Arrays.stream(keywords.split(",")))
                .map(String::trim)
                .filter(keyword -> !keyword.isEmpty())
                .distinct()
                .toList();
    }
}
