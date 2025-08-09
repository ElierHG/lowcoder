
### Progress log

- Added standalone Maven module `branding-plugin` with Java 17 and dependency on `org.lowcoder.plugin:lowcoder-plugin-api:2.3.1`.
- Implemented `BrandingPlugin` registering endpoints under `enterprise`.
- Implemented `BrandingEndpoints` with:
  - GET `/license` returning `{ eeActive: true, remainingAPICalls: 999999, eeLicenses: [] }`.
  - GET `/branding` with `orgId` and `fallbackToGlobal` support; returns stored config or `{ error: 'NOT_FOUND' }`.
  - POST/PUT `/branding` upsert logic; serializes `config_set` to string; accepts legacy `brandId`.
- Added DTO `BrandingConfig` and response helper `Responses` (simple `EndpointResponse` implementation).
- Added `META-INF/services/org.lowcoder.plugin.api.LowcoderPlugin` for ServiceLoader discovery.

Next:
- Build the plugin JAR and place it under a configured `common.plugin-dirs` location (e.g., `server/api-service/lowcoder-server/target/lowcoder-api-service-bin/plugins/`).
- Restart API service and verify endpoints:
  - `/api/plugins/enterprise/license`
  - `/api/plugins/enterprise/branding`

- Build status: Built successfully via `mvn -q -DskipTests package` in `branding-plugin/`; artifact: `target/branding-plugin-1.0.0.jar`.
- Copied to server plugins dir: `server/api-service/lowcoder-server/target/lowcoder-api-service-bin/plugins/enterprise-branding-plugin.jar`.
- Next: start/restart API service (selfhost profile) and verify:
  - `GET /api/plugins/` includes `enterprise` plugin
  - `GET /api/plugins/enterprise/license` returns `eeActive: true`
  - `GET /api/plugins/enterprise/branding` returns either config or `{ error: 'NOT_FOUND' }`