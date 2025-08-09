## Environments plugin implementation plan (server plugin, FE-compatible)

Goal: Provide a drop-in backend plugin that exposes the Environments and Managed Objects endpoints expected by the current frontend, without altering OSS code. The plugin loads via the existing backend plugin manager and serves under `/api/plugins/enterprise/*`, mirroring the enterprise API shape.

### 1) Architecture fit

-   Loader/runtime: `LowcoderPluginManager` + `PathBasedPluginLoader` auto-discovers JARs from `common.plugin-dirs` (see `server/api-service/PLUGIN.md`).
-   API surface: Implement `LowcoderPlugin` and register one or more `PluginEndpoint` classes via `LowcoderServices.registerEndpoints(urlPrefix, endpoints)`. Methods are annotated with `@EndpointExtension` and accept `EndpointRequest`, returning `EndpointResponse`. Endpoints are mounted at `/api/plugins/{urlPrefix}/*`.
-   Chosen urlPrefix: `enterprise` so FE calls like `/api/plugins/enterprise/environments` work as-is.
-   Persistence: Use `LowcoderServices.setConfig/getConfig` (backed by `ServerConfigRepository`) to store environments, managed objects, and simple deployment metadata. Keys are namespaced and values are JSON-serializable objects.

### 2) Data model (internal)

-   Environment (maps to FE `Environment` in `client/packages/lowcoder/src/pages/setting/environments/types/environment.types.ts`):

    -   environmentId: string (UUID)
    -   environmentName?: string
    -   environmentDescription?: string
    -   environmentIcon?: string
    -   environmentType?: string (e.g. DEV/TEST/PREPROD/PROD)
    -   environmentApiServiceUrl?: string (e.g. `https://api.target-env.local`)
    -   environmentNodeServiceUrl?: string (optional)
    -   environmentFrontendUrl?: string (for cards/links)
    -   environmentApikey: string (Bearer for remote env API)
    -   isMaster: boolean
    -   createdAt: string (ISO)
    -   updatedAt: string (ISO)

-   ManagedObject:
    -   id: string (UUID)
    -   managedId: string (target-side record id, when applicable)
    -   objGid: string (global ID of object in source env)
    -   environmentId: string (which env this mapping applies to)
    -   objType: one of ORG | APP | QUERY | DATASOURCE

Storage keys (via `LowcoderServices`):

-   `ee.environments` → Environment[]
-   `ee.managed.objects` → ManagedObject[]

### 3) Endpoint contract (match FE calls exactly)

Base path: `/api/plugins/enterprise`

Environments

-   GET `/environments/list`
    -   Response: `{ data: Environment[] }`
-   GET `/environments?environmentId=...`
    -   Response: `{ data: Environment }`
-   POST `/environments`
    -   Body fields (snake_case as FE sends):
        -   environment_name, environment_description, environment_icon, environment_apikey, environment_type,
            environment_api_service_url, environment_frontend_url, environment_node_service_url, isMaster
    -   Response: `Environment` (unwrapped object)
-   PUT `/environments?environmentId=...`
    -   Body: same shape as POST
    -   Response: `Environment`
-   POST `/environments/byIds`
    -   Body: `string[]` of environmentIds
    -   Response: `{ data: Environment[] }`

License (already used by FE; provide at least a working stub)

-   GET `/license`
    -   Response: `{ eeActive: boolean, remainingAPICalls: number, eeLicenses: [{ uuid, issuedTo, apiCallsLimit }] }`

Managed objects (generic)

-   GET `/managed-obj?objGid=...&environmentId=...&objType=...`
    -   Response (exists): `{ managed: true, data: ManagedObject }`
    -   404 when not found
-   GET `/managed-obj/list?environmentId=...&objType?=...`
    -   Response: `{ data: ManagedObject[] }`
-   POST `/managed-obj`
    -   Body: `{ objGid, environmentId, objType, managedId? }`
    -   Response: `{ success: true }`
-   DELETE `/managed-obj?objGid=...&environmentId=...&objType=...`
    -   Response: `{ success: true }`

Managed Workspaces (ORG)

-   GET `/org/list`
    -   Response: `{ data: { environmentId, org_gid, org_name, org_tags? }[] }` (FE filters by environmentId)
-   POST `/org`
    -   Body: `{ environment_id, org_gid, org_name, org_tags: string[] }`
    -   Response: `{ success: true }`
-   DELETE `/org?orgGid=...`
    -   Response: `{ success: true }`
-   POST `/org/deploy?orgGid=...&envId=...&targetEnvId=...`
    -   Response: `{ success: true }`

