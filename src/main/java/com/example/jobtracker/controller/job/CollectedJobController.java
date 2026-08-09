package com.example.jobtracker.controller.job;

import com.example.jobtracker.dto.job.*;
import com.example.jobtracker.service.job.CollectedJobService;
import com.example.jobtracker.service.job.JobSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class CollectedJobController {

    private final CollectedJobService collectedJobService;
    private final JobSearchService jobSearchService;

    // 크롤러가 저장한 JSON 파일을 DB로 적재
    @PostMapping("/collected/load")
    public ResponseEntity<CollectedJobLoadResult> load() {
        return ResponseEntity.ok(collectedJobService.loadFromFile());
    }

    // 수집 공고 목록 (mine=true면 내 관심 키워드와 매칭되는 공고만, searchField로 검색 범위 지정: all/company/title)
    @GetMapping("/collected")
    public ResponseEntity<List<CollectedJobResponse>> findAll(Authentication authentication,
                                                                 @RequestParam(required = false) String keyword,
                                                                 @RequestParam(required = false) String source,
                                                                 @RequestParam(required = false, defaultValue = "false") boolean mine,
                                                                 @RequestParam(required = false, defaultValue = "all") String searchField) {
        return ResponseEntity.ok(collectedJobService.findAll(keyword, source, mine, authentication.getName(), searchField));
    }

    // 수집 공고를 내 공고로 스크랩
    @PostMapping("/collected/{id}/scrap")
    public ResponseEntity<JobPostingResponse> scrap(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(collectedJobService.scrap(authentication.getName(), id));
    }

    // 링크의 OpenGraph 메타데이터로 회사명/포지션/메모 자동 채우기
    @PostMapping("/preview")
    public ResponseEntity<LinkPreviewResponse> preview(@Valid @RequestBody LinkPreviewRequest request) {
        return ResponseEntity.ok(collectedJobService.preview(request));
    }

    // 자유 키워드로 원티드/잡코리아를 즉시 검색해 수집
    @PostMapping("/collect/search")
    public ResponseEntity<JobSearchResult> search(@Valid @RequestBody JobSearchRequest request) {
        return ResponseEntity.ok(jobSearchService.search(request.keyword()));
    }

    // 모든 사용자의 관심 키워드 목록 (중복 제거) — 크롤러가 매일 자동 수집 시 조회
    @GetMapping("/collect/keywords")
    public ResponseEntity<List<String>> keywords() {
        return ResponseEntity.ok(jobSearchService.findAllKeywords());
    }

    // 경력 정보가 없는 기존 수집 공고를 제목에서 추출해 일괄로 채운다
    @PostMapping("/collected/backfill-experience")
    public ResponseEntity<BackfillExperienceResult> backfillExperience() {
        return ResponseEntity.ok(jobSearchService.backfillExperience());
    }
}
