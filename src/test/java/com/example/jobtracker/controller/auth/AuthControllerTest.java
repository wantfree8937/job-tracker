package com.example.jobtracker.controller.auth;

import com.example.jobtracker.dto.auth.LoginRequest;
import com.example.jobtracker.dto.auth.SignUpRequest;
import com.example.jobtracker.security.JwtUtil;
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
}
