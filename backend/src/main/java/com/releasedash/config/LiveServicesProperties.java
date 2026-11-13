package com.releasedash.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "live-services")
public class LiveServicesProperties {

    private List<String> environments = new ArrayList<>();
    private List<Service> services = new ArrayList<>();

    public List<String> getEnvironments() {
        return environments;
    }

    public void setEnvironments(List<String> environments) {
        this.environments = environments;
    }

    public List<Service> getServices() {
        return services;
    }

    public void setServices(List<Service> services) {
        this.services = services;
    }

    public static class Service {
        private String id;
        private String label;
        private String repository;
        private String versionPath = "/version";
        private Map<String, String> urls = new LinkedHashMap<>();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getRepository() {
            return repository;
        }

        public void setRepository(String repository) {
            this.repository = repository;
        }

        public String getVersionPath() {
            return versionPath;
        }

        public void setVersionPath(String versionPath) {
            this.versionPath = versionPath;
        }

        public Map<String, String> getUrls() {
            return urls;
        }

        public void setUrls(Map<String, String> urls) {
            this.urls = urls;
        }
    }
}
