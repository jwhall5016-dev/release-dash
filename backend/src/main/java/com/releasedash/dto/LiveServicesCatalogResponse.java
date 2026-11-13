package com.releasedash.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LiveServicesCatalogResponse {

    private List<String> environments;
    private List<LiveServiceDescriptor> services;

    public List<String> getEnvironments() {
        return environments;
    }

    public void setEnvironments(List<String> environments) {
        this.environments = environments;
    }

    public List<LiveServiceDescriptor> getServices() {
        return services;
    }

    public void setServices(List<LiveServiceDescriptor> services) {
        this.services = services;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LiveServiceDescriptor {
        private String id;
        private String label;
        private String repository;
        @JsonProperty("version_path")
        private String versionPath;
        /** environment -> base URL (null/absent = not configured) */
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
