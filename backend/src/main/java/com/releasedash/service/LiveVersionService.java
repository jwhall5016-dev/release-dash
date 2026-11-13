package com.releasedash.service;

import com.releasedash.config.LiveServicesProperties;
import com.releasedash.dto.LiveServicesCatalogResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serves the live-services catalog only. Version endpoints are fetched from the user's
 * browser (see frontend api.ts) so reachability follows the operator's network/VPN.
 */
@Service
public class LiveVersionService {

    private final LiveServicesProperties properties;

    public LiveVersionService(LiveServicesProperties properties) {
        this.properties = properties;
    }

    public LiveServicesCatalogResponse getCatalog() {
        LiveServicesCatalogResponse catalog = new LiveServicesCatalogResponse();
        catalog.setEnvironments(new ArrayList<>(properties.getEnvironments()));
        List<LiveServicesCatalogResponse.LiveServiceDescriptor> services = new ArrayList<>();
        for (LiveServicesProperties.Service service : properties.getServices()) {
            LiveServicesCatalogResponse.LiveServiceDescriptor descriptor =
                new LiveServicesCatalogResponse.LiveServiceDescriptor();
            descriptor.setId(service.getId());
            descriptor.setLabel(service.getLabel());
            descriptor.setRepository(service.getRepository());
            descriptor.setVersionPath(service.getVersionPath());
            Map<String, String> urls = new LinkedHashMap<>();
            if (service.getUrls() != null) {
                service.getUrls().forEach((env, url) -> {
                    if (url != null && !url.isBlank()) {
                        urls.put(env, url.trim().replaceAll("/$", ""));
                    }
                });
            }
            descriptor.setUrls(urls);
            services.add(descriptor);
        }
        catalog.setServices(services);
        return catalog;
    }
}
