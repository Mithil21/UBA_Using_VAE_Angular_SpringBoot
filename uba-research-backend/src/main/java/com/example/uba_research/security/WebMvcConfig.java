package com.example.uba_research.security;

import com.example.uba_research.security.filter.CachedBodyFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private RequestInterceptor requestInterceptor;

    @Autowired
    private CachedBodyFilter cachedBodyFilter;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestInterceptor);
    }

    @Bean
    public FilterRegistrationBean<CachedBodyFilter> cachedBodyFilterRegistration() {
        FilterRegistrationBean<CachedBodyFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(cachedBodyFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        return registration;
    }
}

