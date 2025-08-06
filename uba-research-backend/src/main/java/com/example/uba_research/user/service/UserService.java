package com.example.uba_research.user.service;

import com.example.uba_research.common.Metadata;
import com.example.uba_research.service.PythonAnalysisService;
import com.example.uba_research.user.User;
import com.example.uba_research.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.User.UserBuilder;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PythonAnalysisService analysisService;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        UserBuilder builder = org.springframework.security.core.userdetails.User.withUsername(username);
        builder.password(user.getPassword());
        builder.roles("USER");

        return builder.build();
    }

    public boolean checkAuthenticity(Object payload, Metadata metadata) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            
            // Create request object containing both payload and metadata
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("payload", payload);
            requestData.put("metadata", metadata);
            
            // Call Python analysis service
            Map<String, Object> analysisResult = analysisService.analyzeBehavior(requestData);
            
            // Check if the request is authentic based on analysis result
            Boolean isSuspicious = (Boolean) analysisResult.get("suspicious");
            Double confidence = (Double) analysisResult.get("confidence");
            
            // Return true if not suspicious and confidence is above threshold
            return !isSuspicious && confidence > 0.7;
            
        } catch (Exception e) {
            System.err.println("Authentication check failed: " + e.getMessage());
            return false; // Fail safe - reject if analysis fails
        }
    }
}