package com.example.jobtracker.service.job;

import com.example.jobtracker.entity.job.CollectedJob;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JobSearchServiceTest {

    private CollectedJob job(String title, String company) {
        CollectedJob job = new CollectedJob();
        job.setTitle(title);
        job.setCompany(company);
        return job;
    }

    @Test
    void 제목_또는_회사명에_키워드가_포함되면_남기고_둘_다_없으면_제외한다() {
        List<CollectedJob> jobs = List.of(
                job("네트워크 엔지니어 채용", "테크컴퍼니"),
                job("백엔드 개발자 채용", "네트워크컴퍼니"),
                job("디자이너 채용", "일반컴퍼니")
        );

        List<CollectedJob> result = JobSearchService.filterByTitleOrCompany(jobs, "네트워크");

        assertThat(result).extracting(CollectedJob::getTitle)
                .containsExactly("네트워크 엔지니어 채용", "백엔드 개발자 채용");
    }

    @Test
    void 대소문자를_무시하고_제목을_매칭한다() {
        List<CollectedJob> jobs = List.of(job("Backend Engineer", "회사"));

        List<CollectedJob> result = JobSearchService.filterByTitleOrCompany(jobs, "engineer");

        assertThat(result).hasSize(1);
    }
}
