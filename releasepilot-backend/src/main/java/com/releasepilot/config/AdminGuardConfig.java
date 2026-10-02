package com.releasepilot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AdminGuardConfig implements WebMvcConfigurer {

    private final AdminTokenInterceptor adminTokenInterceptor;

    public AdminGuardConfig(AdminTokenInterceptor adminTokenInterceptor) {
        this.adminTokenInterceptor = adminTokenInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminTokenInterceptor).addPathPatterns("/api/release/**");
    }
}