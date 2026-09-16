package com.telusko.config;

import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    /**
     * Lets Jackson serialise entities that still hold uninitialised Hibernate proxies.
     * <p>
     * Several controllers return JPA entities straight from the repository. Any lazy association
     * that was not loaded inside the transaction reaches Jackson as a proxy, and without this the
     * whole response fails with a {@code ByteBuddyInterceptor} type error - which is exactly what
     * {@code GET /users/me} did once a shopper had a cart.
     * <p>
     * Unloaded associations are written as {@code null} rather than being fetched, so rendering a
     * response can never quietly trigger extra queries.
     */
    @Bean
    public Hibernate6Module hibernate6Module() {
        Hibernate6Module module = new Hibernate6Module();
        module.disable(Hibernate6Module.Feature.FORCE_LAZY_LOADING);
        return module;
    }
}
