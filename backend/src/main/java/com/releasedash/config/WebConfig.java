package com.releasedash.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Team sites served by this application. Each one gets /{team} and /{team}/api. */
    private static final String[] TEAMS = {"mentat", "foobar"};

    private static final String INDEX = "/static/spa/index.html";

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Temporary: the original site root was the Mentat dashboard.
        registry.addRedirectViewController("/", "/mentat");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String[] patterns = new String[TEAMS.length * 3];
        for (int i = 0; i < TEAMS.length; i++) {
            String base = "/" + TEAMS[i];
            patterns[i * 3] = base;
            patterns[i * 3 + 1] = base + "/";
            patterns[i * 3 + 2] = base + "/**";
        }
        registry.addResourceHandler(patterns)
            .addResourceLocations("classpath:/static/spa/")
            .resourceChain(true)
            .addResolver(new PathResourceResolver() {
                @Override
                protected Resource getResource(String resourcePath, Resource location) throws IOException {
                    String relative = stripUiPrefix(resourcePath);
                    // Team APIs under /{team}/api are controllers, not client routes.
                    if (relative.equals("api") || relative.startsWith("api/")) {
                        return null;
                    }
                    if (!relative.isEmpty() && !relative.endsWith("/")) {
                        Resource requestedResource = location.createRelative(relative);
                        if (requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        }
                        // Missing assets should 404. Client routes have no file extension.
                        if (relative.contains(".")) {
                            return null;
                        }
                    }

                    Resource indexResource = new ClassPathResource(INDEX);
                    if (indexResource.exists()) {
                        return indexResource;
                    }
                    return null;
                }
            });
    }

    static String stripUiPrefix(String resourcePath) {
        String path = resourcePath == null ? "" : resourcePath;
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        for (String team : TEAMS) {
            if (path.equals(team)) {
                return "";
            }
            String prefixed = team + "/";
            if (path.startsWith(prefixed)) {
                return path.substring(prefixed.length());
            }
        }
        return path;
    }
}
