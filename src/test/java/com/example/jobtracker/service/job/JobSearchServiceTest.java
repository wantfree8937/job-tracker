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

    @Test
    void 잡코리아_검색_URL에_Page_No가_포함된다() {
        String url = JobSearchService.jobKoreaSearchUrl("네트워크", 2);

        assertThat(url).contains("Page_No=2");
        assertThat(url).contains("stext=");
        assertThat(url).contains("tabType=recruit&careerType=1");
    }

    @Test
    void 원티드_JSON을_파싱해_공고_리스트로_변환한다() {
        String json = """
                {"data":[
                    {"id":111,"position":"백엔드 개발자","company":{"name":"원티드랩"}},
                    {"id":222,"position":"프론트엔드 개발자","company":{"name":"토스"}}
                ]}
                """;

        List<CollectedJob> jobs = JobSearchService.parseWanted(json);

        assertThat(jobs).hasSize(2);
        assertThat(jobs.get(0).getJobKey()).isEqualTo("원티드:111");
        assertThat(jobs.get(0).getTitle()).isEqualTo("백엔드 개발자");
        assertThat(jobs.get(0).getCompany()).isEqualTo("원티드랩");
        assertThat(jobs.get(0).getUrl()).isEqualTo("https://www.wanted.co.kr/wd/111");
        assertThat(jobs.get(0).getSource()).isEqualTo("원티드");
    }

    @Test
    void 원티드_JSON이_잘못되면_빈_리스트를_반환한다() {
        List<CollectedJob> jobs = JobSearchService.parseWanted("이것은 JSON이 아닙니다");

        assertThat(jobs).isEmpty();
    }

    private static final String JOBKOREA_SAMPLE_HTML = """
            <div>목록 시작</div>
            <div data-sentry-component="CardJob">
                <a href="/Recruit/GI_Read/12345?Oem_Code=1">
                    <span class="text-typo-b1-18 text-gray900">백엔드 개발자</span>
                </a>
                <img alt="테크컴퍼니 로고" src="/logo1.png">
            </div>
            <div data-sentry-component="CardJob">
                <a href="/Recruit/GI_Read/67890?Oem_Code=1">
                    <span class="text-typo-b1-18 text-gray900">프론트엔드 개발자</span>
                </a>
                <img alt="스타트업㈜" src="/logo2.png">
            </div>
            """;

    @Test
    void 잡코리아_HTML을_파싱해_공고_리스트로_변환한다() {
        List<CollectedJob> jobs = JobSearchService.parseJobKorea(JOBKOREA_SAMPLE_HTML);

        assertThat(jobs).hasSize(2);
        assertThat(jobs.get(0).getJobKey()).isEqualTo("잡코리아:12345");
        assertThat(jobs.get(0).getTitle()).isEqualTo("백엔드 개발자");
        assertThat(jobs.get(0).getCompany()).isEqualTo("테크컴퍼니");
        assertThat(jobs.get(0).getUrl()).isEqualTo("https://www.jobkorea.co.kr/Recruit/GI_Read/12345");
        assertThat(jobs.get(1).getCompany()).isEqualTo("스타트업");
    }

    @Test
    void 잡코리아_HTML에_카드가_없으면_빈_리스트를_반환한다() {
        List<CollectedJob> jobs = JobSearchService.parseJobKorea("<div>공고가 없습니다</div>");

        assertThat(jobs).isEmpty();
    }
}
