package org.example.branding;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.lowcoder.plugin.api.LowcoderPlugin;
import org.lowcoder.plugin.api.LowcoderServices;
import org.lowcoder.plugin.api.PluginEndpoint;

public class BrandingPlugin implements LowcoderPlugin {

    private LowcoderServices services;

    @Override
    public String pluginId() {
        return "enterprise";
    }

    @Override
    public String description() {
        return "Self-hosted enterprise endpoints: branding + license stub";
    }

    @Override
    public int loadOrder() {
        return 100;
    }

    @Override
    public boolean load(Map<String, Object> env, LowcoderServices services) {
        this.services = services;
        PluginEndpoint endpoints = new BrandingEndpoints(services);
        services.registerEndpoints("enterprise", List.of(endpoints));
        return true;
    }

    @Override
    public void unload() {
        // no-op
    }

    @Override
    public List<PluginEndpoint> endpoints() {
        // Endpoints are registered via services in load(); return empty here
        return Collections.emptyList();
    }

    @Override
    public Object pluginInfo() {
        return Map.of(
            "id", pluginId(),
            "description", description(),
            "endpoints", List.of("/license", "/branding")
        );
    }
}