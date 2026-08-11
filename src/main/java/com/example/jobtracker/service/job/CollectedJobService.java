package com.example.jobtracker.service.job;

import com.example.jobtracker.dto.job.*;
import com.example.jobtracker.entity.job.ApplicationStatus;
import com.example.jobtracker.entity.job.CollectedJob;
import com.example.jobtracker.entity.job.JobPosting;
import com.example.jobtracker.entity.user.User;
import com.example.jobtracker.exception.InvalidCredentialsException;
import com.example.jobtracker.exception.JobAlreadyScrapedException;
import com.example.jobtracker.exception.JobNotFoundException;
import com.example.jobtracker.exception.LinkPreviewFailedException;
import com.example.jobtracker.repository.job.CollectedJobRepository;
import com.example.jobtracker.repository.job.JobPostingRepository;
import com.example.jobtracker.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CollectedJobService {

    private static final Path DATA_FILE = Path.of("C:\\Users\\pey21\\projects\\job-tracker\\data\\job_alerts.json");
    private static final Pattern HTTP_SCHEME = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);

    private final CollectedJobRepository collectedJobRepository;
    private final JobPostingRepository jobPostingRepository;
    private final JobPostingService jobPostingService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    // 크롤러가 저장한 JSON 파일을 읽어 DB에 저장한다 (jobKey 중복은 스킵)
    @Transactional
    public CollectedJobLoadResult loadFromFile() {
        if (!Files.exists(DATA_FILE)) {
            return new CollectedJobLoadResult(0, 0);
        }

        CollectedJobImport[] items;
        try {
            items = objectMapper.readValue(DATA_FILE.toFile(), CollectedJobImport[].class);
        } catch (Exception e) {
            return new CollectedJobLoadResult(0, 0);
        }

        int loaded = 0;
        int skipped = 0;
        for (CollectedJobImport item : items) {
            if (collectedJobRepository.existsByJobKey(item.jobKey())) {
                skipped++;
                continue;
            }
            CollectedJob job = new CollectedJob();
            job.setJobKey(item.jobKey());
            job.setCompany(item.company());
            job.setTitle(item.title());
            job.setUrl(item.url());
            job.setSource(item.source());
            collectedJobRepository.save(job);
            loaded++;
        }
        return new CollectedJobLoadResult(loaded, skipped);
    }

    // JobSearchService.search()가 찾은 공고를 저장한다 (크롤링과 분리된 트랜잭션 — DB 커넥션을 크롤링 동안 점유하지 않기 위함)
    // filteredOut: 제목/회사명에 키워드가 없어 이미 걸러진 공고 수 (skipped 초기값)
    @Transactional
    public JobSearchResult saveMatchedJobs(String keyword, List<CollectedJob> matched, int filteredOut) {
        int collected = 0;
        int skipped = filteredOut;
        for (CollectedJob job : matched) {
            var existing = collectedJobRepository.findByJobKey(job.getJobKey());
            if (existing.isPresent()) {
                // 이미 수집된 공고: 새로 갱신된 필드(region/experience/industry)만 빈 값을 채워준다
                if (JobSearchService.fillBlankFields(existing.get(), job)) {
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

    // 수집 공고 목록 (keyword/source 필터 + mine=true일 때 내 관심 키워드로 추가 필터, 최신 등록순)
    // searchField: all(기본, 제목+회사명) / company(회사명만) / title(제목만)
    @Transactional(readOnly = true)
    public List<CollectedJobResponse> findAll(String keyword, String source, boolean mine, String email, String searchField) {
        String pattern = (keyword == null || keyword.isBlank()) ? null : "%" + keyword.trim().toLowerCase() + "%";
        String sourceFilter = (source == null || source.isBlank()) ? null : source.trim();

        List<CollectedJob> jobs = switch (searchField) {
            case "company" -> collectedJobRepository.searchByCompany(sourceFilter, pattern);
            case "title" -> collectedJobRepository.searchByTitle(sourceFilter, pattern);
            default -> collectedJobRepository.search(sourceFilter, pattern);
        };

        if (mine) {
            List<String> myKeywords = getMyKeywords(email);
            if (!myKeywords.isEmpty()) {
                jobs = jobs.stream()
                        .filter(job -> matchesAnyKeyword(job, myKeywords, searchField))
                        .toList();
            }
        }

        Set<String> myScrapedUrls = getMyScrapedUrls(email);

        return jobs.stream()
                .map(job -> CollectedJobResponse.from(job, myScrapedUrls.contains(job.getUrl())))
                .toList();
    }

    // 로그인 사용자가 이미 스크랩한 공고의 url 목록 (scrap 중복 검사와 동일하게 link 기준)
    private Set<String> getMyScrapedUrls(String email) {
        Long userId = getUserId(email);
        return jobPostingRepository.findByUserId(userId).stream()
                .map(JobPosting::getLink)
                .collect(Collectors.toSet());
    }

    // 관심 키워드 중 하나라도 제목/회사명(searchField로 범위 지정)에 포함되는지 확인 (대소문자 무시)
    private boolean matchesAnyKeyword(CollectedJob job, List<String> keywords, String searchField) {
        String title = job.getTitle() == null ? "" : job.getTitle().toLowerCase();
        String company = job.getCompany() == null ? "" : job.getCompany().toLowerCase();
        return keywords.stream().anyMatch(k -> switch (searchField) {
            case "company" -> company.contains(k);
            case "title" -> title.contains(k);
            default -> title.contains(k) || company.contains(k);
        });
    }

    private List<String> getMyKeywords(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        String keywords = user.getKeywords();
        if (keywords == null || keywords.isBlank()) {
            return List.of();
        }
        return Arrays.stream(keywords.split(","))
                .map(String::toLowerCase)
                .toList();
    }

    // 수집 공고를 내 공고(JobPosting)로 스크랩 (link 중복 시 409)
    @Transactional
    public JobPostingResponse scrap(String email, Long id) {
        CollectedJob collected = collectedJobRepository.findById(id)
                .orElseThrow(JobNotFoundException::new);

        Long userId = getUserId(email);
        boolean alreadyScraped = jobPostingRepository.findByUserId(userId).stream()
                .anyMatch(job -> collected.getUrl().equals(job.getLink()));
        if (alreadyScraped) {
            throw new JobAlreadyScrapedException();
        }

        JobPostingRequest request = new JobPostingRequest(
                collected.getCompany(),
                collected.getTitle(),
                collected.getUrl(),
                null,
                ApplicationStatus.WISH,
                "자동 수집: " + collected.getSource(),
                collected.getRegion(),
                collected.getExperience(),
                collected.getIndustry(),
                collected.getSource()
        );
        return jobPostingService.create(email, request);
    }

    // 링크의 OpenGraph 메타데이터를 읽어 회사명/포지션/설명을 추출한다
    public LinkPreviewResponse preview(LinkPreviewRequest request) {
        URI uri = validateUrl(request.link());

        String html;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                    .header("User-Agent", "Mozilla/5.0 (compatible; JobTrackerBot/1.0)")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new LinkPreviewFailedException();
            }
            html = response.body();
        } catch (LinkPreviewFailedException e) {
            throw e;
        } catch (Exception e) {
            throw new LinkPreviewFailedException();
        }

        return new LinkPreviewResponse(
                extractMeta(html, "og:site_name"),
                extractMeta(html, "og:title"),
                extractMeta(html, "og:description")
        );
    }

    // http/https만 허용하고 로컬/사설 대역 접근은 차단한다 (SSRF 방지)
    static URI validateUrl(String link) {
        if (!HTTP_SCHEME.matcher(link).find()) {
            throw new IllegalArgumentException("http/https URL만 허용됩니다");
        }

        URI uri;
        try {
            uri = URI.create(link);
        } catch (Exception e) {
            throw new IllegalArgumentException("올바르지 않은 URL입니다");
        }

        String host = uri.getHost() == null ? null : uri.getHost().toLowerCase();
        if (host == null || host.equals("localhost") || host.equals("127.0.0.1")
                || host.startsWith("192.168.") || host.startsWith("10.")) {
            throw new IllegalArgumentException("접근할 수 없는 주소입니다");
        }
        return uri;
    }

    // property/content 순서가 바뀐 경우까지 처리하는 og 메타 태그 추출
    static String extractMeta(String html, String property) {
        Pattern propertyFirst = Pattern.compile(
                "<meta[^>]+property=[\"']" + property + "[\"'][^>]+content=[\"']([^\"']*)[\"']");
        Matcher m1 = propertyFirst.matcher(html);
        if (m1.find()) {
            return m1.group(1);
        }

        Pattern contentFirst = Pattern.compile(
                "<meta[^>]+content=[\"']([^\"']*)[\"'][^>]+property=[\"']" + property + "[\"']");
        Matcher m2 = contentFirst.matcher(html);
        if (m2.find()) {
            return m2.group(1);
        }

        return null;
    }

    private Long getUserId(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        return user.getId();
    }

    // job_alerts.json 파싱용 DTO
    private record CollectedJobImport(String company, String title, String url, String source, String jobKey) {
    }
}
