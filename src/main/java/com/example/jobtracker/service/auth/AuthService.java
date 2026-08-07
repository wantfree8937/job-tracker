package com.example.jobtracker.service.auth;

import com.example.jobtracker.dto.auth.KeywordsRequest;
import com.example.jobtracker.dto.auth.LoginRequest;
import com.example.jobtracker.dto.auth.SignUpRequest;
import com.example.jobtracker.dto.auth.TokenResponse;
import com.example.jobtracker.dto.auth.UserResponse;
import com.example.jobtracker.entity.user.User;
import com.example.jobtracker.exception.EmailAlreadyExistsException;
import com.example.jobtracker.exception.InvalidCredentialsException;
import com.example.jobtracker.repository.user.UserRepository;
import com.example.jobtracker.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // 회원가입: 이메일 중복 확인 후 비밀번호를 암호화해 저장
    @Transactional
    public UserResponse signUp(SignUpRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());

        return toResponse(userRepository.save(user));
    }

    // 로그인: 이메일/비밀번호 확인 후 JWT 발급
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtUtil.generateToken(user.getEmail());
        return TokenResponse.of(token, jwtUtil.getExpirationMs());
    }

    // 내 정보 조회 (JWT에서 추출한 이메일 기준)
    @Transactional(readOnly = true)
    public UserResponse getMyInfo(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        return toResponse(user);
    }

    // 관심 분야 키워드 설정 (trim, 빈 값 제거 후 콤마 문자열로 저장, 빈 배열이면 초기화)
    @Transactional
    public UserResponse updateKeywords(String email, KeywordsRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        List<String> normalized = normalizeKeywords(request.keywords());
        user.setKeywords(normalized.isEmpty() ? null : String.join(",", normalized));
        return toResponse(user);
    }

    private List<String> normalizeKeywords(List<String> keywords) {
        if (keywords == null) {
            return List.of();
        }
        List<String> normalized = keywords.stream()
                .filter(k -> k != null && !k.isBlank())
                .map(String::trim)
                .toList();
        for (String k : normalized) {
            if (k.length() < 2 || k.length() > 20) {
                throw new IllegalArgumentException("키워드는 2~20자여야 합니다");
            }
        }
        return normalized;
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getNickname(), user.getCreatedAt(),
                parseKeywords(user.getKeywords()));
    }

    private List<String> parseKeywords(String keywords) {
        if (keywords == null || keywords.isBlank()) {
            return List.of();
        }
        return Arrays.asList(keywords.split(","));
    }
}
