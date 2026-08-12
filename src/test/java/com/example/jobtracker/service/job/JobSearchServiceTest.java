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

    @Test
    void 원티드_JSON에서_지역과_업종을_추출한다() {
        String json = """
                {"data":[
                    {"id":333,"position":"백엔드 개발자","company":{"name":"원티드랩","industry_name":"IT, 컨텐츠"},
                     "address":{"location":"서울","district":"종로구"},
                     "annual_from":3,"annual_to":5}
                ]}
                """;

        List<CollectedJob> jobs = JobSearchService.parseWanted(json);

        assertThat(jobs.get(0).getRegion()).isEqualTo("서울");
        assertThat(jobs.get(0).getIndustry()).isEqualTo("IT, 컨텐츠");
        assertThat(jobs.get(0).getExperience()).isNull();
    }

    @Test
    void 원티드_제목에서_경력_정보를_추출한다() {
        String json = """
                {"data":[
                    {"id":1,"position":"안드로이드 개발자 (3년 이상)","company":{"name":"A"}},
                    {"id":2,"position":"프로그래머(신입)","company":{"name":"B"}},
                    {"id":3,"position":"백엔드 개발자 (경력무관)","company":{"name":"C"}},
                    {"id":4,"position":"디자이너","company":{"name":"D"}},
                    {"id":5,"position":"안드로이드 시니어 개발자","company":{"name":"E"}}
                ]}
                """;

        List<CollectedJob> jobs = JobSearchService.parseWanted(json);

        assertThat(jobs.get(0).getExperience()).isEqualTo("3년 이상");
        assertThat(jobs.get(1).getExperience()).isEqualTo("신입");
        assertThat(jobs.get(2).getExperience()).isEqualTo("경력무관");
        assertThat(jobs.get(3).getExperience()).isNull();
        assertThat(jobs.get(4).getExperience()).isEqualTo("시니어");
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
    void 잡코리아_회사명의_HTML_엔티티를_디코딩한다() {
        String html = """
                <div>목록 시작</div>
                <div data-sentry-component="CardJob">
                    <a href="/Recruit/GI_Read/99999?Oem_Code=1">
                        <span class="text-typo-b1-18 text-gray900">MD&amp;영업 채용</span>
                    </a>
                    <img alt="F&amp;F 로고" src="/logo5.png">
                </div>
                """;

        List<CollectedJob> jobs = JobSearchService.parseJobKorea(html);

        assertThat(jobs.get(0).getTitle()).isEqualTo("MD&영업 채용");
        assertThat(jobs.get(0).getCompany()).isEqualTo("F&F");
    }

    @Test
    void 잡코리아_HTML에_카드가_없으면_빈_리스트를_반환한다() {
        List<CollectedJob> jobs = JobSearchService.parseJobKorea("<div>공고가 없습니다</div>");

        assertThat(jobs).isEmpty();
    }

    // 실측 GrayChip 구조: 1번째 칩=지역, 2번째 칩=업종 리스트(쉼표 구분), 지역-업종 사이에 태그가 끼어 있어도 매칭돼야 함
    private static final String JOBKOREA_SAMPLE_WITH_META = """
            <div>목록 시작</div>
            <div data-sentry-component="CardJob">
                <a href="/Recruit/GI_Read/54321?Oem_Code=1">
                    <span class="text-typo-b1-18 text-gray900">백엔드개발자</span>
                </a>
                <img alt="제이에스엘인재개발원㈜ 로고" src="/logo3.png">
                <span class="truncate text-gray900 text-typo-b4-14">대전 중구 외 5</span></span></div><div class="chip-wrap"><span class="truncate text-gray900 text-typo-b4-14">학원·어학원·교육원, IT컨설팅, 백엔드개발자</span>
                <span>경력무관 • 대졸↑ • 정규직</span>
            </div>
            """;

    @Test
    void 잡코리아_HTML에서_지역_경력_업종을_추출한다() {
        List<CollectedJob> jobs = JobSearchService.parseJobKorea(JOBKOREA_SAMPLE_WITH_META);

        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).getRegion()).isEqualTo("대전 중구");
        assertThat(jobs.get(0).getExperience()).isEqualTo("경력무관");
        assertThat(jobs.get(0).getIndustry()).isEqualTo("학원·어학원·교육원");
    }

    @Test
    void 기존_공고에_빈_지역이_있으면_새로_수집한_지역으로_채운다() {
        CollectedJob existing = job("백엔드 개발자", "테크컴퍼니");
        existing.setJobKey("잡코리아:12345");
        // region/experience/industry 미설정 (기존 DB의 빈 필드 상황 재현)

        CollectedJob incoming = job("백엔드 개발자", "테크컴퍼니");
        incoming.setJobKey("잡코리아:12345");
        incoming.setRegion("서울 강남구");
        incoming.setExperience("경력무관");
        incoming.setIndustry("IT");

        boolean updated = JobSearchService.fillBlankFields(existing, incoming);

        assertThat(updated).isTrue();
        assertThat(existing.getRegion()).isEqualTo("서울 강남구");
        assertThat(existing.getExperience()).isEqualTo("경력무관");
        assertThat(existing.getIndustry()).isEqualTo("IT");
    }

    @Test
    void 기존_공고에_이미_값이_있으면_덮어쓰지_않는다() {
        CollectedJob existing = job("백엔드 개발자", "테크컴퍼니");
        existing.setJobKey("잡코리아:12345");
        existing.setRegion("대전 중구");

        CollectedJob incoming = job("백엔드 개발자", "테크컴퍼니");
        incoming.setJobKey("잡코리아:12345");
        incoming.setRegion("서울 강남구");

        boolean updated = JobSearchService.fillBlankFields(existing, incoming);

        assertThat(updated).isFalse();
        assertThat(existing.getRegion()).isEqualTo("대전 중구");
    }

    @Test
    void 제목에서_경력_패턴을_추출해_백필한다() {
        String experience = JobSearchService.extractExperienceFromTitle("백엔드 개발자 (3년 이상)");

        assertThat(experience).isEqualTo("3년 이상");
    }

    @Test
    void 제목에_경력_패턴이_없으면_null을_반환한다() {
        String experience = JobSearchService.extractExperienceFromTitle("디자이너 채용");

        assertThat(experience).isNull();
    }

    @Test
    void 원티드_JSON에서_마감일을_추출한다() {
        String json = """
                {"data":[
                    {"id":1,"position":"백엔드 개발자","company":{"name":"A"},"due_time":"2026-08-31"},
                    {"id":2,"position":"프론트엔드 개발자","company":{"name":"B"},"due_time":null},
                    {"id":3,"position":"디자이너","company":{"name":"C"}}
                ]}
                """;

        List<CollectedJob> jobs = JobSearchService.parseWanted(json);

        assertThat(jobs.get(0).getDeadline()).isEqualTo(java.time.LocalDate.of(2026, 8, 31));
        assertThat(jobs.get(1).getDeadline()).isNull();
        assertThat(jobs.get(2).getDeadline()).isNull();
    }

    @Test
    void 잡코리아_HTML에서_마감일을_추출한다() {
        String html = """
                <div>목록 시작</div>
                <div data-sentry-component="CardJob">
                    <a href="/Recruit/GI_Read/22222?Oem_Code=1">
                        <span class="text-typo-b1-18 text-gray900">백엔드 개발자</span>
                    </a>
                    <img alt="테크컴퍼니 로고" src="/logo6.png">
                    <meta name="description" content="테크컴퍼니 채용 마감일 : 2026.10.09 백엔드 개발자">
                </div>
                <div data-sentry-component="CardJob">
                    <a href="/Recruit/GI_Read/33333?Oem_Code=1">
                        <span class="text-typo-b1-18 text-gray900">프론트엔드 개발자</span>
                    </a>
                    <img alt="스타트업 로고" src="/logo7.png">
                    <meta name="description" content="스타트업 채용 마감일 : 채용시까지 프론트엔드 개발자">
                </div>
                """;

        List<CollectedJob> jobs = JobSearchService.parseJobKorea(html);

        assertThat(jobs.get(0).getDeadline()).isEqualTo(java.time.LocalDate.of(2026, 10, 9));
        assertThat(jobs.get(1).getDeadline()).isNull();
    }

    @Test
    void 잡코리아_칩이_1개뿐이면_업종은_null이다() {
        String html = """
                <div>목록 시작</div>
                <div data-sentry-component="CardJob">
                    <a href="/Recruit/GI_Read/11111?Oem_Code=1">
                        <span class="text-typo-b1-18 text-gray900">백엔드개발자</span>
                    </a>
                    <img alt="테크컴퍼니 로고" src="/logo4.png">
                    <span class="truncate text-gray900 text-typo-b4-14">대전 중구</span>
                </div>
                """;

        List<CollectedJob> jobs = JobSearchService.parseJobKorea(html);

        assertThat(jobs.get(0).getIndustry()).isNull();
    }
}
