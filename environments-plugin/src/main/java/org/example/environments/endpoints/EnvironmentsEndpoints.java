package org.example.environments.endpoints;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.example.environments.model.Environment;
import org.example.environments.service.StorageService;
import org.lowcoder.plugin.api.EndpointExtension;
import org.lowcoder.plugin.api.PluginEndpoint;
import org.lowcoder.plugin.api.data.EndpointRequest;
import org.lowcoder.plugin.api.data.EndpointResponse;
import static org.example.environments.util.Responses.*;
import org.lowcoder.plugin.api.LowcoderServices;

public class EnvironmentsEndpoints implements PluginEndpoint {
    private final StorageService storage;
    private final ObjectMapper mapper = new ObjectMapper();

    public EnvironmentsEndpoints(LowcoderServices services) {
        this.storage = new StorageService(services);
    }

    @EndpointExtension(uri = "/environments/list", method = Method.GET, authorize = "isAuthenticated()")
    public EndpointResponse list(EndpointRequest req) {
        Map<String, Object> body = new HashMap<>();
        body.put("data", storage.listEnvironments());
        return ok(body);
    }

    @EndpointExtension(uri = "/environments", method = Method.GET, authorize = "isAuthenticated()")
    public EndpointResponse get(EndpointRequest req) {
        String environmentId = first(req.queryParams().get("environmentId"));
        Optional<Environment> env = storage.getEnvironment(environmentId);
        if (env.isEmpty()) {
            return notFound();
        }
        Map<String, Object> body = new HashMap<>();
        body.put("data", env.get());
        return ok(body);
    }

    @EndpointExtension(uri = "/environments/byIds", method = Method.POST, authorize = "isAuthenticated()")
    public EndpointResponse getByIds(EndpointRequest req) {
        try {
            byte[] b = req.body().get();
            JsonNode node = mapper.readTree(new String(b, StandardCharsets.UTF_8));
            if (node.isArray()) {
                List<String> ids = mapper.convertValue(node, mapper.getTypeFactory().constructCollectionType(List.class, String.class));
                Map<String, Object> body = new HashMap<>();
                body.put("data", storage.listEnvironmentsByIds(ids));
                return ok(body);
            }
            return badRequest(Map.of("error", "INVALID_PARAMETER"));
        } catch (Exception e) {
            return badRequest(Map.of("error", e.getMessage()));
        }
    }

    @EndpointExtension(uri = "/environments", method = Method.POST, authorize = "isAuthenticated()")
    public EndpointResponse create(EndpointRequest req) {
        try {
            JsonNode node = mapper.readTree(new String(req.body().get(), StandardCharsets.UTF_8));
            Environment env = fromSnake(node);
            String now = Instant.now().toString();
            env.setCreatedAt(now);
            env.setUpdatedAt(now);
            Environment saved = storage.upsertEnvironment(null, env);
            return ok(saved);
        } catch (Exception e) {
            return badRequest(Map.of("error", e.getMessage()));
        }
    }

    @EndpointExtension(uri = "/environments", method = Method.PUT, authorize = "isAuthenticated()")
    public EndpointResponse update(EndpointRequest req) {
        try {
            String environmentId = first(req.queryParams().get("environmentId"));
            if (environmentId == null) return badRequest(Map.of("error", "MISSING_ENVIRONMENT_ID"));
            JsonNode node = mapper.readTree(new String(req.body().get(), StandardCharsets.UTF_8));
            Environment env = fromSnake(node);
            env.setUpdatedAt(Instant.now().toString());
            Environment saved = storage.upsertEnvironment(environmentId, env);
            return ok(saved);
        } catch (Exception e) {
            return badRequest(Map.of("error", e.getMessage()));
        }
    }

    private String first(List<String> list) { return (list == null || list.isEmpty()) ? null : list.get(0); }

    private Environment fromSnake(JsonNode node) {
        Environment.EnvironmentBuilder b = Environment.builder();
        if (node.hasNonNull("environment_name")) b.environmentName(node.get("environment_name").asText());
        if (node.hasNonNull("environment_description")) b.environmentDescription(node.get("environment_description").asText());
        if (node.hasNonNull("environment_icon")) b.environmentIcon(node.get("environment_icon").asText());
        if (node.hasNonNull("environment_type")) b.environmentType(node.get("environment_type").asText());
        if (node.hasNonNull("environment_api_service_url")) b.environmentApiServiceUrl(node.get("environment_api_service_url").asText());
        if (node.hasNonNull("environment_node_service_url")) b.environmentNodeServiceUrl(node.get("environment_node_service_url").asText());
        if (node.hasNonNull("environment_frontend_url")) b.environmentFrontendUrl(node.get("environment_frontend_url").asText());
        if (node.hasNonNull("environment_apikey")) b.environmentApikey(node.get("environment_apikey").asText());
        if (node.has("isMaster")) b.isMaster(node.get("isMaster").asBoolean(false));
        return b.build();
    }
}