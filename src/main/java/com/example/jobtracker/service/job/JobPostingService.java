package com.example.jobtracker.service.job;

import com.example.jobtracker.dto.job.JobPostingRequest;
import com.example.jobtracker.dto.job.JobPostingResponse;
import com.example.jobtracker.dto.job.JobPostingUpdateRequest;
import com.example.jobtracker.dto.job.JobStatusStatsResponse;
import com.example.jobtracker.entity.job.ApplicationStatus;
import com.example.jobtracker.entity.job.JobPosting;
import com.example.jobtracker.entity.user.User;
import com.example.jobtracker.exception.InvalidCredentialsException;
import com.example.jobtracker.exception.JobNotFoundException;
import com.example.jobtracker.repository.job.JobPostingRepository;
import com.example.jobtracker.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JobPostingService {

    private final JobPostingRepository jobPostingRepository;
    private final UserRepository userRepository;

    // 공고 생성
    @Transactional
    public JobPostingResponse create(String email, JobPostingRequest request) {
        Long userId = getUserId(email);

        JobPosting job = new JobPosting();
        job.setUserId(userId);
        job.setCompanyName(request.companyName());
        job.setPosition(request.position());
        job.setLink(request.link());
        job.setDeadline(request.deadline());
        job.setMemo(request.memo());
        job.setRegion(request.region());
        job.setExperience(request.experience());
        job.setIndustry(request.industry());
        job.setSource(request.source());
        if (request.status() != null) {
            job.setStatus(request.status());
        }

        return JobPostingResponse.from(jobPostingRepository.save(job));
    }

    // 내 공고 목록 (상태/키워드 필터, 최신 수정순)
    @Transactional(readOnly = true)
    public List<JobPostingResponse> findAll(String email, String status, String keyword) {
        Long userId = getUserId(email);
        ApplicationStatus parsedStatus = status == null ? null : ApplicationStatus.from(status);
        String pattern = (keyword == null || keyword.isBlank()) ? null : "%" + keyword.trim().toLowerCase() + "%";

        return jobPostingRepository.search(userId, parsedStatus, pattern)
                .stream()
                .map(JobPostingResponse::from)
                .toList();
    }

    // 공고 상세 (내 공고가 아니면 404)
    @Transactional(readOnly = true)
    public JobPostingResponse findOne(String email, Long id) {
        Long userId = getUserId(email);
        return JobPostingResponse.from(getMyJob(userId, id));
    }

    // 공고 부분 수정 (요청에 온 필드만 반영)
    @Transactional
    public JobPostingResponse update(String email, Long id, JobPostingUpdateRequest request) {
        Long userId = getUserId(email);
        JobPosting job = getMyJob(userId, id);

        if (request.companyName() != null) job.setCompanyName(request.companyName());
        if (request.position() != null) job.setPosition(request.position());
        if (request.link() != null) job.setLink(request.link());
        if (request.deadline() != null) job.setDeadline(request.deadline());
        if (request.status() != null) job.setStatus(request.status());
        if (request.memo() != null) job.setMemo(request.memo());

        return JobPostingResponse.from(job);
    }

    // 공고 삭제 (내 공고가 아니면 404)
    @Transactional
    public void delete(String email, Long id) {
        Long userId = getUserId(email);
        jobPostingRepository.delete(getMyJob(userId, id));
    }

    // 상태별 개수 집계 (없는 상태는 0)
    @Transactional(readOnly = true)
    public JobStatusStatsResponse getStats(String email) {
        Long userId = getUserId(email);

        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus s : ApplicationStatus.values()) {
            counts.put(s, 0L);
        }
        for (JobPosting job : jobPostingRepository.findByUserId(userId)) {
            counts.merge(job.getStatus(), 1L, Long::sum);
        }

        Map<String, Long> result = new LinkedHashMap<>();
        counts.forEach((s, count) -> result.put(s.name(), count));
        return new JobStatusStatsResponse(result);
    }

    private Long getUserId(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        return user.getId();
    }

    private JobPosting getMyJob(Long userId, Long id) {
        return jobPostingRepository.findByIdAndUserId(id, userId)
                .orElseThrow(JobNotFoundException::new);
    }
}
