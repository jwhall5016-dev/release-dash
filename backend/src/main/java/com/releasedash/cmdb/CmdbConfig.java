package com.releasedash.cmdb;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({CmdbProperties.class, FoobarCmdbProperties.class})
public class CmdbConfig {
}
