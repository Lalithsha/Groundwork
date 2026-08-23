package com.groundwork.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final AuditInterceptor auditInterceptor;

    public WebMvcConfig(RateLimitInterceptor rateLimitInterceptor, AuditInterceptor auditInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.auditInterceptor = auditInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
            .addPathPatterns("/api/chat/**", "/api/auth/login", "/api/auth/register", "/api/documents/upload");
        registry.addInterceptor(auditInterceptor).addPathPatterns("/api/**");
    }
}
