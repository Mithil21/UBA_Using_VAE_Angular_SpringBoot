package com.example.uba_research.config;

import com.example.uba_research.security.AzureToLocalUserConverter;
import com.example.uba_research.security.StealthHeaderFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final StealthHeaderFilter stealthHeaderFilter;
    private final AzureToLocalUserConverter azureToLocalUserConverter;

    public SecurityConfig(StealthHeaderFilter stealthHeaderFilter, AzureToLocalUserConverter azureToLocalUserConverter) {
        this.stealthHeaderFilter = stealthHeaderFilter;
        this.azureToLocalUserConverter = azureToLocalUserConverter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(azureToLocalUserConverter);

        http
            .csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/public/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter)
                )
            );

        http.addFilterBefore(stealthHeaderFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}