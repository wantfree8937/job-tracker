package com.example.jobtracker.service.job;

import com.example.jobtracker.dto.job.*;
import com.example.jobtracker.entity.job.ApplicationStatus;
import com.example.jobtracker.entity.job.CollectedJob;
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
import java.util.List;
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

    // 수집 공고 목록 (keyword/source 필터, 최신 등록순)
    @Transactional(readOnly = true)
    public List<CollectedJobResponse> findAll(String keyword, String source) {
        String pattern = (keyword == null || keyword.isBlank()) ? null : "%" + keyword.trim().toLowerCase() + "%";
        String sourceFilter = (source == null || source.isBlank()) ? null : source.trim();

        return collectedJobRepository.search(sourceFilter, pattern)
                .stream()
                .map(CollectedJobResponse::from)
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
                "자동 수집: " + collected.getSource()
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
    private URI validateUrl(String link) {
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
    private String extractMeta(String html, String property) {
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
