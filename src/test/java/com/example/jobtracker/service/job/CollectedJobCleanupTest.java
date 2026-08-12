package com.example.jobtracker.service.job;

import com.example.jobtracker.entity.job.CollectedJob;
import com.example.jobtracker.repository.job.CollectedJobRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** CollectedJobService.deleteExpired 통합 테스트 (H2 인메모리 DB 사용) */
@SpringBootTest
@ActiveProfiles("test")
class CollectedJobCleanupTest {

    @Autowired
    private CollectedJobService collectedJobService;

    @Autowired
    private CollectedJobRepository collectedJobRepository;

    @AfterEach
    void cleanUp() {
        collectedJobRepository.deleteAll();
    }

    private CollectedJob saveJob(String jobKey, LocalDate deadline) {
        CollectedJob job = new CollectedJob();
        job.setJobKey(jobKey);
        job.setCompany("회사");
        job.setTitle("제목");
        job.setUrl("https://example.com/" + jobKey);
        job.setSource("잡코리아");
        job.setDeadline(deadline);
        return collectedJobRepository.save(job);
    }

    @Test
    void 마감_8일_지난_공고는_삭제된다() {
        CollectedJob expired = saveJob("expired", LocalDate.now().minusDays(8));

        int deleted = collectedJobService.deleteExpired(7);

        assertThat(deleted).isEqualTo(1);
        assertThat(collectedJobRepository.findById(expired.getId())).isEmpty();
    }

    @Test
    void 마감_6일_지난_공고는_유지된다() {
        CollectedJob recent = saveJob("recent", LocalDate.now().minusDays(6));

        int deleted = collectedJobService.deleteExpired(7);

        assertThat(deleted).isEqualTo(0);
        assertThat(collectedJobRepository.findById(recent.getId())).isPresent();
    }

    @Test
    void 마감일이_없는_상시채용_공고는_유지된다() {
        CollectedJob alwaysOpen = saveJob("always-open", null);

        int deleted = collectedJobService.deleteExpired(7);

        assertThat(deleted).isEqualTo(0);
        assertThat(collectedJobRepository.findById(alwaysOpen.getId())).isPresent();
    }
}