Managed Apps

-   GET `/app/list` → `{ data: any[] }` (FE filters by environmentId)
-   POST `/app` → Body `{ environment_id, app_gid, app_name, app_tags: string[] }`
-   DELETE `/app?appGid=...`
-   POST `/app/deploy?applicationId=...&envId=...&targetEnvId=...&updateDependenciesIfNeeded=...&publishOnTarget=...&publicToAll=...&publicToMarketplace=...&deployCredential=...`

Managed Data Sources

-   GET `/datasource/list?environmentId=...` → `{ data: any[] }`
-   POST `/datasource` → Body `{ environment_id, name, datasource_gid }`
-   DELETE `/datasource?datasourceGid=...`
-   POST `/datasource/deploy?envId=...&targetEnvId=...&datasourceId=...&deployCredential=...`

Managed Queries

-   GET `/qlQuery/list?environmentId=...` → `{ data: any[] }`
-   POST `/qlQuery` → Body `{ environment_id, ql_query_gid, ql_query_name, ql_query_tags: string[] }`
-   DELETE `/qlQuery?qlQueryGid=...`
-   POST `/qlQuery/deploy?envId=...&targetEnvId=...&queryId=...&deployCredential=...`

Notes on auth:

-   Use `authorize = "isAuthenticated()"` for all endpoints except `/license` which can be `isAuthenticated()` as well (or `permitAll()` if desired).

### 4) Implementation outline (classes)

Maven module (external JAR):

```
environments-plugin/
  pom.xml                              # depends on org.lowcoder.plugin:lowcoder-plugin-api (+ optional sdk)
  src/main/java/org/example/environments/
    EnvironmentsPlugin.java            # implements LowcoderPlugin; registers endpoints
    endpoints/
      EnvironmentsEndpoints.java       # CRUD/list for environments
      ManagedObjectEndpoints.java      # set/unset/list/get managed objects
      OrgEndpoints.java                # managed workspace connect/unconnect/deploy
      AppEndpoints.java                # managed apps + deploy
      DatasourceEndpoints.java         # managed datasources + deploy
      QueryEndpoints.java              # managed queries + deploy
      LicenseEndpoints.java            # license summary (stub OK)
    service/
      StorageService.java              # wraps LowcoderServices getConfig/setConfig with typed accessors
      DeploymentService.java           # orchestrates cross-env copy
      RemoteApiClient.java             # axios-like HTTP client using Java (WebClient/HttpClient), Bearer auth
    model/
      Environment.java                 # as above
      ManagedObject.java               # as above
```

Plugin entrypoint sketch:

```java
public class EnvironmentsPlugin implements LowcoderPlugin {
  public String pluginId() { return "enterprise"; }
  public String description() { return "Self-hosted enterprise endpoints: environments & deployment"; }
  public int loadOrder() { return 100; }
  public boolean load(Map<String,Object> env, LowcoderServices services) {
    services.registerEndpoints("enterprise", List.of(
      new EnvironmentsEndpoints(services),
      new ManagedObjectEndpoints(services),
      new OrgEndpoints(services),
      new AppEndpoints(services),
      new DatasourceEndpoints(services),
      new QueryEndpoints(services),
      new LicenseEndpoints(services)
    ));
    return true;
  }
  public void unload() {}
}
```

Endpoint handler method shape:

```java
@EndpointExtension(uri = "/environments/list", method = Method.GET, authorize = "isAuthenticated()")
public EndpointResponse listEnvironments(EndpointRequest req) {
  List<Environment> envs = storage.listEnvironments();
  return EndpointResponse.ok(Map.of("data", envs));
}
```

### 5) Deployment flows (high-level algorithms)

Common

-   Resolve source `envId` and `targetEnvId` to Environment records from storage.
-   Use `RemoteApiClient` with `environmentApiServiceUrl` and `environmentApikey` to call remote OSS APIs.
-   For org/app/query/datasource, prefer GIDs to correlate objects across envs. Maintain `ManagedObject` entries to map source GID to target `managedId` when created.

Org (Workspace) deploy `/org/deploy`

1. Lookup source org by GID in source env (via `/api/users/me` then `orgAndRoles` or a dedicated org endpoint if available).
2. If target ManagedObject of type ORG for same GID exists → done.
3. Else create target org (if an API exists) or fail with clear error instructing to connect the workspace first via `/org` endpoint.
4. Set ManagedObject for target.

App deploy `/app/deploy`

