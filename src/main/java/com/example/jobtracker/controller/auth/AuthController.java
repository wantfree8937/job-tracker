package com.example.jobtracker.controller.auth;

import com.example.jobtracker.dto.auth.KeywordsRequest;
import com.example.jobtracker.dto.auth.LoginRequest;
import com.example.jobtracker.dto.auth.ProfileRequest;
import com.example.jobtracker.dto.auth.ProfileResponse;
import com.example.jobtracker.dto.auth.ProfileTextResponse;
import com.example.jobtracker.dto.auth.ProfileUrlRequest;
import com.example.jobtracker.dto.auth.SignUpRequest;
import com.example.jobtracker.dto.auth.TokenResponse;
import com.example.jobtracker.dto.auth.UserResponse;
import com.example.jobtracker.service.auth.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping("/me/profile/parse-url")
    public ResponseEntity<ProfileTextResponse> parseProfileUrl(@Valid @RequestBody ProfileUrlRequest request) {
        return ResponseEntity.ok(authService.parseProfileUrl(request.url()));
    }

    @PostMapping("/me/profile/parse-pdf")
    public ResponseEntity<ProfileTextResponse> parseProfilePdf(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(authService.parseProfilePdf(file));
    }
}
