package com.releasedash.foobar;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FoobarJfrogProperties.class)
public class FoobarJfrogConfig {
}
