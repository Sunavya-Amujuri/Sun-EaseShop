package com.telusko.service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.util.Base64;
@Service
@Slf4j
public class AIImageGeneratorService {

    private final ImageModel imageModel;

    public AIImageGeneratorService(ImageModel imageModel) {
        this.imageModel = imageModel;
    }

    public byte[] generateImage(String prompt) {
        try {
            // quality "medium" and no responseFormat on purpose: gpt-image-1 uses low/medium/high
            // rather than dall-e-3's "standard", and it rejects response_format entirely because
            // it always answers with base64.
            OpenAiImageOptions options = OpenAiImageOptions.builder()
                    .N(1)
                    .width(1024)
                    .height(1024)
                    .quality("medium")
                    .build();

            ImageResponse response = imageModel.call(new ImagePrompt(prompt, options));
            Image output = response.getResult().getOutput();

            // gpt-image-1 returns base64; dall-e style models return a URL. Handle both so the
            // configured model can change without touching this code.
            if (output.getB64Json() != null) {
                return Base64.getDecoder().decode(output.getB64Json());
            }

            if (output.getUrl() != null) {
                try (InputStream in = URI.create(output.getUrl()).toURL().openStream()) {
                    return in.readAllBytes();
                }
            }

            log.error("Image generation returned neither base64 nor a URL.");
            return null;

        } catch (Exception e) {
            log.error("Image generation failed: {}", e.getMessage());
            return null;
        }
    }
}
