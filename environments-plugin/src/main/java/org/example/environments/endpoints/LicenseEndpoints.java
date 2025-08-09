package org.example.environments.endpoints;

import java.util.Map;

import org.lowcoder.plugin.api.EndpointExtension;
import org.lowcoder.plugin.api.PluginEndpoint;
import org.lowcoder.plugin.api.data.EndpointRequest;
import org.lowcoder.plugin.api.data.EndpointResponse;
import static org.example.environments.util.Responses.*;

public class LicenseEndpoints implements PluginEndpoint {

    @EndpointExtension(uri = "/license", method = Method.GET, authorize = "isAuthenticated()")
    public EndpointResponse license(EndpointRequest req) {
        Map<String, Object> body = Map.of(
                "eeActive", true,
                "remainingAPICalls", 999999,
                "eeLicenses", new Object[]{}
        );
        return ok(body);
    }
}