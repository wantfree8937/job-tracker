package com.example.jobtracker.controller.job;

import com.example.jobtracker.dto.job.*;
import com.example.jobtracker.service.job.CollectedJobService;
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

    // 크롤러가 저장한 JSON 파일을 DB로 적재
    @PostMapping("/collected/load")
    public ResponseEntity<CollectedJobLoadResult> load() {
        return ResponseEntity.ok(collectedJobService.loadFromFile());
    }

    // 수집 공고 전체 목록 (내 공고 목록과는 별개)
    @GetMapping("/collected")
    public ResponseEntity<List<CollectedJobResponse>> findAll(@RequestParam(required = false) String keyword,
                                                                 @RequestParam(required = false) String source) {
        return ResponseEntity.ok(collectedJobService.findAll(keyword, source));
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
}