1. Fetch app from source env: `/api/applications/{applicationId}` (editing DSL) and its orgId.
2. Ensure org mapping exists on target (ManagedObject ORG). If missing → error.
3. If `updateDependenciesIfNeeded=true`: enumerate app dependencies (datasources, queries) and ensure they exist in target (deploy those first if needed; see below). Respect `deployCredential` for sensitive fields.
4. Create or update app in target env using `/api/applications` (POST) or `PUT /api/applications/{id}` with DSL and mapped orgId.
5. Optionally publish on target (`publishOnTarget`, `publicToAll`, `publicToMarketplace`).
6. Record `ManagedObject` for APP (objGid → target managedId) if not present.

Datasource deploy `/datasource/deploy`

1. Fetch datasource definition from source env (including structure and config if `deployCredential=true`).
2. Map orgId using ManagedObject ORG.
3. Upsert on target via `/api/datasources` endpoints.
4. Record `ManagedObject` for DATASOURCE.

Query deploy `/qlQuery/deploy`

1. Fetch library query from source env (`/api/library-queries/...`).
2. Map orgId via ManagedObject ORG.
3. Upsert on target via library query endpoints.
4. Record `ManagedObject` for QUERY.

### 6) Response shaping and errors

-   Match FE shapes exactly:
    -   List endpoints return `{ data: [...] }`.
    -   Single GET returns `{ data: {...} }`.
    -   Create/Update for environments return unwrapped `Environment`.
-   Consistent errors: `status=404` when not found (e.g., GET managed-obj missing). For validation errors, return `400` with `{ error: 'INVALID_PARAMETER', details }`.

### 7) Security

-   Use `authorize = "isAuthenticated()"` for all Environments/Managed/Deploy endpoints.
-   Validate that caller has sufficient privileges (optional enhancement) using Spring Security expressions if user roles are exposed.
-   Never echo API keys; redact on read/list.

### 8) Persistence details

-   `StorageService` wraps `LowcoderServices.getConfig/setConfig` with atomic read-modify-write and defensive copies.
-   Keys:
    -   `ee.environments` (Environment[]): on write, keep only one `isMaster=true` item.
    -   `ee.managed.objects` (ManagedObject[]): upsert by `(objGid, environmentId, objType)`.
-   For large datasets, consider sharding per env key later: `ee.env.{environmentId}.managed`.

### 9) Packaging & deployment

-   Build an external JAR with dependency on `lowcoder-plugin-api`.
-   Place the JAR under a folder listed in `common.plugin-dirs` (see `application.yaml`).
-   On server startup, `PathBasedPluginLoader` discovers the JAR and `LowcoderPluginManager` starts it.

### 10) Testing plan

-   Unit-test `StorageService` and response shape mappers.
-   Mock `RemoteApiClient` for deploy flows.
-   Integration-test endpoint registration (router) and auth decisions.
-   Manual FE smoke-test:
    -   Environments list/create/update
    -   Managed objects connect/unconnect/list
    -   Deploy flows for ORG/APP/DATASOURCE/QUERY (with and without credentials)

### 11) Future enhancements

-   Fine-grained RBAC (e.g., only admins can mutate environments).
-   Background jobs for dependency analysis and preflight checks.
-   Signed export bundles for offline promotion between envs.

## Environments plugin implementation plan (server plugin, FE-compatible)

Goal: Provide a drop-in backend plugin that exposes the Environments endpoints expected by the current frontend, without altering OSS code. The plugin loads via the existing plugin manager and serves under `/api/plugins/enterprise/*`, mirroring the enterprise API shape.

### 1) Architecture fit

-   Loader/runtime: `LowcoderPluginManager` + `PathBasedPluginLoader` auto-discovers JARs from `common.plugin-dirs` (see `server/api-service/PLUGIN.md`).
-   API surface: Implement `LowcoderPlugin` and register one or more `PluginEndpoint` classes whose handler methods are annotated with `@EndpointExtension`. Endpoints will be mounted at `/api/plugins/{urlPrefix}/*`.
-   Chosen urlPrefix: `enterprise` (to match existing FE calls like `/api/plugins/enterprise/environments/...`).
-   Config persistence: Use the plugin services abstraction (e.g., a config repository via `LowcoderServices`/`SharedPluginServices`) to store environment records per organization.

### 2) Endpoint contract (aligns with current FE calls)

Base path: `/api/plugins/enterprise`

Environment model (normalized object returned to FE):

