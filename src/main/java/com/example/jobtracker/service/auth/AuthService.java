package com.example.jobtracker.service.auth;

import com.example.jobtracker.dto.auth.KeywordsRequest;
import com.example.jobtracker.dto.auth.LoginRequest;
import com.example.jobtracker.dto.auth.ProfileRequest;
import com.example.jobtracker.dto.auth.ProfileResponse;
import com.example.jobtracker.dto.auth.SignUpRequest;
import com.example.jobtracker.dto.auth.TokenResponse;
import com.example.jobtracker.dto.auth.UserResponse;
import com.example.jobtracker.dto.auth.ProfileTextResponse;
import com.example.jobtracker.dto.auth.ProfileFileResponse;
import com.example.jobtracker.dto.auth.ResumeFileData;
import com.example.jobtracker.entity.user.ResumeFile;
import com.example.jobtracker.entity.user.User;
import com.example.jobtracker.exception.EmailAlreadyExistsException;
import com.example.jobtracker.exception.InvalidCredentialsException;
import com.example.jobtracker.exception.ProfileParseFailedException;
import com.example.jobtracker.exception.ResumeFileNotFoundException;
import com.example.jobtracker.repository.user.ResumeFileRepository;
import com.example.jobtracker.repository.user.UserRepository;
import com.example.jobtracker.security.JwtUtil;
import com.example.jobtracker.util.ResumeTextExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_RESUME_FILES = 3;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final ResumeFileRepository resumeFileRepository;

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

    // 이력서/포트폴리오 조회
    @Transactional(readOnly = true)
    public ProfileResponse getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        return new ProfileResponse(user.getProfileText());
    }

    // 이력서/포트폴리오 저장
    @Transactional
    public ProfileResponse updateProfile(String email, ProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        user.setProfileText(request.profileText());
        return new ProfileResponse(user.getProfileText());
    }

    // 이력서 파일(PDF/PPT/PPTX)에서 텍스트 추출 (최대 5000자)
    public ProfileTextResponse parseProfilePdf(MultipartFile file) {
        return new ProfileTextResponse(ResumeTextExtractor.extractResumeText(file));
    }

    // 이력서 원본 파일 추가 저장 + 텍스트 추출 (최대 3개, 다운로드용 원본은 DB에 그대로 보관)
    @Transactional
    public ProfileFileResponse uploadProfileFile(String email, MultipartFile file) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        if (resumeFileRepository.countByUserId(user.getId()) >= MAX_RESUME_FILES) {
            throw new ProfileParseFailedException("이력서 파일은 최대 3개까지 저장할 수 있습니다");
        }

        String text = ResumeTextExtractor.extractResumeText(file);

        ResumeFile resumeFile = new ResumeFile();
        resumeFile.setUser(user);
        resumeFile.setFileName(file.getOriginalFilename());
        resumeFile.setFileType(file.getContentType());
        try {
            resumeFile.setContent(file.getBytes());
        } catch (IOException e) {
            throw new ProfileParseFailedException("파일을 읽을 수 없습니다");
        }
        resumeFileRepository.save(resumeFile);

        return new ProfileFileResponse(resumeFile.getId(), resumeFile.getFileName(), resumeFile.getFileType(), text);
    }

    // 이력서 원본 파일 목록 조회 (id + 파일명 + 타입만, 파일 내용은 제외)
    @Transactional(readOnly = true)
    public List<ProfileFileResponse> getProfileFiles(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        return resumeFileRepository.findByUserIdOrderByCreatedAtAsc(user.getId()).stream()
                .map(f -> new ProfileFileResponse(f.getId(), f.getFileName(), f.getFileType(), null))
                .toList();
    }

    // 이력서 원본 파일 다운로드 (없거나 내 소유가 아니면 예외)
    @Transactional(readOnly = true)
    public ResumeFileData downloadProfileFile(String email, Long fileId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        ResumeFile file = resumeFileRepository.findByIdAndUserId(fileId, user.getId())
                .orElseThrow(ResumeFileNotFoundException::new);
        return new ResumeFileData(file.getFileName(), file.getFileType(), file.getContent());
    }

    // 이력서 원본 파일 개별 삭제 (없거나 내 소유가 아니면 예외)
    @Transactional
    public void deleteProfileFile(String email, Long fileId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        long deleted = resumeFileRepository.deleteByIdAndUserId(fileId, user.getId());
        if (deleted == 0) {
            throw new ResumeFileNotFoundException();
        }
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
