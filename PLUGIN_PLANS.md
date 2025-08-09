## App Usage Logs Plugin — CE-Compatible Implementation Plan

- **Goal**: Provide opt-in app usage logging (non-security events) via a standalone plugin that integrates using the existing Lowcoder CE plugin architecture without modifying CE code. Examples of usage metrics: app opens, session duration, component interactions (aggregated), query runs (counts, timing), and performance metrics.

### 1) Understand and align to CE plugin architecture
- **Node data-source plugins**: Implemented under `server/node-service/src/plugins`, conforming to `DataSourcePlugin` / `DataSourcePluginFactory` shape documented in `docs/lowcoder-extension/opensource-contribution/develop-data-source-plugins.md` and used in `server/node-service/src/services/plugin.ts`.
- **EE switch**: `server/node-service/src/plugins/index.ts` loads CE plugins by default and can be overridden by EE (`../ee/plugins`). Our plugin should be pure CE and ship as an npm package; admins add it via the “Add npm plugin” UI (`develop-ui-components-for-apps.md`), or we propose an optional PR to include it in the CE list like other built-ins (not required).
- **Backend API service Java plugins**: For custom endpoints, align to `server/api-service/PLUGIN.md` (Java) if needed. For this plugin, prefer Node data-source plugin plus app-level wiring via event handlers to avoid core changes.

### 2) Scope and event sources (no CE modifications)
- Use only existing UX event handlers and query hooks:
  - Component events and query events described in `docs/build-applications/app-interaction/event-handlers.md`.
  - Users wire events in their apps to call the plugin’s actions (e.g., "logUsageEvent").
- Do not attempt to intercept global events by patching CE; instead, provide simple helper queries and a ready-to-import app template to wire common usage events.

### 3) Plugin packaging and discovery
- Publish as npm package (e.g., `@your-scope/lowcoder-plugin-usage-logs`).
- Include `Lowcoder` manifest in `package.json` only if providing UI components (optional). Primary deliverable is a Node data-source plugin exposing actions.
- Distribution includes:
  - `src/index.ts` exporting the plugin factory default.
  - `README.md` with setup and example app wiring.
  - Optional `templates/usage-logger-app.json` with example event-handlers.

### 4) Data-source plugin design
- **Plugin ID**: `usageLogs`
- **Category**: `api`
- **DataSourceConfig.params**:
  - `destinationType` (select): `http`, `webhook`, `database` (future), `file` (future).
  - `httpUrl` (textInput): required if destinationType=`http`.
  - `httpAuthHeader` (textInput, optional): allows custom header like `Authorization: Bearer <token>`.
  - `redactFields` (jsonInput): array of JSONPath or field names to redact.
  - `sampleRate` (numberInput): 0.0–1.0 to sample events.
  - `meta` (jsonInput): arbitrary JSON merged into each event.
- **QueryConfig.actions**:
  - `logUsageEvent`:
    - `eventType` (textInput): e.g., `app_open`, `query_run`, `component_interaction`.
    - `eventTime` (textInput): ISO timestamp, default `{{Date.now()}}` as ISO.
    - `appId` (textInput) and `appName` (textInput).
    - `userId` (textInput) and `userEmail` (textInput) if available.
    - `sessionId` (textInput).
    - `properties` (jsonInput): arbitrary structured data.
  - `logPerformanceMetric`:
    - `metricName` (textInput) e.g., `app_load_ms`, `query_latency_ms`.
    - `metricValue` (numberInput)
    - `tags` (jsonInput)
- **validateDataSourceConfig**:
  - If `destinationType=http`, ensure `httpUrl` is valid URL.
  - Warn when `sampleRate` not in 0..1.
- **run(action, config, context)**:
  - Respect `sampleRate`.
  - Merge `meta` into payload.
  - Apply redaction on `properties` and top-level fields.
  - Deliver to destination:
    - `http`: POST JSON to `httpUrl` with optional `httpAuthHeader` and `Content-Type: application/json`.
  - Return `{ success: true }` with delivery status; don’t throw on 4xx/5xx—return status so app can decide retry.

### 5) Security and PII
- Default redact for keys like `password`, `token`, `secret`, `authorization` unless explicitly disabled in config.
- Avoid collecting personally identifiable information unless wired by the app author.

### 6) Frontend wiring guide (no CE changes)
- Provide instructions to add event handlers for:
  - On app load: call `logUsageEvent` with `eventType=app_open`.
  - On app leave (if supported) or periodically: call metric with session duration.
  - On query success/failure: add event handlers to increment counters and report timings; pass the measured timing as `metricValue`.
  - On important component interactions: buttons, forms, tabs.
- Provide macro examples using Lowcoder expressions to capture values.

### 7) Testing
- Unit tests for:
  - config validation and sampling
  - redaction
  - HTTP dispatch
