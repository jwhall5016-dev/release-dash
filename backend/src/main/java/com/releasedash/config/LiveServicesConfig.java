package com.releasedash.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LiveServicesProperties.class)
public class LiveServicesConfig {
}
