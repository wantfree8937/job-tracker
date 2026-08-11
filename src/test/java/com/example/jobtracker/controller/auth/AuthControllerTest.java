package com.example.jobtracker.controller.auth;

import com.example.jobtracker.dto.auth.LoginRequest;
import com.example.jobtracker.dto.auth.SignUpRequest;
import com.example.jobtracker.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** AuthController API 통합 테스트 (H2 인메모리 DB 사용) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    private SignUpRequest signUpRequest(String email) {
        return new SignUpRequest(email, "password123", "닉네임");
    }

    private void signUp(String email) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signUpRequest(email))));
    }

    // ① 회원가입 성공
    @Test
    void signUpTest() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signUpRequest("signup@test.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("signup@test.com"))
                .andExpect(jsonPath("$.nickname").value("닉네임"));
    }

    // ② 이메일 중복 시 409
    @Test
    void signUpDuplicateEmailTest() throws Exception {
        signUp("dup@test.com");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signUpRequest("dup@test.com"))))
                .andExpect(status().isConflict());
    }

    // ③ 로그인 성공
    @Test
    void loginTest() throws Exception {
        signUp("login@test.com");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("login@test.com", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    // ④ 비밀번호가 틀리면 401
    @Test
    void loginWrongPasswordTest() throws Exception {
        signUp("wrongpw@test.com");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("wrongpw@test.com", "wrongpass"))))
                .andExpect(status().isUnauthorized());
    }

    // ⑤ 토큰 없이 /me 접근 시 401
    @Test
    void meWithoutTokenTest() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    // ⑥ 토큰이 있으면 /me 200
    @Test
    void meWithTokenTest() throws Exception {
        signUp("me@test.com");
        String token = jwtUtil.generateToken("me@test.com");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@test.com"));
    }

    // ⑦ 위조 토큰으로 /me 접근 시 401 (보안: 변조된 토큰 거부)
    @Test
    void meWithForgedTokenTest() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer fake.token.value"))
                .andExpect(status().isUnauthorized());
    }

    // ⑧ 관심 키워드 설정 후 /me 조회 시 반영 확인
    @Test
    void updateKeywordsTest() throws Exception {
        signUp("keywords@test.com");
        String token = jwtUtil.generateToken("keywords@test.com");

        mockMvc.perform(put("/api/auth/me/keywords")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keywords":["안드로이드", "백엔드"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keywords[0]").value("안드로이드"))
                .andExpect(jsonPath("$.keywords[1]").value("백엔드"));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keywords.length()").value(2));
    }

    // ⑨ 빈 배열로 설정하면 관심 키워드 초기화
    @Test
    void updateKeywordsEmptyTest() throws Exception {
        signUp("keywords-empty@test.com");
        String token = jwtUtil.generateToken("keywords-empty@test.com");

        mockMvc.perform(put("/api/auth/me/keywords")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"keywords":["안드로이드"]}
                        """));

        mockMvc.perform(put("/api/auth/me/keywords")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keywords":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keywords.length()").value(0));
    }

    // ⑩ 이력서 저장 후 조회 시 반영 확인
    @Test
    void updateProfileTest() throws Exception {
        signUp("profile@test.com");
        String token = jwtUtil.generateToken("profile@test.com");

        mockMvc.perform(put("/api/auth/me/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"profileText":"3년차 백엔드 개발자입니다."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileText").value("3년차 백엔드 개발자입니다."));

        mockMvc.perform(get("/api/auth/me/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileText").value("3년차 백엔드 개발자입니다."));
    }

    // ⑪ 5000자 초과 시 400
    @Test
    void updateProfileTooLongTest() throws Exception {
        signUp("profile-long@test.com");
        String token = jwtUtil.generateToken("profile-long@test.com");
        String tooLong = "a".repeat(5001);

        mockMvc.perform(put("/api/auth/me/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("profileText", tooLong))))
                .andExpect(status().isBadRequest());
    }

    // ⑫ http/https가 아닌 URL 파싱 시 400
    @Test
    void parseProfileUrlInvalidSchemeTest() throws Exception {
        signUp("parse-url@test.com");
        String token = jwtUtil.generateToken("parse-url@test.com");

        mockMvc.perform(post("/api/auth/me/profile/parse-url")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"ftp://example.com"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ⑬ PDF가 아닌 파일 업로드 시 400
    @Test
    void parseProfilePdfNotPdfTest() throws Exception {
        signUp("parse-pdf@test.com");
        String token = jwtUtil.generateToken("parse-pdf@test.com");
        MockMultipartFile file = new MockMultipartFile("file", "resume.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/auth/me/profile/parse-pdf", file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    // ⑭ 토큰 없이 parse-pdf 접근 시 401
    @Test
    void parseProfilePdfWithoutTokenTest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", "hello".getBytes());

        mockMvc.perform(multipart("/api/auth/me/profile/parse-pdf", file))
                .andExpect(status().isUnauthorized());
    }
}
