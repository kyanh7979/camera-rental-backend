package com.camerarental.controller;

import com.camerarental.dto.request.*;
import com.camerarental.dto.response.ApiResponse;
import com.camerarental.dto.response.AuthResponse;
import com.camerarental.entity.User;
import com.camerarental.repository.UserRepository;
import com.camerarental.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PutMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userDetails.getUsername(), request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        String message = authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(message, null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password has been reset", null));
    }

    /**
     * DEBUG ENDPOINT - Remove in production!
     * Test password encoding and matching directly
     */
    @PostMapping("/debug-password")
    public ResponseEntity<ApiResponse<Map<String, Object>>> debugPassword(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String rawPassword = payload.get("password");
        
        Map<String, Object> result = new HashMap<>();
        
        if (email == null || rawPassword == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Missing parameters"));
        }
        
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("User not found"));
        }
        
        String storedHash = user.getPassword();
        boolean matches = passwordEncoder.matches(rawPassword, storedHash);
        String newHash = passwordEncoder.encode(rawPassword);
        
        result.put("email", email);
        result.put("rawPasswordLength", rawPassword.length());
        result.put("storedHashFirst30Chars", storedHash != null ? storedHash.substring(0, Math.min(30, storedHash.length())) : "null");
        result.put("matches", matches);
        result.put("newHashFirst30Chars", newHash.substring(0, Math.min(30, newHash.length())));
        result.put("userId", user.getId());
        result.put("isActive", user.getIsActive());
        
        log.info("=== PASSWORD DEBUG ===");
        log.info("Email: {}, Matches: {}, RawLength: {}", email, matches, rawPassword.length());
        
        return ResponseEntity.ok(ApiResponse.success("Debug info", result));
    }

    /**
     * DEBUG ENDPOINT - Remove in production!
     * Reset password AND immediately test if login works
     */
    @PostMapping("/debug-reset-and-test")
    public ResponseEntity<ApiResponse<Map<String, Object>>> debugResetAndTest(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String newPassword = payload.get("newPassword");
        
        Map<String, Object> result = new HashMap<>();
        
        if (email == null || newPassword == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Missing parameters"));
        }
        
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("User not found"));
        }
        
        // Reset password
        String encodedPassword = passwordEncoder.encode(newPassword.trim());
        user.setPassword(encodedPassword);
        userRepository.save(user);
        userRepository.flush();
        
        result.put("step1_passwordReset", "success");
        result.put("step1_userId", user.getId());
        result.put("step1_newHash", encodedPassword.substring(0, Math.min(30, encodedPassword.length())));
        
        // Immediately test login
        try {
            User freshUser = userRepository.findByEmail(email).orElseThrow();
            boolean matches = passwordEncoder.matches(newPassword.trim(), freshUser.getPassword());
            
            result.put("step2_immediateMatch", matches);
            result.put("step2_storedHash", freshUser.getPassword().substring(0, Math.min(30, freshUser.getPassword().length())));
            
            if (matches) {
                result.put("result", "SUCCESS - Password reset and login test both passed!");
            } else {
                result.put("result", "FAILURE - Immediate test failed! Password hash not saved correctly.");
                log.error("PASSWORD BUG DETECTED! Hash mismatch immediately after save!");
                log.error("Expected hash to match, but it doesn't!");
            }
        } catch (Exception e) {
            result.put("step2_error", e.getMessage());
            result.put("result", "FAILURE - " + e.getMessage());
        }
        
        return ResponseEntity.ok(ApiResponse.success("Debug result", result));
    }

    /**
     * DEBUG ENDPOINT - Remove in production!
     * Delete user by email (to allow re-registration)
     */
    @DeleteMapping("/debug-delete-user")
    public ResponseEntity<ApiResponse<Map<String, Object>>> debugDeleteUser(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Email is required"));
        }
        
        User user = userRepository.findByEmail(email.trim()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("User not found"));
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("deletedUserId", user.getId());
        result.put("deletedEmail", user.getEmail());
        result.put("deletedAt", LocalDateTime.now());
        
        userRepository.delete(user);
        
        log.info("User deleted via debug endpoint: id={}, email={}", user.getId(), user.getEmail());
        
        return ResponseEntity.ok(ApiResponse.success("User deleted successfully. You can now register with this email.", result));
    }
}
