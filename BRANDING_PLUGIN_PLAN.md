## Branding plugin implementation plan (server plugin, FE-compatible)

Goal: Provide a drop-in backend plugin that exposes the Branding endpoints expected by the current frontend, without altering OSS code. The plugin loads via the existing plugin manager and serves under `/api/plugins/enterprise/*`, mirroring the enterprise API shape.

### 1) Architecture fit

-   **Loader/runtime**: `LowcoderPluginManager` + `PathBasedPluginLoader` auto-discovers JARs from `common.plugin-dirs` (see `server/api-service/PLUGIN.md`).
-   **API surface**: Plugins register endpoints via `LowcoderServices.registerEndpoints(urlPrefix, endpoints)` and annotate handlers with `@EndpointExtension` (methods accept `EndpointRequest` and return `EndpointResponse`). Endpoints become available at `/api/plugins/{urlPrefix}/*`.
-   **Chosen urlPrefix**: `enterprise` (so FE can call `/api/plugins/enterprise/branding` without changes).
-   **Config persistence**: Use `LowcoderServices.setConfig/getConfig` (backed by `ServerConfigRepository`) to store branding configs per org and global.

### 2) Endpoints to implement (contract mirrors FE in `client/packages/lowcoder/src/api/enterpriseApi.ts`)

Base path: `/api/plugins/enterprise`

-   GET `/branding?orgId=`

    -   Input: optional `orgId` (empty = global).
    -   Output: object compatible with FE `BrandingSettingResponse`:
        -   `id`: string (e.g., key or generated id)
        -   `orgId`: string | ''
        -   `config_name`, `config_description`, `config_icon` (optional)
        -   `config_set`: stringified JSON of `BrandingSettings` (FE parses JSON)
    -   Behavior: return org override if present; else fall back to global when `fallbackToGlobal=true` is used by FE.
    -   On not found: `{ error: 'NOT_FOUND' }` (FE handles error and retries global).

-   POST `/branding`

    -   Body: `{ orgId?: string, config_name?, config_description?, config_icon?, config_set: BrandingSettings }`
    -   Behavior: upsert storage key and respond with created/updated object; server must serialize `config_set` to string.

-   PUT `/branding` (and accept legacy `?brandId=` param)

    -   Body: same as POST but must contain `id` or addressable key; upsert semantics.

-   GET `/license` (minimal)
    -   Output (to unlock FE screens): `{ eeActive: true, remainingAPICalls: 999999, eeLicenses: [] }`
    -   Rationale: `Branding` settings page is gated by license selectors; this lightweight endpoint keeps OSS unchanged while enabling the UI.

Notes

-   Uploads (logo/favicon/images) already use `/materials` (see `client/packages/lowcoder/src/api/materialApi.ts`); plugin only stores returned material IDs/URLs inside `config_set`.

### 3) Storage model

-   Keys under `ServerConfig`:
    -   Global: `enterprise.branding.global`
    -   Per org: `enterprise.branding.org.{orgId}`
-   Value: JSON object `{ id, orgId, config_name?, config_description?, config_icon?, config_set: string }`.
-   The plugin returns the value as-is (ensuring `config_set` is a stringified JSON per FE expectations).

### 4) Authorization

-   Use `@EndpointExtension(authorize = "isAuthenticated()")` initially.
-   Optional hardening phase: restrict write ops to admins once a reliable expression is available (e.g., a custom method exposed to SpEL), but avoid modifying OSS server first.

### 5) Project layout (new Maven module or external repo)

```
branding-plugin/
  pom.xml                             # depends on org.lowcoder.plugin:lowcoder-plugin-api (+ optional sdk)
  src/main/java/org/example/branding/
    BrandingPlugin.java               # implements LowcoderPlugin
    BrandingEndpoints.java            # implements PluginEndpoint; defines @EndpointExtension handlers
    dto/BrandingConfig.java           # mirrors FE types as needed
    dto/BrandingSettings.java         # mirrors FE types
    util/Responses.java               # helpers to build EndpointResponse
```

Key class sketches

