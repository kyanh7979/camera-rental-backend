package com.camerarental.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthenticationEventListener {

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        Object principal = event.getAuthentication().getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            log.info("=== AUTH SUCCESS ===");
            log.info("User: {}", userDetails.getUsername());
            log.info("Authorities: {}", userDetails.getAuthorities());
        } else {
            log.info("=== AUTH SUCCESS ===");
            log.info("Principal: {}", principal);
        }
    }

    @EventListener
    public void onAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event) {
        String email = event.getAuthentication().getName();
        String credentials = event.getAuthentication().getCredentials().toString();
        
        log.warn("=== AUTH FAILURE - BAD CREDENTIALS ===");
        log.warn("Email: {}", email);
        log.warn("Credentials length: {}", credentials != null ? credentials.length() : 0);
        log.warn("Exception: {}", event.getException().getMessage());
        
        if (event.getException().getCause() != null) {
            log.warn("Cause: {}", event.getException().getCause().getMessage());
        }
    }
}
