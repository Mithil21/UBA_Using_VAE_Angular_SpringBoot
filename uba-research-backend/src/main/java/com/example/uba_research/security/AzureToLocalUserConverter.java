package com.example.uba_research.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.example.uba_research.user.User;
import com.example.uba_research.user.repository.UserRepository;

import jakarta.validation.constraints.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Component
public class AzureToLocalUserConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final UserRepository userRepository;

    public AzureToLocalUserConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Collection<GrantedAuthority> convert(@NotNull Jwt jwt) {
        String email = jwt.getClaim("preferred_username");
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        Set<GrantedAuthority> authorities = new HashSet<>();
        // for (String role : user.getRoles()) {
        //     authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        // }
         authorities.add(new SimpleGrantedAuthority("ROLE_USER")); // Example role assignment

        return authorities;
    }
}