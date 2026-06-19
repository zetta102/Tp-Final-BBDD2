package com.bbdd.tp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configures Spring Boot 4 / Spring Framework 7 native API versioning.
 *
 * <p>Maps API versions from the path segment at index 1 (e.g., {@code /api/1.0/...})
 * and registers versions {@code 1.0}, {@code 2.0}, and {@code 3.0} as supported,
 * corresponding to the three provisioning strategies exposed by the controller.</p>
 */
@Configuration
public class WebVersioningConfig implements WebMvcConfigurer {

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer
                .addSupportedVersions("1.0", "2.0", "3.0")
                .setDefaultVersion("1.0")
                .usePathSegment(1); // Maps from segment index 1 (/api/{version}/...)
    }
}