```
{
  environmentId: string,
  environmentName?: string,
  environmentDescription?: string,
  environmentIcon?: string,
  environmentType?: 'DEV' | 'TEST' | 'PREPROD' | 'PROD',
  environmentApiServiceUrl?: string,
  environmentNodeServiceUrl?: string,
  environmentFrontendUrl?: string,
  environmentApikey: string,
  isMaster: boolean,
  createdAt: string, // ISO
  updatedAt: string  // ISO
}
```

Payload mapping (incoming body from FE to storage):

-   FE sends snake-case keys, e.g. `environment_name`, `environment_description`, ...
-   Server maps snake-case to the normalized model above.

Endpoints:

-   GET `/environments/list`

    -   Output: `{ data: Environment[] }`
    -   Scope: current organization of the caller (see Authorization below).

-   GET `/environments?environmentId=...`

    -   Output: `{ data: Environment }`

-   POST `/environments`

    -   Body: accepts snake-case keys described above; `environment_name` is required, others optional.
    -   Behavior: create a new environment for the caller's org, generate `environmentId`, set timestamps.
    -   Output: `Environment` (raw object, no `{ data: ... }` wrapper). Matches FE expectation.

-   PUT `/environments?environmentId=...`

    -   Body: accepts same snake-case keys; partial update; updates `updatedAt`.
    -   Output: `Environment` (raw object, no `{ data: ... }` wrapper).

-   Optional: DELETE `/environments?environmentId=...`

    -   Output: `{ success: true }` (FE currently does not call this; keep for completeness).

-   GET `/license`
    -   Output: minimal license stub to unlock EE-gated screens in FE:
        -   `{ eeActive: true, remainingAPICalls: 999999, eeLicenses: [] }`
    -   Rationale: FE checks enterprise status in `EnterpriseProvider` and will fetch environments only when EE is active.

Notes on response shapes (critical for FE compatibility):

-   `GET /environments/list` → FE reads `response.data.data`.
-   `GET /environments?environmentId=...` → FE reads `response.data.data`.
-   `POST/PUT /environments` → FE reads `response.data` directly.

### 3) Storage model

-   Keys per-org under a config namespace, e.g.:
    -   `enterprise.environments.org.{orgId}` → array of `Environment` objects.
-   Indexing/lookup:
    -   On list: return the full array.
    -   On get/update: find by `environmentId` within that array.
-   Integrity rules:
    -   `isMaster` must be unique per org (at most one master).
    -   `environmentName` must be unique per org (case-insensitive) to reduce confusion.
    -   Timestamps: set `createdAt` at creation; always update `updatedAt`.
    -   Sensitive field `environmentApikey` is stored as provided; consider encryption using server-side utilities in a follow-up hardening.

### 4) Authorization & multitenancy

-   Start with `@EndpointExtension(authorize = "isAuthenticated()")` (consistent with Branding plan) to avoid changing OSS security config.
-   Derive organization context from the authenticated user/session. All CRUD is scoped to the caller's `currentOrgId`.
-   Future hardening: restrict write operations to org admins or privileged roles.

### 5) Project layout (new Maven module or external repo)

```
environments-plugin/
  pom.xml
  src/main/java/org/example/environments/
    EnvironmentsPlugin.java        # implements LowcoderPlugin
    EnvironmentsEndpoints.java     # implements PluginEndpoint; CRUD endpoints
    dto/EnvironmentDto.java        # normalized model
    dto/EnvironmentPayload.java    # snake-case request payload
    util/EnvRepository.java        # wraps config read/write per org
    util/Responses.java            # EndpointResponse helpers
```

Key class sketches

```
// EnvironmentsPlugin.java
public class EnvironmentsPlugin implements LowcoderPlugin {
  public String pluginId() { return "enterprise"; }
  public String description() { return "Self-hosted enterprise endpoints: environments + license stub"; }
  public int loadOrder() { return 100; }
  public boolean load(Map<String,Object> env, LowcoderServices services) {
    services.registerEndpoints("enterprise", List.of(new EnvironmentsEndpoints(services)));
    return true;
  }
  public void unload() {}
}

// EnvironmentsEndpoints.java (signatures)
@EndpointExtension(uri = "/license", method = Method.GET, authorize = "permitAll()")
EndpointResponse license(EndpointRequest req)

@EndpointExtension(uri = "/environments/list", method = Method.GET, authorize = "isAuthenticated()")
EndpointResponse listEnvironments(EndpointRequest req)

@EndpointExtension(uri = "/environments", method = Method.GET, authorize = "isAuthenticated()")
EndpointResponse getEnvironment(EndpointRequest req) // expects query param environmentId

@EndpointExtension(uri = "/environments", method = Method.POST, authorize = "isAuthenticated()")
EndpointResponse createEnvironment(EndpointRequest req) // body: EnvironmentPayload (snake-case)

@EndpointExtension(uri = "/environments", method = Method.PUT, authorize = "isAuthenticated()")
EndpointResponse updateEnvironment(EndpointRequest req) // query: environmentId; body: EnvironmentPayload

// Optional
@EndpointExtension(uri = "/environments", method = Method.DELETE, authorize = "isAuthenticated()")
EndpointResponse deleteEnvironment(EndpointRequest req)
```

