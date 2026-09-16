package com.telusko.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import com.telusko.enums.Role;
import com.telusko.model.User;
import com.telusko.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest)
            throws OAuth2AuthenticationException {

        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId(); // "github"
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email;
        String name;

        if ("github".equalsIgnoreCase(registrationId)) {
            email = (String) attributes.get("email");
            String login = (String) attributes.get("login");
            String rawName = (String) attributes.get("name");

            if (email == null || email.isBlank()) {
                email = login + "@github.local";
            }

            if (rawName != null && !rawName.isBlank()) {
                name = rawName;
            } else if (login != null && !login.isBlank()) {
                name = login;
            } else {
                name = "GitHub User";
            }
        } else {
            throw new OAuth2AuthenticationException("Unsupported OAuth2 provider: " + registrationId);
        }

        String finalEmail = email;
        User user = users.findByEmail(email).orElseGet(() -> {
            User u = new User();
            u.setFullName(name);
            u.setEmail(finalEmail);
            u.setPassword(encoder.encode(UUID.randomUUID().toString()));
            u.setPhoneNumber("0000000000");
            u.setRole(Role.USER);
            u.setEnabled(true);
            u.setEmailVerified(true);
            return users.save(u);
        });

        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                Map.of(
                        "id", user.getId(),
                        "email", user.getEmail(),
                        "name", user.getFullName()
                ),
                "email"
        );
    }
}