- Integration test by adding plugin into `server/node-service/src/plugins/index.ts` locally (for dev only) or via “Add npm plugin” and running the example app.

### 8) Deployment
- Publish to npm.
- In CE, users add plugin via UI: Insert > Extensions > Add npm plugin → package name.
- Backend env vars (optional): none required; respects API URL and headers from config.

---

## Audit Logs Plugin — CE-Compatible Implementation Plan

- **Goal**: Provide auditable, security-relevant logs (who did what, when, where) as a standalone plugin using only extension points. No CE code changes. Focus on admin-grade event capture through app wiring and an optional Java API plugin for server-level events if desired by the operator.

### 1) Architecture choices
- Phase 1 (pure CE, no CE changes): Node data-source plugin exposing `logAuditEvent` action. App builders wire security-sensitive flows (CRUD, permission updates, sign-ins via app-level flows) to trigger audit logs via event handlers.
- Phase 2 (optional, still no CE modifications): Java API-Service plugin jar that registers endpoints via `LowcoderPlugin` and `@EndpointExtension` (see `server/api-service/PLUGIN.md`) to tap into server-side events generated by custom automations or to offer a central `/audit/log` endpoint (used from the Node plugin). This jar is dropped into `common.plugin-dirs` path; no CE code changes.

### 2) Node data-source plugin design
- **Plugin ID**: `auditLogs`
- **Category**: `api`
- **DataSourceConfig.params**:
  - `sinkType` (select): `http`, `webhook`, `file` (future), `SIEM` (future).
  - `httpUrl` (textInput) required if `sinkType=http`.
  - `httpAuthHeader` (textInput) optional.
  - `hashPII` (switch): when true, hash known PII fields with SHA-256.
  - `redactFields` (jsonInput) list of keys/paths to redact.
  - `clockSkewToleranceMs` (numberInput) default 300000.
  - `meta` (jsonInput) additional fields to include.
- **QueryConfig.actions**:
  - `logAuditEvent`:
    - `action` (textInput): `create`, `read`, `update`, `delete`, `login`, `logout`, `permission_change`, etc.
    - `actorId` (textInput) and `actorEmail` (textInput)
    - `targetType` (textInput) and `targetId` (textInput)
    - `resource` (textInput) e.g., `app:1234`, `datasource:abc`
    - `success` (booleanInput)
    - `ip` (textInput)
    - `userAgent` (textInput)
    - `timestamp` (textInput) ISO
    - `details` (jsonInput) arbitrary additional info
  - `logBatch`:
    - `events` (jsonInput) array of events as above for batch writes
- **validateDataSourceConfig**:
  - Validate URL and headers when `sinkType=http`.
- **run**:
  - Prepare structured payload per event, enforce required fields, merge `meta`, apply redaction/hashing, then POST to sink.
  - Return delivery status per event in batch.

### 3) Optional Java API-Service plugin (Phase 2)
- Build `lowcoder-plugins`-compliant jar module, depending on `lowcoder-sdk` and placed into `common.plugin-dirs`.
- Provide an endpoint like `/audit/log` and `/audit/logs/search` using `@EndpointExtension` annotated handlers. No CE modification required.
- Operators who need server-level logs drop the jar; otherwise, only Node plugin is used.

### 4) Security & integrity
- Support HMAC signing: optional `signingSecret` config to compute `X-Lowcoder-Signature: sha256=<hex>` over request body.
- Hash PII with SHA-256 when enabled; ensure salts/keys management guidance.
- Clock-skew tolerance parameter documented for correlating distributed events.

### 5) Frontend wiring guide
- Typical audit cases to wire via event handlers:
  - User management app: on create/update/delete user actions → `logAuditEvent` with `actorId`, `targetId`, `success`.
  - Permissions app: on group/role changes.
  - Critical data views: on export/download actions, log `read` with details.
  - Login/logout flows implemented as apps (if applicable).

### 6) Testing
- Unit tests: hashing, redaction, signing, config validation, batch handling.
- Integration tests: run CE locally, add plugin via npm, simulate actions in a demo app.
- If Java plugin used: spring-webflux functional router auto-exposed by `LowcoderPluginManager` per `PLUGIN.md`; test endpoints with curl.

### 7) Deployment
- Publish Node plugin to npm as `@your-scope/lowcoder-plugin-audit-logs`.
- For server-side optional plugin: publish jar and document path config in `application.yaml` via `common.plugin-dirs`.
- Provide example SIEM/webhook integrations (e.g., Splunk HEC, Elasticsearch indexer) via docs.

### 8) Documentation delivered with the plugins
- Setup instructions with screenshots of event handlers wiring.
- Example payloads and recommended field taxonomy.
- Data protection guidance (redaction, hashing, retention policies).