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
import com.example.jobtracker.entity.user.User;
import com.example.jobtracker.exception.EmailAlreadyExistsException;
import com.example.jobtracker.exception.InvalidCredentialsException;
import com.example.jobtracker.exception.ProfileParseFailedException;
import com.example.jobtracker.repository.user.UserRepository;
import com.example.jobtracker.security.JwtUtil;
import com.example.jobtracker.util.ResumeTextExtractor;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Pattern HTTP_SCHEME = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);
    private static final Set<String> ALLOWED_URL_HOSTS = Set.of(
            "notion.so", "www.notion.so", "notion.site", "www.notion.site",
            "github.com", "www.github.com", "raw.githubusercontent.com",
            "velog.io", "www.velog.io"
    );
    private static final Pattern VELOG_USERNAME = Pattern.compile("^/@([^/]+)");
    private static final String VELOG_POSTS_QUERY =
            "query Posts($username: String, $limit: Int) { posts(username: $username, limit: $limit) { title short_description } }";
    private static final RestClient VELOG_CLIENT = RestClient.create("https://api.velog.io/graphql");

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

        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new ProfileParseFailedException("올바르지 않은 URL입니다");
        }
        String host = uri.getHost();
        if (host == null || !ALLOWED_URL_HOSTS.contains(host.toLowerCase())) {
            throw new ProfileParseFailedException("노션/GitHub/벨로그 주소만 지원합니다");
        }

        // velog는 JS 렌더링 페이지라 정적 HTML(jsoup)로는 본문을 못 얻어서 velog API로 최근 글을 가져온다
        if (host.toLowerCase().endsWith("velog.io")) {
            return parseVelogProfile(uri);
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
        return ResumeTextExtractor.truncate(doc.body().text());
    }

    // velog.io/@username 주소에서 username을 추출해 최근 글 5개(제목+요약)를 velog API로 가져온다
    private ProfileTextResponse parseVelogProfile(URI uri) {
        String username = extractVelogUsername(uri);
        if (username == null) {
            throw new ProfileParseFailedException("벨로그 프로필 주소(velog.io/@username)가 아닙니다");
        }

        String responseBody;
        try {
            responseBody = VELOG_CLIENT.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "query", VELOG_POSTS_QUERY,
                            "variables", Map.of("username", username, "limit", 5)
                    ))
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            throw new ProfileParseFailedException("velog 글을 가져올 수 없습니다");
        }

        return new ProfileTextResponse(buildVelogText(responseBody));
    }

    // velog.io/@username 형태의 경로에서 username을 추출한다
    static String extractVelogUsername(URI uri) {
        String path = uri.getPath();
        if (path == null) {
            return null;
        }
        Matcher matcher = VELOG_USERNAME.matcher(path);
        return matcher.find() ? matcher.group(1) : null;
    }

    // velog GraphQL 응답(data.posts[].title/short_description)에서 텍스트를 조합한다 (실제 호출 없이 파싱만 테스트하기 위해 분리)
    static String buildVelogText(String responseBody) {
        JsonNode posts;
        try {
            posts = new ObjectMapper().readTree(responseBody).path("data").path("posts");
        } catch (Exception e) {
            throw new ProfileParseFailedException("velog 글을 가져올 수 없습니다");
        }
        if (!posts.isArray() || posts.isEmpty()) {
            throw new ProfileParseFailedException("velog 글을 가져올 수 없습니다");
        }

        StringBuilder sb = new StringBuilder();
        posts.forEach(post -> sb.append(post.path("title").asText())
                .append("\n")
                .append(post.path("short_description").asText())
                .append("\n\n"));
        return ResumeTextExtractor.truncate(sb.toString());
    }

    // 이력서 파일(PDF/PPT/PPTX)에서 텍스트 추출 (최대 5000자)
    public ProfileTextResponse parseProfilePdf(MultipartFile file) {
        return new ProfileTextResponse(ResumeTextExtractor.extractResumeText(file));
    }

    // 이력서 원본 파일 저장 + 텍스트 추출 (다운로드용 원본은 DB에 그대로 보관)
    @Transactional
    public ProfileFileResponse saveProfileFile(String email, MultipartFile file) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        String text = ResumeTextExtractor.extractResumeText(file);
        try {
            user.setResumeFile(file.getBytes());
        } catch (IOException e) {
            throw new ProfileParseFailedException("파일을 읽을 수 없습니다");
        }
        user.setResumeFileName(file.getOriginalFilename());
        user.setResumeFileType(file.getContentType());

        return new ProfileFileResponse(user.getResumeFileName(), user.getResumeFileType(), text);
    }

    // 이력서 원본 파일 메타 조회 (저장된 파일이 없으면 null)
    @Transactional(readOnly = true)
    public ProfileFileResponse getProfileFile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        if (user.getResumeFile() == null) {
            return null;
        }
        return new ProfileFileResponse(user.getResumeFileName(), user.getResumeFileType(), null);
    }

    // 이력서 원본 파일 다운로드 (저장된 파일이 없으면 null)
    @Transactional(readOnly = true)
    public ResumeFileData downloadProfileFile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        if (user.getResumeFile() == null) {
            return null;
        }
        return new ResumeFileData(user.getResumeFileName(), user.getResumeFileType(), user.getResumeFile());
    }

    // 이력서 원본 파일 삭제
    @Transactional
    public void deleteProfileFile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        user.setResumeFileName(null);
        user.setResumeFileType(null);
        user.setResumeFile(null);
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
