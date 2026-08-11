package com.example.jobtracker.controller.auth;

import com.example.jobtracker.dto.auth.KeywordsRequest;
import com.example.jobtracker.dto.auth.LoginRequest;
import com.example.jobtracker.dto.auth.ProfileFileResponse;
import com.example.jobtracker.dto.auth.ProfileRequest;
import com.example.jobtracker.dto.auth.ProfileResponse;
import com.example.jobtracker.dto.auth.ProfileTextResponse;
import com.example.jobtracker.dto.auth.ResumeFileData;
import com.example.jobtracker.dto.auth.SignUpRequest;
import com.example.jobtracker.dto.auth.TokenResponse;
import com.example.jobtracker.dto.auth.UserResponse;
import com.example.jobtracker.service.auth.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signUp(request));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        return ResponseEntity.ok(authService.getMyInfo(authentication.getName()));
    }

    @PutMapping("/me/keywords")
    public ResponseEntity<UserResponse> updateKeywords(Authentication authentication,
                                                         @Valid @RequestBody KeywordsRequest request) {
        return ResponseEntity.ok(authService.updateKeywords(authentication.getName(), request));
    }

    @GetMapping("/me/profile")
    public ResponseEntity<ProfileResponse> getProfile(Authentication authentication) {
        return ResponseEntity.ok(authService.getProfile(authentication.getName()));
    }

    @PutMapping("/me/profile")
    public ResponseEntity<ProfileResponse> updateProfile(Authentication authentication,
                                                           @Valid @RequestBody ProfileRequest request) {
        return ResponseEntity.ok(authService.updateProfile(authentication.getName(), request));
    }

    @PostMapping("/me/profile/parse-pdf")
    public ResponseEntity<ProfileTextResponse> parseProfilePdf(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(authService.parseProfilePdf(file));
    }

    @PutMapping("/me/profile/file")
    public ResponseEntity<ProfileFileResponse> saveProfileFile(Authentication authentication,
                                                                 @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(authService.saveProfileFile(authentication.getName(), file));
    }

    @GetMapping("/me/profile/file")
    public ResponseEntity<ProfileFileResponse> getProfileFile(Authentication authentication) {
        ProfileFileResponse meta = authService.getProfileFile(authentication.getName());
        if (meta == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(meta);
    }

    @GetMapping("/me/profile/file/download")
    public ResponseEntity<byte[]> downloadProfileFile(Authentication authentication) {
        ResumeFileData file = authService.downloadProfileFile(authentication.getName());
        if (file == null) {
            return ResponseEntity.notFound().build();
        }

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        MediaType contentType = file.contentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(file.contentType());

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(file.data());
    }

    @DeleteMapping("/me/profile/file")
    public ResponseEntity<Void> deleteProfileFile(Authentication authentication) {
        authService.deleteProfileFile(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
