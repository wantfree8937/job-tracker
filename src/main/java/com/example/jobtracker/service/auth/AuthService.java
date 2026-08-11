package com.example.jobtracker.service.auth;

import com.example.jobtracker.dto.auth.KeywordsRequest;
import com.example.jobtracker.dto.auth.LoginRequest;
import com.example.jobtracker.dto.auth.ProfileRequest;
import com.example.jobtracker.dto.auth.ProfileResponse;
import com.example.jobtracker.dto.auth.SignUpRequest;
import com.example.jobtracker.dto.auth.TokenResponse;
import com.example.jobtracker.dto.auth.UserResponse;
import com.example.jobtracker.dto.auth.ProfileTextResponse;
import com.example.jobtracker.entity.user.User;
import com.example.jobtracker.exception.EmailAlreadyExistsException;
import com.example.jobtracker.exception.InvalidCredentialsException;
import com.example.jobtracker.exception.ProfileParseFailedException;
import com.example.jobtracker.repository.user.UserRepository;
import com.example.jobtracker.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Pattern HTTP_SCHEME = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);
    private static final int PROFILE_TEXT_MAX_LENGTH = 5000;
    private static final Set<String> ALLOWED_URL_HOSTS = Set.of(
            "notion.so", "www.notion.so", "notion.site", "www.notion.site",
            "github.com", "www.github.com", "raw.githubusercontent.com",
            "velog.io", "www.velog.io"
    );
    private static final String PPTX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    private static final String PPT_CONTENT_TYPE = "application/vnd.ms-powerpoint";

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

    // URL 페이지에서 본문 텍스트 추출 (화이트리스트 도메인만 허용, script/style 제거, 최대 5000자)
    public ProfileTextResponse parseProfileUrl(String url) {
        if (!HTTP_SCHEME.matcher(url).find()) {
            throw new ProfileParseFailedException("http/https URL만 허용됩니다");
        }

        String host;
        try {
            host = URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            throw new ProfileParseFailedException("올바르지 않은 URL입니다");
        }
        if (host == null || !ALLOWED_URL_HOSTS.contains(host.toLowerCase())) {
            throw new ProfileParseFailedException("노션/GitHub/벨로그 주소만 지원합니다");
        }

        Document doc;
        try {
            doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; JobTrackerBot/1.0)")
                    .timeout(10_000)
                    .get();
        } catch (Exception e) {
            throw new ProfileParseFailedException("URL에서 텍스트를 가져올 수 없습니다");
        }

        String text = extractBodyText(doc);
        if (text.isBlank()) {
            // notion.so/notion.site는 JS 렌더링 페이지라 본문이 비어있는 경우가 많음
            throw new ProfileParseFailedException("이 페이지는 텍스트를 가져올 수 없습니다 (공개 설정 확인)");
        }
        return new ProfileTextResponse(text);
    }

    // script/style 태그를 제거한 본문 텍스트 추출 (최대 5000자)
    static String extractBodyText(Document doc) {
        doc.select("script, style").remove();
        return truncate(doc.body().text());
    }

    // 이력서 파일(PDF/PPT/PPTX)에서 텍스트 추출 (최대 5000자)
    public ProfileTextResponse parseProfilePdf(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ProfileParseFailedException("PDF/PPT/PPTX 파일만 업로드할 수 있습니다");
        }

        String contentType = file.getContentType();
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();

        try {
            if ("application/pdf".equals(contentType) || filename.endsWith(".pdf")) {
                return new ProfileTextResponse(extractPdfText(file));
            }
            if (PPTX_CONTENT_TYPE.equals(contentType) || filename.endsWith(".pptx")) {
                return new ProfileTextResponse(extractPptxText(file));
            }
            if (PPT_CONTENT_TYPE.equals(contentType) || filename.endsWith(".ppt")) {
                return new ProfileTextResponse(extractPptText(file));
            }
        } catch (Exception e) {
            throw new ProfileParseFailedException("파일에서 텍스트를 추출할 수 없습니다");
        }

        throw new ProfileParseFailedException("PDF/PPT/PPTX 파일만 업로드할 수 있습니다");
    }

    private static String extractPdfText(MultipartFile file) throws Exception {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            return truncate(new PDFTextStripper().getText(document));
        }
    }

    private static String extractPptxText(MultipartFile file) throws Exception {
        try (XMLSlideShow slideShow = new XMLSlideShow(file.getInputStream())) {
            StringBuilder sb = new StringBuilder();
            for (XSLFSlide slide : slideShow.getSlides()) {
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        sb.append(textShape.getText()).append('\n');
                    }
                }
            }
            return truncate(sb.toString());
        }
    }

    private static String extractPptText(MultipartFile file) throws Exception {
        try (HSLFSlideShow slideShow = new HSLFSlideShow(file.getInputStream())) {
            StringBuilder sb = new StringBuilder();
            for (HSLFSlide slide : slideShow.getSlides()) {
                for (List<HSLFTextParagraph> paragraphs : slide.getTextParagraphs()) {
                    sb.append(HSLFTextParagraph.getText(paragraphs)).append('\n');
                }
            }
            return truncate(sb.toString());
        }
    }

    private static String truncate(String text) {
        String trimmed = text == null ? "" : text.trim();
        return trimmed.length() > PROFILE_TEXT_MAX_LENGTH ? trimmed.substring(0, PROFILE_TEXT_MAX_LENGTH) : trimmed;
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
