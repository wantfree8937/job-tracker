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
    void 제목에_키워드가_포함된_공고만_남기고_회사명만_매칭되는_공고는_제외한다() {
        List<CollectedJob> jobs = List.of(
                job("네트워크 엔지니어 채용", "테크컴퍼니"),
                job("백엔드 개발자 채용", "네트워크컴퍼니"),
                job("네트워크 관리자", "네트워크컴퍼니")
        );

        List<CollectedJob> result = JobSearchService.filterByTitleKeyword(jobs, "네트워크");

        assertThat(result).extracting(CollectedJob::getTitle)
                .containsExactly("네트워크 엔지니어 채용", "네트워크 관리자");
    }

    @Test
    void 대소문자를_무시하고_제목을_매칭한다() {
        List<CollectedJob> jobs = List.of(job("Backend Engineer", "회사"));

        List<CollectedJob> result = JobSearchService.filterByTitleKeyword(jobs, "engineer");

        assertThat(result).hasSize(1);
    }
}