### 6) Data mapping rules

-   Input snake-case → normalized DTO mapping:

    -   `environment_name` → `environmentName`
    -   `environment_description` → `environmentDescription`
    -   `environment_icon` → `environmentIcon`
    -   `environment_type` → `environmentType`
    -   `environment_api_service_url` → `environmentApiServiceUrl`
    -   `environment_frontend_url` → `environmentFrontendUrl`
    -   `environment_node_service_url` → `environmentNodeServiceUrl`
    -   `environment_apikey` → `environmentApikey`
    -   `isMaster` (boolean) passes through

-   Responses always return the normalized model shown above.

### 7) Packaging & deployment

-   Build: `mvn -q -DskipTests package` in the plugin module.
-   Configure server (API service) `application.yaml`:

```
common:
  plugin-dirs:
    - plugins
```

-   Drop the built JAR into `server/api-service/lowcoder-server/target/lowcoder-api-service-bin/plugins/` (or any configured `plugin-dirs`).
-   Restart API service; verify endpoints under `/api/plugins/enterprise/*`.

### 8) Frontend behavior hooks you will unlock

-   `EnterpriseProvider` triggers:
    -   `GET /api/plugins/enterprise/license` to set EE active.
    -   `GET /api/plugins/enterprise/environments/list` to populate environments in Redux.
-   Settings → Environments page:
    -   Create/update calls:
        -   POST `/api/plugins/enterprise/environments` (expects raw environment in `response.data`).
        -   PUT `/api/plugins/enterprise/environments?environmentId=...` (expects raw environment in `response.data`).
-   Remote resource listing (workspaces/apps/datasources/queries) call the target environment’s own `/api/...` endpoints directly from the browser using the saved `environmentApiServiceUrl` and `environmentApikey`. Ensure CORS is allowed on those remote environments.

### 9) Acceptance checks

-   `GET /api/plugins/enterprise/license` returns `{ eeActive: true, remainingAPICalls: 999999, eeLicenses: [] }`.
-   `GET /api/plugins/enterprise/environments/list` returns `{ data: [...] }` with normalized fields.
-   `POST /api/plugins/enterprise/environments` creates a record and returns the environment object.
-   `PUT /api/plugins/enterprise/environments?environmentId=...` updates and returns the environment object.
-   Environments settings page loads, can create/update items, and shows license status (the per-environment license check uses the remote `apiServiceUrl`).

### 10) Future hardening

-   Authorization: limit write operations to org admins; add audit logs.
-   Validation: URL format, API key presence for remote lookups, allowed `environmentType` values, and unique `isMaster` per org.
-   Secrets: encrypt `environmentApikey` at rest.
-   Pagination: if the list grows large, add pagination/sorting; FE currently expects a full array.
-   Admin tools: bulk import/export of environments; health checks to test connectivity to each environment.

### 11) Implementing outside this repo and plugging into Lowcoder

You can build this plugin in an independent repository and load it dynamically with no OSS code changes.

1. Minimal `pom.xml` (match server’s `lowcoder-plugin-api` version):

```
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.yourorg.lowcoder</groupId>
  <artifactId>environments-plugin</artifactId>
  <version>1.0.0</version>
  <properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.lowcoder.plugin</groupId>
      <artifactId>lowcoder-plugin-api</artifactId>
      <version>2.3.1</version>
    </dependency>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <version>1.18.32</version>
      <scope>provided</scope>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.11.0</version>
        <configuration>
          <release>17</release>
        </configuration>
      </plugin>
    </plugins>
  </build>

</project>
```

2. Keep `pluginId()` returning `"enterprise"` so FE URLs remain unchanged. Only one plugin with this ID should be active.

3. Build and place the JAR into a configured `plugin-dirs` path. Restart the API service.

4. If you want full UI auto-activation without code changes, build the FE with `REACT_APP_EDITION=enterprise` so `EnterpriseProvider` auto-fetches license/environments. Otherwise, users can still navigate to the Environments setting screen via routes.
