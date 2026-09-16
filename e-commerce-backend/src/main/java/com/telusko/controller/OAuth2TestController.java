package com.telusko.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class OAuth2TestController {

    @GetMapping("/oauth2/test-success")
    public Map<String, String> oauth2Success(
            @RequestParam String accessToken,
            @RequestParam String refreshToken
    ) {
        // Just echo them back as JSON so you can copy-paste
        return Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken
        );
    }
}
