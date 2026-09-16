package com.telusko.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the React build when it is packaged inside the jar.
 * <p>
 * In development the UI runs on its own Vite server and this class does nothing, because
 * {@code /static} holds no React build. The deployment image copies {@code dist/} in there, and
 * then the backend answers both the API and the storefront on one origin - which is what removes
 * CORS from the picture and gives the whole app a single URL.
 * <p>
 * The resolver exists because React Router owns paths the server knows nothing about. A shopper
 * who reloads {@code /orders/12} sends that path to Spring, which has no such mapping and would
 * answer 404. Returning {@code index.html} instead lets the browser start the app, and the app
 * then reads the path itself. API and infrastructure prefixes are deliberately excluded: a wrong
 * {@code /api} URL has to stay a 404 rather than quietly return a web page.
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    private static final String[] SERVER_OWNED = {
            "api/", "actuator/", "swagger-ui", "v3/api-docs", "oauth2/", "login/oauth2/", "error"
    };

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        for (String prefix : SERVER_OWNED) {
                            if (resourcePath.startsWith(prefix)) {
                                return null;
                            }
                        }
                        ClassPathResource index = new ClassPathResource("static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
