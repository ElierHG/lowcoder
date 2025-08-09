package org.example.branding;

import static org.example.branding.util.Responses.ok;
import static org.example.branding.util.Responses.status;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.example.branding.dto.BrandingConfig;
import org.lowcoder.plugin.api.EndpointExtension;
import org.lowcoder.plugin.api.PluginEndpoint;
import org.lowcoder.plugin.api.data.EndpointRequest;
import org.lowcoder.plugin.api.data.EndpointResponse;
import org.lowcoder.plugin.api.LowcoderServices;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class BrandingEndpoints implements PluginEndpoint {

    private static final String KEY_GLOBAL = "enterprise.branding.global";
    private static final String KEY_ORG_PREFIX = "enterprise.branding.org.";

    private final LowcoderServices services;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BrandingEndpoints(LowcoderServices services) {
        this.services = services;
    }

    @EndpointExtension(uri = "/license", method = Method.GET, authorize = "permitAll()")
    public EndpointResponse license(EndpointRequest req) {
        Map<String, Object> body = new HashMap<>();
        body.put("eeActive", true);
        body.put("remainingAPICalls", 999999);
        body.put("eeLicenses", new Object[0]);
        return ok(body);
    }

    @EndpointExtension(uri = "/branding", method = Method.GET, authorize = "isAuthenticated()")
    public EndpointResponse getBranding(EndpointRequest req) {
        String orgId = firstParam(req, "orgId");
        boolean fallbackToGlobal = Boolean.parseBoolean(Optional.ofNullable(firstParam(req, "fallbackToGlobal")).orElse("true"));

        Object stored = services.getConfig(selectKey(orgId));
        if (stored == null && fallbackToGlobal) {
            stored = services.getConfig(KEY_GLOBAL);
        }
        if (stored == null) {
            return ok(Map.of("error", "NOT_FOUND"));
        }
        return ok(stored);
    }

    @EndpointExtension(uri = "/branding", method = Method.POST, authorize = "isAuthenticated()")
    public EndpointResponse createBranding(EndpointRequest req) {
        try {
            JsonNode root = readJson(req);
            BrandingConfig config = parseConfigFromBody(root);
            Map<String, Object> toStore = asMap(config);
            services.setConfig(selectKey(config.getOrgId()), toStore);
            return ok(toStore);
        } catch (Exception e) {
            return status(400, Map.of("error", e.getMessage()));
        }
    }

    @EndpointExtension(uri = "/branding", method = Method.PUT, authorize = "isAuthenticated()")
    public EndpointResponse updateBranding(EndpointRequest req) {
        try {
            String legacyId = firstParam(req, "brandId");
            JsonNode root = readJson(req);
            BrandingConfig config = parseConfigFromBody(root);
            if (config.getId() == null || config.getId().isBlank()) {
                config.setId(legacyId != null ? legacyId : UUID.randomUUID().toString());
            }
            Map<String, Object> toStore = asMap(config);
            services.setConfig(selectKey(config.getOrgId()), toStore);
            return ok(toStore);
        } catch (Exception e) {
            return status(400, Map.of("error", e.getMessage()));
        }
    }

    private String selectKey(String orgId) {
        if (orgId == null || orgId.isBlank()) {
            return KEY_GLOBAL;
        }
        return KEY_ORG_PREFIX + orgId;
    }

    private JsonNode readJson(EndpointRequest req) throws Exception {
        byte[] bytes = Optional.ofNullable(req.body()).map(f -> {
            try { return f.get(); } catch (Exception e) { return null; }
        }).orElse(null);
        if (bytes == null || bytes.length == 0) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(bytes);
    }

    private BrandingConfig parseConfigFromBody(JsonNode root) throws Exception {
        String id = getText(root, "id");
        String orgId = Optional.ofNullable(getText(root, "orgId")).orElse("");
        String name = getText(root, "config_name");
        String description = getText(root, "config_description");
        String icon = getText(root, "config_icon");

        JsonNode cfgNode = root.get("config_set");
        String configSetString;
        if (cfgNode == null || cfgNode.isNull()) {
            configSetString = "{}";
        } else if (cfgNode.isTextual()) {
            // already stringified
            configSetString = cfgNode.asText();
        } else {
            configSetString = objectMapper.writeValueAsString(cfgNode);
        }

        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }

        return BrandingConfig.builder()
            .id(id)
            .orgId(orgId)
            .config_name(name)
            .config_description(description)
            .config_icon(icon)
            .config_set(configSetString)
            .build();
    }

    private Map<String, Object> asMap(BrandingConfig config) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", config.getId());
        map.put("orgId", Optional.ofNullable(config.getOrgId()).orElse(""));
        if (config.getConfig_name() != null) map.put("config_name", config.getConfig_name());
        if (config.getConfig_description() != null) map.put("config_description", config.getConfig_description());
        if (config.getConfig_icon() != null) map.put("config_icon", config.getConfig_icon());
        map.put("config_set", config.getConfig_set());
        return map;
    }

    private static String firstParam(EndpointRequest req, String name) {
        if (req.queryParams() == null) return null;
        var list = req.queryParams().get(name);
        if (list == null || list.isEmpty()) return null;
        return list.get(0);
    }

    private static String getText(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return (n == null || n.isNull()) ? null : n.asText();
    }
}