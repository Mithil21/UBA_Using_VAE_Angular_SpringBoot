package com.example.uba_research.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<RequestCachingFilter> requestCachingFilterRegistration() {
        FilterRegistrationBean<RequestCachingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestCachingFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(0);
        return registration;
    }
}