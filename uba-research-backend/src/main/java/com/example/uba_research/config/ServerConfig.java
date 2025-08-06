package com.example.uba_research.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServerConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> containerCustomizer() {
        return factory -> {
            factory.addConnectorCustomizers(connector -> {
                connector.setMaxPostSize(10 * 1024 * 1024); // 10MB
                connector.setProperty("maxHttpHeaderSize", "65536"); // 64KB
            });
        };
    }
}