package org.example.environments.endpoints;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.environments.model.ManagedObject;
import org.example.environments.service.StorageService;
import org.lowcoder.plugin.api.EndpointExtension;
import org.lowcoder.plugin.api.PluginEndpoint;
import org.lowcoder.plugin.api.data.EndpointRequest;
import org.lowcoder.plugin.api.data.EndpointResponse;
import static org.example.environments.util.Responses.*;
import org.lowcoder.plugin.api.LowcoderServices;

public class ManagedObjectEndpoints implements PluginEndpoint {
    private final StorageService storage;
    private final ObjectMapper mapper = new ObjectMapper();

    public ManagedObjectEndpoints(LowcoderServices services) {
        this.storage = new StorageService(services);
    }

    @EndpointExtension(uri = "/managed-obj", method = Method.GET, authorize = "isAuthenticated()")
    public EndpointResponse get(EndpointRequest req) {
        String objGid = first(req.queryParams().get("objGid"));
        String envId = first(req.queryParams().get("environmentId"));
        String objType = first(req.queryParams().get("objType"));
        Optional<ManagedObject> mo = storage.getManagedObject(objGid, envId, objType);
        if (mo.isEmpty()) return notFound();
        return ok(Map.of("managed", true, "data", mo.get()));
    }

    @EndpointExtension(uri = "/managed-obj/list", method = Method.GET, authorize = "isAuthenticated()")
    public EndpointResponse list(EndpointRequest req) {
        String envId = first(req.queryParams().get("environmentId"));
        String objType = first(req.queryParams().get("objType"));
        List<ManagedObject> list = storage.listManagedObjects();
        if (envId != null) list = list.stream().filter(m -> envId.equals(m.getEnvironmentId())).toList();
        if (objType != null) list = list.stream().filter(m -> objType.equals(m.getObjType())).toList();
        return ok(Map.of("data", list));
    }

    @EndpointExtension(uri = "/managed-obj", method = Method.POST, authorize = "isAuthenticated()")
    public EndpointResponse create(EndpointRequest req) {
        try {
            JsonNode node = mapper.readTree(new String(req.body().get(), StandardCharsets.UTF_8));
            ManagedObject mo = ManagedObject.builder()
                    .objGid(node.path("objGid").asText(null))
                    .environmentId(node.path("environmentId").asText(null))
                    .objType(node.path("objType").asText(null))
                    .managedId(node.path("managedId").asText(null))
                    .build();
            storage.upsertManagedObject(mo);
            return ok(Map.of("success", true));
        } catch (Exception e) {
            return badRequest(Map.of("error", e.getMessage()));
        }
    }

    @EndpointExtension(uri = "/managed-obj", method = Method.DELETE, authorize = "isAuthenticated()")
    public EndpointResponse delete(EndpointRequest req) {
        String objGid = first(req.queryParams().get("objGid"));
        String envId = first(req.queryParams().get("environmentId"));
        String objType = first(req.queryParams().get("objType"));
        storage.deleteManagedObject(objGid, envId, objType);
        return ok(Map.of("success", true));
    }

    private String first(List<String> list) { return (list == null || list.isEmpty()) ? null : list.get(0); }
}