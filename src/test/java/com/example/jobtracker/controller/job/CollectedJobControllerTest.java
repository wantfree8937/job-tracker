package com.example.jobtracker.controller.job;

import com.example.jobtracker.dto.auth.LoginRequest;
import com.example.jobtracker.dto.auth.SignUpRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** CollectedJobController API 통합 테스트 (H2 인메모리 DB 사용) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CollectedJobControllerTest {

    // 서비스가 읽는 실제 job_alerts.json 경로 (테스트 중엔 백업해뒀다가 끝나면 복원)
    private static final Path DATA_FILE = Path.of("C:\\Users\\pey21\\projects\\job-tracker\\data\\job_alerts.json");
    private static final Path BACKUP_FILE = DATA_FILE.resolveSibling("job_alerts.json.bak");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void backupExistingDataFile() throws Exception {
        if (Files.exists(DATA_FILE)) {
            Files.move(DATA_FILE, BACKUP_FILE, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @AfterAll
    static void restoreDataFile() throws Exception {
        Files.deleteIfExists(DATA_FILE);
        if (Files.exists(BACKUP_FILE)) {
            Files.move(BACKUP_FILE, DATA_FILE, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @AfterEach
    void cleanUpDataFile() throws Exception {
        Files.deleteIfExists(DATA_FILE);
    }

    private String signUpAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SignUpRequest(email, "password123", "닉네임"))));

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "password123"))))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private void writeAlerts(String json) throws Exception {
        // CI(Linux)에서는 하드코딩된 윈도우 경로가 구분자 없는 단일 세그먼트로 해석돼 getParent()가 null → 그때만 스킵
        Path parent = DATA_FILE.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(DATA_FILE, json);
    }

    // ① 로그인 없이 접근하면 401
    @Test
    void listWithoutTokenTest() throws Exception {
        mockMvc.perform(get("/api/jobs/collected"))
                .andExpect(status().isUnauthorized());
    }

    // ② 파일 로드 성공 (신규 jobKey → loaded 증가)
    @Test
    void loadFromFileTest() throws Exception {
        String token = signUpAndLogin("load@test.com");
        String jobKey = "test:" + UUID.randomUUID();
        writeAlerts("""
                [{"company":"네이버","title":"백엔드 개발자","url":"https://naver.com/1","source":"잡코리아","jobKey":"%s"}]
                """.formatted(jobKey));

        mockMvc.perform(post("/api/jobs/collected/load").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loaded").value(1))
                .andExpect(jsonPath("$.skipped").value(0));
    }

    // ③ 같은 jobKey로 다시 로드하면 skipped 증가
    @Test
    void loadDuplicateSkipTest() throws Exception {
        String token = signUpAndLogin("load-dup@test.com");
        String json = """
                [{"company":"카카오","title":"프론트엔드","url":"https://kakao.com/1","source":"잡코리아","jobKey":"test:%s"}]
                """.formatted(UUID.randomUUID());

        writeAlerts(json);
        mockMvc.perform(post("/api/jobs/collected/load").header("Authorization", "Bearer " + token));

        writeAlerts(json);
        mockMvc.perform(post("/api/jobs/collected/load").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loaded").value(0))
                .andExpect(jsonPath("$.skipped").value(1));
    }

    // ④ 목록 조회 + keyword/source 필터
    @Test
    void listAndFilterTest() throws Exception {
        String token = signUpAndLogin("list@test.com");
        String uniqueSource = "출처-" + UUID.randomUUID();
        writeAlerts("""
                [{"company":"Toss Bank","title":"Backend Engineer","url":"https://toss.im/1","source":"%s","jobKey":"test:%s"},
                 {"company":"당근마켓","title":"프론트엔드","url":"https://daangn.com/1","source":"기타출처","jobKey":"test:%s"}]
                """.formatted(uniqueSource, UUID.randomUUID(), UUID.randomUUID()));
        mockMvc.perform(post("/api/jobs/collected/load").header("Authorization", "Bearer " + token));

        mockMvc.perform(get("/api/jobs/collected").param("source", uniqueSource)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].company").value("Toss Bank"));

        mockMvc.perform(get("/api/jobs/collected").param("keyword", "toss")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].company").value("Toss Bank"));
    }

    // ⑤ 스크랩 → 내 공고 목록에 생성, 중복 스크랩은 409
    @Test
    void scrapTest() throws Exception {
        String token = signUpAndLogin("scrap@test.com");
        String uniqueTitle = "스크랩테스트-" + UUID.randomUUID();
        writeAlerts("""
                [{"company":"라인","title":"%s","url":"https://line.me/1","source":"잡코리아","jobKey":"test:%s"}]
                """.formatted(uniqueTitle, UUID.randomUUID()));
        mockMvc.perform(post("/api/jobs/collected/load").header("Authorization", "Bearer " + token));

        String listResponse = mockMvc.perform(get("/api/jobs/collected").param("keyword", uniqueTitle)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(listResponse).get(0).get("id").asLong();

        mockMvc.perform(post("/api/jobs/collected/" + id + "/scrap").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyName").value("라인"));

        mockMvc.perform(get("/api/jobs").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].companyName").value("라인"));

        mockMvc.perform(post("/api/jobs/collected/" + id + "/scrap").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    // ⑥ SSRF 차단: localhost 접근 시 400
    @Test
    void previewLocalhostBlockedTest() throws Exception {
        String token = signUpAndLogin("preview-local@test.com");
        mockMvc.perform(post("/api/jobs/preview")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"link":"http://localhost:8080"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ⑦ http/https가 아닌 링크는 400
    @Test
    void previewInvalidSchemeTest() throws Exception {
        String token = signUpAndLogin("preview-scheme@test.com");
        mockMvc.perform(post("/api/jobs/preview")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"link":"ftp://example.com/file"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ⑧ mine=true: 관심 키워드와 매칭되는 공고만 반환
    @Test
    void mineTrueFiltersByKeywordTest() throws Exception {
        String token = signUpAndLogin("mine@test.com");
        String uniqueCompany = "매칭회사-" + UUID.randomUUID();
        String otherCompany = "무관회사-" + UUID.randomUUID();
        writeAlerts("""
                [{"company":"%s","title":"안드로이드 개발자","url":"https://mine.com/1","source":"잡코리아","jobKey":"test:%s"},
                 {"company":"%s","title":"디자이너","url":"https://mine.com/2","source":"잡코리아","jobKey":"test:%s"}]
                """.formatted(uniqueCompany, UUID.randomUUID(), otherCompany, UUID.randomUUID()));
        mockMvc.perform(post("/api/jobs/collected/load").header("Authorization", "Bearer " + token));

        mockMvc.perform(put("/api/auth/me/keywords")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"keywords":["안드로이드"]}
                        """));

        mockMvc.perform(get("/api/jobs/collected").param("mine", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.company=='" + uniqueCompany + "')]").exists())
                .andExpect(jsonPath("$[?(@.company=='" + otherCompany + "')]").doesNotExist());
    }

    // ⑨ mine=true + 키워드 미설정 시 전체 반환
    @Test
    void mineTrueWithoutKeywordsReturnsAllTest() throws Exception {
        String token = signUpAndLogin("mine-empty@test.com");
        String uniqueSource = "출처-" + UUID.randomUUID();
        writeAlerts("""
                [{"company":"아무회사","title":"아무직무","url":"https://mine-empty.com/1","source":"%s","jobKey":"test:%s"}]
                """.formatted(uniqueSource, UUID.randomUUID()));
        mockMvc.perform(post("/api/jobs/collected/load").header("Authorization", "Bearer " + token));

        mockMvc.perform(get("/api/jobs/collected").param("mine", "true").param("source", uniqueSource)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
