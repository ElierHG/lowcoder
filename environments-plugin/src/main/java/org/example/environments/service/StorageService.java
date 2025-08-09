package org.example.environments.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.example.environments.model.Environment;
import org.example.environments.model.ManagedObject;
import org.lowcoder.plugin.api.LowcoderServices;

public class StorageService {
    private static final String ENVIRONMENTS_KEY = "ee.environments";
    private static final String MANAGED_OBJECTS_KEY = "ee.managed.objects";

    private final LowcoderServices services;
    private final ObjectMapper mapper = new ObjectMapper();

    public StorageService(LowcoderServices services) {
        this.services = services;
    }

    public synchronized List<Environment> listEnvironments() {
        Object value = services.getConfig(ENVIRONMENTS_KEY);
        if (value == null) return new ArrayList<>();
        return mapper.convertValue(value, new TypeReference<List<Environment>>(){});
    }

    public synchronized Environment upsertEnvironment(String environmentId, Environment update) {
        List<Environment> envs = new ArrayList<>(listEnvironments());
        if (update.isMaster()) {
            for (Environment e : envs) {
                e.setMaster(false);
            }
        }
        if (environmentId == null) {
            // create
            update.setEnvironmentId(UUID.randomUUID().toString());
            envs.add(update);
        } else {
            for (int i = 0; i < envs.size(); i++) {
                if (Objects.equals(envs.get(i).getEnvironmentId(), environmentId)) {
                    Environment existing = envs.get(i);
                    // merge simple fields
                    if (update.getEnvironmentName() != null) existing.setEnvironmentName(update.getEnvironmentName());
                    if (update.getEnvironmentDescription() != null) existing.setEnvironmentDescription(update.getEnvironmentDescription());
                    if (update.getEnvironmentIcon() != null) existing.setEnvironmentIcon(update.getEnvironmentIcon());
                    if (update.getEnvironmentType() != null) existing.setEnvironmentType(update.getEnvironmentType());
                    if (update.getEnvironmentApiServiceUrl() != null) existing.setEnvironmentApiServiceUrl(update.getEnvironmentApiServiceUrl());
                    if (update.getEnvironmentNodeServiceUrl() != null) existing.setEnvironmentNodeServiceUrl(update.getEnvironmentNodeServiceUrl());
                    if (update.getEnvironmentFrontendUrl() != null) existing.setEnvironmentFrontendUrl(update.getEnvironmentFrontendUrl());
                    if (update.getEnvironmentApikey() != null) existing.setEnvironmentApikey(update.getEnvironmentApikey());
                    existing.setMaster(update.isMaster());
                    existing.setUpdatedAt(update.getUpdatedAt());
                    envs.set(i, existing);
                    update = existing;
                    break;
                }
            }
        }
        services.setConfig(ENVIRONMENTS_KEY, envs);
        return update;
    }

    public synchronized Optional<Environment> getEnvironment(String environmentId) {
        return listEnvironments().stream().filter(e -> Objects.equals(e.getEnvironmentId(), environmentId)).findFirst();
    }

    public synchronized List<Environment> listEnvironmentsByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return new ArrayList<>();
        return listEnvironments().stream().filter(e -> ids.contains(e.getEnvironmentId())).collect(Collectors.toList());
    }

    public synchronized List<ManagedObject> listManagedObjects() {
        Object value = services.getConfig(MANAGED_OBJECTS_KEY);
        if (value == null) return new ArrayList<>();
        return mapper.convertValue(value, new TypeReference<List<ManagedObject>>(){});
    }

    public synchronized Optional<ManagedObject> getManagedObject(String objGid, String environmentId, String objType) {
        return listManagedObjects().stream().filter(m ->
            Objects.equals(m.getObjGid(), objGid) && Objects.equals(m.getEnvironmentId(), environmentId) && Objects.equals(m.getObjType(), objType)
        ).findFirst();
    }

    public synchronized void upsertManagedObject(ManagedObject obj) {
        List<ManagedObject> list = new ArrayList<>(listManagedObjects());
        Optional<ManagedObject> existing = getManagedObject(obj.getObjGid(), obj.getEnvironmentId(), obj.getObjType());
        if (existing.isPresent()) {
            ManagedObject e = existing.get();
            e.setManagedId(obj.getManagedId());
        } else {
            if (obj.getId() == null) obj.setId(UUID.randomUUID().toString());
            list.add(obj);
        }
        services.setConfig(MANAGED_OBJECTS_KEY, list);
    }

    public synchronized void deleteManagedObject(String objGid, String environmentId, String objType) {
        List<ManagedObject> list = new ArrayList<>(listManagedObjects());
        list.removeIf(m -> Objects.equals(m.getObjGid(), objGid) && Objects.equals(m.getEnvironmentId(), environmentId) && Objects.equals(m.getObjType(), objType));
        services.setConfig(MANAGED_OBJECTS_KEY, list);
    }
}