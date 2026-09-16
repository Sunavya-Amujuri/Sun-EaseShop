package com.telusko.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtFilter;
    private final AuthEntryPointJwt authEntryPointJwt;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);
        http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.exceptionHandling(e -> e.authenticationEntryPoint(authEntryPointJwt));

        http.authorizeHttpRequests(auth -> auth
                // -------- PUBLIC --------
                .requestMatchers(
                        "/api/v1/users/register",
                        "/api/v1/users/login",
                        "/api/v1/users/verify-email",
                        "/api/v1/users/resend-email-verification",
                        "/api/v1/users/forgot-password",
                        "/api/v1/users/reset-password",
                        "/api/v1/users/refresh-token",
                        "/index.html",

                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/v3/api-docs",
                        "/swagger-resources/**",
                        "/webjars/**",
                        "/favicon.ico",
                        "/error",

                        // OAuth2 endpoints
                        "/oauth2/**",
                        "/login/oauth2/**"
                ).permitAll()

                // The Docker HEALTHCHECK calls this without credentials.
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()

                // -------- REACT APP --------
                // Only relevant once the React build is packaged into the jar (see SpaConfig).
                // These are page loads, not data: the browser asks for /admin or /orders/12 before
                // any JavaScript is running, so there is no token to send yet. Letting the HTML
                // through costs nothing yet, because the page is empty until it calls the API - and
                // every /api/v1 rule below still guards that call. Each page also checks the signed
                // in user itself, which is what keeps /admin out of a shopper's hands.
                .requestMatchers(HttpMethod.GET,
                        "/", "/assets/**", "/favicon.ico", "/favicon.svg", "/vite.svg",
                        "/search", "/category/**", "/product/**",
                        "/login", "/register", "/verify-email", "/forgot-password", "/reset-password",
                        "/account", "/cart", "/checkout", "/orders", "/orders/**",
                        "/addresses", "/support", "/deals", "/admin"
                ).permitAll()

                .requestMatchers(HttpMethod.GET,
                        "/api/v1/ecommerce/products",
                        "/api/v1/ecommerce/products/**",
                        "/api/v1/ecommerce/categories",
                        "/api/v1/ecommerce/categories/**"
                ).permitAll()

                // -------- USER / ADMIN ROLES --------
                .requestMatchers("/api/v1/users/assign-role/**").hasRole("ADMIN")

                // Category CRUD
                .requestMatchers(HttpMethod.POST,   "/api/v1/ecommerce/categories/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH,  "/api/v1/ecommerce/categories/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/ecommerce/categories/**").hasRole("ADMIN")

                // Product CRUD (non-GET)
                .requestMatchers(HttpMethod.POST,   "/api/v1/ecommerce/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH,  "/api/v1/ecommerce/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/ecommerce/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/ecommerce/product/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/ecommerce/product/**").hasRole("ADMIN")


                // Coupons
                .requestMatchers(
                        "/api/v1/ecommerce/coupons/customer/**",
                        "/api/v1/ecommerce/coupons/c/**"
                ).authenticated()
                .requestMatchers(HttpMethod.POST,   "/api/v1/ecommerce/coupons").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,    "/api/v1/ecommerce/coupons").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH,  "/api/v1/ecommerce/coupons/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/ecommerce/coupons/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,    "/api/v1/ecommerce/coupons/**").hasRole("ADMIN")

                // Orders admin
                .requestMatchers(
                        "/api/v1/ecommerce/orders/list/admin/**",
                        "/api/v1/ecommerce/orders/status/**"
                ).hasRole("ADMIN")

                // ---------- SUPPORT TICKETS ----------
                // Customer support: create ticket, view own tickets/messages
                .requestMatchers(
                        "/api/v1/ecommerce/support/tickets/**"
                ).authenticated()

                // Admin support: manage all tickets
                .requestMatchers("/api/v1/ecommerce/ai/admin/**").hasRole("ADMIN")

                .requestMatchers("/api/v1/ecommerce/ai/**").authenticated()

                .requestMatchers("/api/v1/ecommerce/support/admin/**").hasRole("ADMIN")
                .requestMatchers(
                        "/api/v1/ecommerce/support/admin/**",
                        "/api/v1/ecommerce/support/ticket-assistant"
                ).hasRole("ADMIN")

                // Payments
                .requestMatchers("/api/v1/ecommerce/payments/**").authenticated()
                .requestMatchers("/api/v1/ecommerce/ai/**").authenticated()


                // Everything else requires authentication
                .anyRequest().authenticated()
        );

        // OAuth2 login (GitHub, etc.)
        // loginPage points straight at the GitHub authorization endpoint. Without it Spring
        // generates its own page at /login, and that filter runs before the DispatcherServlet - so
        // once the React build shares this origin, Spring's page would answer /login instead of the
        // app's own sign-in screen. Naming a page also skips a provider chooser we do not need,
        // since GitHub is the only provider.
        http.oauth2Login(oauth -> oauth
                .loginPage("/oauth2/authorization/github")
                .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                .successHandler(oAuth2LoginSuccessHandler)
        );

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
