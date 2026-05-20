package com.camerarental.service.impl;

import com.camerarental.dto.request.*;
import com.camerarental.dto.response.AuthResponse;
import com.camerarental.dto.response.UserResponse;
import com.camerarental.entity.User;
import com.camerarental.exception.BadRequestException;
import com.camerarental.exception.DuplicateResourceException;
import com.camerarental.exception.ResourceNotFoundException;
import com.camerarental.repository.UserRepository;
import com.camerarental.security.CustomUserDetails;
import com.camerarental.security.JwtUtil;
import com.camerarental.service.AuthService;
import com.camerarental.service.EmailService;
import com.camerarental.service.TelegramBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final TelegramBotService telegramBotService;

    @Value("${app.base-url:http://localhost:5173}")
    private String baseUrl;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Attempt to register with existing email: {}", request.getEmail());
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }

        // Hash password trước khi lưu
        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .address(request.getAddress())
                .build();

        user = userRepository.save(user);
        log.info("New user registered: id={}, email={}", user.getId(), user.getEmail());

        // Gửi thông báo Telegram cho admin
        try {
            telegramBotService.sendNewUserNotification(user);
        } catch (Exception e) {
            log.warn("Failed to send Telegram notification for new user: {}", e.getMessage());
        }

        // Sinh token JWT
        CustomUserDetails userDetails = new CustomUserDetails(user);
        String token = jwtUtil.generateToken(userDetails);

        // Trả về AuthResponse nhưng **ẩn password**
        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .user(UserResponse.fromEntity(user)) // UserResponse không bao gồm password
                .build();
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String password = request.getPassword().trim();
        
        log.info("=== LOGIN ATTEMPT ===");
        log.info("Email: '{}'", email);
        log.info("Password length: {}", password.length());
        
        // First, manually check if user exists
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            log.warn("Login failed: User not found with email: {}", email);
            throw new BadRequestException("Email hoặc mật khẩu không đúng");
        }
        
        log.info("User found: id={}, email={}, isActive={}", user.getId(), user.getEmail(), user.getIsActive());
        log.info("Stored password hash (first 30): {}", 
                user.getPassword() != null ? user.getPassword().substring(0, Math.min(30, user.getPassword().length())) : "null");
        
        // Check if user is active
        if (user.getIsActive() != null && !user.getIsActive()) {
            log.warn("Login failed: User account is inactive: {}", email);
            throw new BadRequestException("Tài khoản đã bị vô hiệu hóa");
        }
        
        // Verify password manually
        boolean matches = passwordEncoder.matches(password, user.getPassword());
        log.info("Password verification result: {}", matches);
        
        if (!matches) {
            log.error("Login failed: Password mismatch for user: {}", email);
            throw new BadRequestException("Email hoặc mật khẩu không đúng");
        }
        
        // Generate JWT token
        CustomUserDetails userDetails = new CustomUserDetails(user);
        String token = jwtUtil.generateToken(userDetails);
        
        log.info("Login successful: email={}, userId={}", userDetails.getUser().getEmail(), userDetails.getUser().getId());

        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .user(UserResponse.fromEntity(userDetails.getUser()))
                .build();
    }

    @Override
    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed for user: id={}, email={}", user.getId(), user.getEmail());
    }

    @Override
    @Transactional
    public String forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            user.setResetPasswordToken(token);
            user.setResetPasswordExpiry(LocalDateTime.now().plusHours(1));
            userRepository.save(user);

            String resetLink = baseUrl + "/reset-password?token=" + token;
            emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
            log.info("Password reset email queued for: {}", user.getEmail());
        });
        // Luôn trả message giống nhau — không tiết lộ email có tồn tại hay không
        return "If that email exists in our system, a password reset link has been sent.";
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByResetPasswordToken(request.getToken())
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));

        if (user.getResetPasswordExpiry().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Reset token has expired");
        }

        String rawPassword = request.getNewPassword();
        
        if (rawPassword == null || rawPassword.trim().isEmpty()) {
            throw new BadRequestException("Password cannot be empty");
        }
        
        rawPassword = rawPassword.trim();
        
        String encodedPassword = passwordEncoder.encode(rawPassword);
        
        log.info("=== PASSWORD RESET DEBUG ===");
        log.info("Token: {}", request.getToken());
        log.info("User ID: {}, Email: {}", user.getId(), user.getEmail());
        log.info("Raw password length: {}", rawPassword.length());
        log.info("New encoded password (first 30): {}", encodedPassword.substring(0, Math.min(30, encodedPassword.length())));

        user.setPassword(encodedPassword);
        user.setResetPasswordToken(null);
        user.setResetPasswordExpiry(null);
        
        User savedUser = userRepository.save(user);
        userRepository.flush();
        
        log.info("User saved with new password. ID: {}, PasswordHash: {}", 
                savedUser.getId(), 
                savedUser.getPassword().substring(0, Math.min(20, savedUser.getPassword().length())));
        
        log.info("Password reset successfully for user: id={}, email={}", user.getId(), user.getEmail());
    }
}