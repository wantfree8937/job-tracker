package com.example.jobtracker.controller.auth;

import com.example.jobtracker.dto.auth.KeywordsRequest;
import com.example.jobtracker.dto.auth.LoginRequest;
import com.example.jobtracker.dto.auth.ProfileRequest;
import com.example.jobtracker.dto.auth.ProfileResponse;
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
}
