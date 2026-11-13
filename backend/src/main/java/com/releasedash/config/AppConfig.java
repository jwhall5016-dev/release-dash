package com.releasedash.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AppConfig {
    
    @Value("${jfrog.url}")
    private String jfrogUrl;
    
    @Value("${jfrog.username}")
    private String jfrogUsername;
    
    @Value("${jfrog.token}")
    private String jfrogToken;
    
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
    
    public String getJfrogUrl() {
        return jfrogUrl;
    }
    
    public String getJfrogUsername() {
        return jfrogUsername;
    }
    
    public String getJfrogToken() {
        return jfrogToken;
    }
}
