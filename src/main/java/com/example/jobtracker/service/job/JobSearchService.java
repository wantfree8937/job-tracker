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
    // 시도 + 시군구 (예: "대전 중구") — 시군구가 없으면 시도만 매칭
    private static final Pattern JOBKOREA_REGION = Pattern.compile(
            "(서울|경기|인천|대전|대구|부산|광주|울산|세종|강원|충북|충남|전북|전남|경북|경남|제주)\\s?[^\\s,<]*");
    private static final Pattern JOBKOREA_EXPERIENCE = Pattern.compile("경력무관|신입|경력[^•,<]*");
    // GrayChip 텍스트 (지역/업종/연봉 칩들) — 2번째 칩이 쉼표 구분 업종 리스트
    private static final Pattern JOBKOREA_CHIP = Pattern.compile("text-typo-b4-14\">([^<]+)</span>");

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
            var existing = collectedJobRepository.findByJobKey(job.getJobKey());
            if (existing.isPresent()) {
                // 이미 수집된 공고: 새로 갱신된 필드(region/experience/industry)만 빈 값을 채워준다
                if (fillBlankFields(existing.get(), job)) {
                    collectedJobRepository.save(existing.get());
                }
                skipped++;
                continue;
            }
            collectedJobRepository.save(job);
            collected++;
        }
        return new JobSearchResult(keyword, collected, skipped);
    }

    // existing의 region/experience/industry가 비어있고 incoming에 값이 있으면 채운다. 갱신 여부를 반환
    static boolean fillBlankFields(CollectedJob existing, CollectedJob incoming) {
        boolean updated = false;
        if (isBlank(existing.getRegion()) && incoming.getRegion() != null) {
            existing.setRegion(incoming.getRegion());
            updated = true;
        }
        if (isBlank(existing.getExperience()) && incoming.getExperience() != null) {
            existing.setExperience(incoming.getExperience());
            updated = true;
        }
        if (isBlank(existing.getIndustry()) && incoming.getIndustry() != null) {
            existing.setIndustry(incoming.getIndustry());
            updated = true;
        }
        return updated;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
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
        String json;
        try {
            json = fetchWithCurl(url);
        } catch (Exception e) {
            log.warn("원티드 검색 curl 실패: {}", e.getMessage());
            return List.of();
        }
        return parseWanted(json);
    }

    // 원티드 API JSON 응답을 공고 리스트로 변환한다 (실제 호출 없이 파싱 로직만 테스트하기 위해 분리)
    static List<CollectedJob> parseWanted(String json) {
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

            String region = item.path("address").path("location").asText("");
            if (!region.isBlank()) {
                job.setRegion(region);
            }
            String industry = item.path("company").path("industry_name").asText("");
            if (!industry.isBlank()) {
                job.setIndustry(industry);
            }
            // 원티드에는 경력 정보가 없음 (annual_from/to는 연봉) — experience는 null 유지

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
            jobs.addAll(parseJobKorea(html));
        }
        return jobs;
    }

    static String jobKoreaSearchUrl(String keyword, int page) {
        return "https://www.jobkorea.co.kr/Search/?stext="
                + URLEncoder.encode(keyword, StandardCharsets.UTF_8) + "&tabType=recruit&careerType=1&Page_No=" + page;
    }

    // 잡코리아 검색 결과 HTML을 공고 리스트로 변환한다 (실제 호출 없이 파싱 로직만 테스트하기 위해 분리)
    static List<CollectedJob> parseJobKorea(String html) {
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

            Matcher regionMatcher = JOBKOREA_REGION.matcher(card);
            if (regionMatcher.find()) {
                job.setRegion(regionMatcher.group());
            }
            Matcher experienceMatcher = JOBKOREA_EXPERIENCE.matcher(card);
            if (experienceMatcher.find()) {
                job.setExperience(experienceMatcher.group());
            }

            Matcher chipMatcher = JOBKOREA_CHIP.matcher(card);
            int chipIndex = 0;
            while (chipMatcher.find()) {
                chipIndex++;
                if (chipIndex == 2) {
                    job.setIndustry(chipMatcher.group(1).split(",")[0].trim());
                    break;
                }
            }

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
