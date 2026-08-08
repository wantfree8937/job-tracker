package com.example.jobtracker.controller.job;

import com.example.jobtracker.dto.auth.LoginRequest;
import com.example.jobtracker.dto.auth.SignUpRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** JobPostingController API 통합 테스트 (H2 인메모리 DB 사용) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobPostingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // 회원가입 + 로그인으로 토큰을 발급받는 공통 헬퍼
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

    private String createJob(String token, String companyName, String position, String status) throws Exception {
        String body = """
                {"companyName":"%s","position":"%s","status":"%s"}
                """.formatted(companyName, position, status);

        return mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getContentAsString();
    }

    // ① 로그인 없이 접근하면 401
    @Test
    void listWithoutTokenTest() throws Exception {
        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isUnauthorized());
    }

    // ② 공고 생성 성공 (201 + 필드 확인)
    @Test
    void createJobTest() throws Exception {
        String token = signUpAndLogin("create@test.com");

        mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"네이버","position":"백엔드 개발자","link":"https://example.com","memo":"메모"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyName").value("네이버"))
                .andExpect(jsonPath("$.position").value("백엔드 개발자"))
                .andExpect(jsonPath("$.status").value("WISH"));
    }

    // ③ 내 공고 목록 조회 (다른 사용자 공고는 안 보임)
    @Test
    void listMyJobsOnlyTest() throws Exception {
        String myToken = signUpAndLogin("me-list@test.com");
        String otherToken = signUpAndLogin("other-list@test.com");

        createJob(myToken, "카카오", "프론트엔드", "WISH");
        createJob(otherToken, "라인", "백엔드", "WISH");

        mockMvc.perform(get("/api/jobs").header("Authorization", "Bearer " + myToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].companyName").value("카카오"));
    }

    // ④ status 필터: 해당 상태만 반환
    @Test
    void filterByStatusTest() throws Exception {
        String token = signUpAndLogin("filter-status@test.com");
        createJob(token, "쿠팡", "백엔드", "WISH");
        createJob(token, "배민", "백엔드", "INTERVIEW");

        mockMvc.perform(get("/api/jobs").param("status", "interview")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].companyName").value("배민"));
    }

    // ⑤ keyword 검색: 회사명/포지션 부분일치, 대소문자 무시
    @Test
    void searchByKeywordTest() throws Exception {
        String token = signUpAndLogin("keyword@test.com");
        createJob(token, "Toss Bank", "Backend Engineer", "WISH");
        createJob(token, "당근마켓", "프론트엔드", "WISH");

        mockMvc.perform(get("/api/jobs").param("keyword", "toss")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].companyName").value("Toss Bank"));
    }

    // ⑥ 상태 변경 (PATCH) → 200 + 변경 확인
    @Test
    void updateStatusTest() throws Exception {
        String token = signUpAndLogin("update@test.com");
        Long id = objectMapper.readTree(createJob(token, "당근", "백엔드", "WISH")).get("id").asLong();

        mockMvc.perform(patch("/api/jobs/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"APPLIED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"));
    }

    // ⑥-1 내 공고 상세 조회 성공
    @Test
    void findOneTest() throws Exception {
        String token = signUpAndLogin("find-one@test.com");
        Long id = objectMapper.readTree(createJob(token, "네이버", "백엔드", "WISH")).get("id").asLong();

        mockMvc.perform(get("/api/jobs/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("네이버"))
                .andExpect(jsonPath("$.position").value("백엔드"));
    }

    // ⑦ 다른 사용자 공고 접근 시 404 (상세/수정/삭제 공통)
    @Test
    void otherUsersJobNotFoundTest() throws Exception {
        String ownerToken = signUpAndLogin("owner@test.com");
        String attackerToken = signUpAndLogin("attacker@test.com");
        Long id = objectMapper.readTree(createJob(ownerToken, "회사", "포지션", "WISH")).get("id").asLong();

        mockMvc.perform(get("/api/jobs/" + id).header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isNotFound());
    }

    // ⑧ 삭제 → 204
    @Test
    void deleteJobTest() throws Exception {
        String token = signUpAndLogin("delete@test.com");
        Long id = objectMapper.readTree(createJob(token, "회사", "포지션", "WISH")).get("id").asLong();

        mockMvc.perform(delete("/api/jobs/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/jobs/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ⑨ 잘못된 status 값 → 400
    @Test
    void invalidStatusTest() throws Exception {
        String token = signUpAndLogin("invalid-status@test.com");

        mockMvc.perform(get("/api/jobs").param("status", "NOT_A_STATUS")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    // ⑩ 상태별 개수 집계 (없는 상태는 0)
    @Test
    void statsTest() throws Exception {
        String token = signUpAndLogin("stats@test.com");
        createJob(token, "회사1", "포지션", "WISH");
        createJob(token, "회사2", "포지션", "WISH");
        createJob(token, "회사3", "포지션", "INTERVIEW");

        mockMvc.perform(get("/api/jobs/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.WISH").value(2))
                .andExpect(jsonPath("$.INTERVIEW").value(1))
                .andExpect(jsonPath("$.APPLIED").value(0))
                .andExpect(jsonPath("$.OFFER").value(0))
                .andExpect(jsonPath("$.REJECTED").value(0));
    }
}
