package com.camerarental.security;

import com.camerarental.entity.User;
import com.camerarental.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
        
        log.debug("Loaded user for authentication: id={}, email={}, isActive={}", 
                user.getId(), user.getEmail(), user.getIsActive());
        log.debug("Stored password hash (first 30 chars): {}", 
                user.getPassword() != null ? user.getPassword().substring(0, Math.min(30, user.getPassword().length())) : "null");
        
        return new CustomUserDetails(user);
    }
}