```
// BrandingPlugin.java
public class BrandingPlugin implements LowcoderPlugin {
  public String pluginId() { return "enterprise"; } // critical: keep FE URLs
  public String description() { return "Self-hosted enterprise endpoints: branding + license stub"; }
  public int loadOrder() { return 100; }
  public boolean load(Map<String,Object> env, LowcoderServices services) {
    services.registerEndpoints("enterprise", List.of(new BrandingEndpoints(services)));
    return true;
  }
  public void unload() {}
}

// BrandingEndpoints.java (snippets)
public class BrandingEndpoints implements PluginEndpoint {
  private final LowcoderServices services;
  public BrandingEndpoints(LowcoderServices services){ this.services = services; }

  @EndpointExtension(uri = "/license", method = Method.GET, authorize = "permitAll()")
  public EndpointResponse license(EndpointRequest req) { /* return eeActive=true... */ }

  @EndpointExtension(uri = "/branding", method = Method.GET, authorize = "isAuthenticated()")
  public EndpointResponse getBranding(EndpointRequest req) { /* read orgId, lookup config, fallback */ }

  @EndpointExtension(uri = "/branding", method = Method.POST, authorize = "isAuthenticated()")
  public EndpointResponse createBranding(EndpointRequest req) { /* upsert; serialize config_set */ }

  @EndpointExtension(uri = "/branding", method = Method.PUT, authorize = "isAuthenticated()")
  public EndpointResponse updateBranding(EndpointRequest req) { /* upsert */ }
}
```

### 6) Packaging & deployment

-   Build: `mvn -q -DskipTests package` in the plugin module.
-   Configure server (API service) `application.yaml`:
    ```yaml
    common:
        plugin-dirs:
            - plugins
    ```
-   Drop the built JAR into `server/api-service/lowcoder-server/target/lowcoder-api-service-bin/plugins/` (prod: the equivalent runtime folder), or any path listed under `plugin-dirs`.
-   Restart API service; verify `/api/plugins/` lists the plugin.

### 7) Frontend behavior

-   Calls already present:
    -   License: `GET /api/plugins/enterprise/license` (saga boot)
    -   Branding: `GET/POST/PUT /api/plugins/enterprise/branding`
-   For the FE to fetch branding automatically, build with `REACT_APP_EDITION=enterprise` (see `util/envUtils.isEEEnvironment`). Otherwise, the login flow still triggers branding fetchs in some routes; set the env for full parity.

### 8) Acceptance checks

-   Hitting `GET /api/plugins/enterprise/license` returns `eeActive: true`.
-   `GET /api/plugins/enterprise/branding?orgId=` returns `{ id, config_set: "{...}" }` or `{ error: ... }` when missing.
-   Branding page in Settings loads and saves successfully (images uploaded via `/materials`).
-   Editor/app pages reflect `mainBrandingColor`, favicon and logos (see selectors and `Helmet` usage).

### 9) Future hardening (optional)

-   Tighten authorization to workspace admins.
-   Add server-side validation of colors/URLs and size limits (align to Material upload constraints).
-   Migrate `config_set` to structured fields while keeping FE compatible (continue returning stringified JSON).

### 10) Implementing outside this repo and plugging into Lowcoder

You can build this plugin in an independent repository and load it dynamically with no OSS code changes.

1. Create a standalone Maven project (JDK 17)

```xml
<!-- pom.xml (minimal) -->
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.yourorg.lowcoder</groupId>
  <artifactId>branding-plugin</artifactId>
  <version>1.0.0</version>
  <properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
  </properties>
  <dependencies>
    <!-- Match the server's dependency (see lowcoder-dependencies/pom.xml) -->
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
</project>
```

2. Implement `LowcoderPlugin` and `PluginEndpoint` as described above (keep `pluginId()` returning `"enterprise"` so the FE works unchanged).

3. Build

```bash
mvn -q -DskipTests package
```

4. Plug into the running Lowcoder API server

-   Place the built JAR where the server scans for plugins. Configure one or more locations via Spring property `common.plugin-dirs`:

    -   In `application.yaml` of the API service:
        ```yaml
        common:
            plugin-dirs:
                - /opt/lowcoder/plugins
                - /opt/lowcoder/plugins/branding-plugin-1.0.0.jar
        ```
    -   Or as a JVM/system property or CLI flag: `--common.plugin-dirs=/opt/lowcoder/plugins,/opt/branding.jar`
    -   Or as an environment variable (Spring relaxed binding): `COMMON_PLUGIN_DIRS=/opt/lowcoder/plugins,/opt/branding.jar`

-   Drop or mount your plugin JAR into one of those paths (e.g., Docker volume mount to `/opt/lowcoder/plugins`).
-   Restart the API service. On boot, you should see: "Registered plugin: enterprise" and the plugin listed at `GET /api/plugins/`.

5. Production notes

-   Ensure the plugin is compiled with the same `lowcoder-plugin-api` version the server uses (currently `2.3.1`).
-   Target Java 17; do not relocate/shade `org.lowcoder.plugin.*` classes to avoid classloader mismatches.
-   If the vendor EE plugin ever co-exists, plugin IDs must be unique. Since the FE calls `/api/plugins/enterprise/*`, only one plugin with `pluginId() == "enterprise"` should be present at a time.
