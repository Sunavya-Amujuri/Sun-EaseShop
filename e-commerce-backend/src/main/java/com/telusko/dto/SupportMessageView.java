package com.telusko.dto;

import java.time.LocalDateTime;

public record SupportMessageView(
        Long id,
        boolean fromAdmin,
        String content,
        LocalDateTime createdAt
) {}
