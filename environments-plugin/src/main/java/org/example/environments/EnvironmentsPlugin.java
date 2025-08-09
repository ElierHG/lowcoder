package org.example.environments;

import java.util.List;
import java.util.Map;

import org.example.environments.endpoints.EnvironmentsEndpoints;
import org.example.environments.endpoints.LicenseEndpoints;
import org.example.environments.endpoints.ManagedObjectEndpoints;
import org.lowcoder.plugin.api.LowcoderPlugin;
import org.lowcoder.plugin.api.LowcoderServices;
import org.lowcoder.plugin.api.PluginEndpoint;

public class EnvironmentsPlugin implements LowcoderPlugin {
    private LowcoderServices services;

    @Override
    public String pluginId() {
        return "enterprise";
    }

    @Override
    public String description() {
        return "Self-hosted enterprise endpoints: environments & deployment";
    }

    @Override
    public int loadOrder() {
        return 100;
    }

    @Override
    public boolean load(Map<String, Object> env, LowcoderServices services) {
        this.services = services;
        services.registerEndpoints("enterprise", endpoints());
        return true;
    }

    @Override
    public void unload() {
    }

    @Override
    public Object pluginInfo() {
        return Map.of("name", "EnvironmentsPlugin", "version", "1.0.0");
    }

    @Override
    public List<PluginEndpoint> endpoints() {
        if (this.services == null) {
            return List.of(new LicenseEndpoints());
        }
        return List.of(
            new EnvironmentsEndpoints(this.services),
            new ManagedObjectEndpoints(this.services),
            new LicenseEndpoints()
        );
    }
}