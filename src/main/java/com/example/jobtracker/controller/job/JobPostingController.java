package com.example.jobtracker.controller.job;

import com.example.jobtracker.dto.job.JobPostingRequest;
import com.example.jobtracker.dto.job.JobPostingResponse;
import com.example.jobtracker.dto.job.JobPostingUpdateRequest;
import com.example.jobtracker.dto.job.JobStatusStatsResponse;
import com.example.jobtracker.service.job.JobPostingService;
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
public class JobPostingController {

    private final JobPostingService jobPostingService;

    @PostMapping
    public ResponseEntity<JobPostingResponse> create(Authentication authentication,
                                                       @Valid @RequestBody JobPostingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobPostingService.create(authentication.getName(), request));
    }

    @GetMapping
    public ResponseEntity<List<JobPostingResponse>> findAll(Authentication authentication,
                                                              @RequestParam(required = false) String status,
                                                              @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(jobPostingService.findAll(authentication.getName(), status, keyword));
    }

    @GetMapping("/stats")
    public ResponseEntity<JobStatusStatsResponse> stats(Authentication authentication) {
        return ResponseEntity.ok(jobPostingService.getStats(authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobPostingResponse> findOne(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(jobPostingService.findOne(authentication.getName(), id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<JobPostingResponse> update(Authentication authentication,
                                                       @PathVariable Long id,
                                                       @Valid @RequestBody JobPostingUpdateRequest request) {
        return ResponseEntity.ok(jobPostingService.update(authentication.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        jobPostingService.delete(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
