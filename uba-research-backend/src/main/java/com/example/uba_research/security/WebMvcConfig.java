package com.example.uba_research.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AnomalyDetectionInterceptor anomalyDetectionInterceptor;

    public WebMvcConfig(AnomalyDetectionInterceptor anomalyDetectionInterceptor) {
        this.anomalyDetectionInterceptor = anomalyDetectionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(anomalyDetectionInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/api/auth/**");
    }
}