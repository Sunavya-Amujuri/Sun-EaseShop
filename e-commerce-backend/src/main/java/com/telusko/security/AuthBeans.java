package com.telusko.security;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.telusko.enums.Role;
import com.telusko.model.User;
import com.telusko.repository.UserRepository;

@Configuration
public class AuthBeans {

    private final UserRepository userRepository;

    public AuthBeans(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService uds, PasswordEncoder pe) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(uds);
        provider.setPasswordEncoder(pe);
        return new ProviderManager(provider);
    }

    @Bean
    CommandLineRunner initDefaultAdmin(PasswordEncoder passwordEncoder) {
        return args -> {
            String adminEmail = "admin@gmail.com";

            if (!userRepository.existsByEmail(adminEmail)) {
                User admin = User.builder()
                        .fullName("Admin")
                        .email(adminEmail)
                        .password(passwordEncoder.encode("admin123"))
                        .phoneNumber("1234567890")
                        .role(Role.ADMIN)
                        .enabled(true)
                        .emailVerified(true)
                        .build();

                userRepository.save(admin);
            }
        };
    }
}
