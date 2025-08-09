
### Progress log

- [x] Scaffolding
  - Created standalone Maven module `environments-plugin` (outside server tree) with ServiceLoader wiring.
  - Added `pom.xml` with `lowcoder-plugin-api` 2.3.1 and shade plugin.
  - Registered `org.example.environments.EnvironmentsPlugin` in `META-INF/services`.
- [x] Core models and storage
  - Implemented `Environment` and `ManagedObject` DTOs.
  - Implemented `StorageService` using `LowcoderServices.getConfig/setConfig` with keys `ee.environments`, `ee.managed.objects`.
- [x] Endpoints (phase 1)
  - `GET /license` → returns eeActive stub.
  - `GET /environments/list` → `{ data: Environment[] }`.
  - `GET /environments?environmentId=...` → `{ data: Environment }` or 404.
  - `POST /environments` → creates and returns raw `Environment`.
  - `PUT /environments?environmentId=...` → updates and returns raw `Environment`.
  - `POST /environments/byIds` → `{ data: Environment[] }`.
  - Managed objects CRUD: `GET /managed-obj`, `GET /managed-obj/list`, `POST /managed-obj`, `DELETE /managed-obj`.
- [ ] Endpoints (phase 2)
  - ORG, APP, DATASOURCE, QL Query management and deploy endpoints (to be added).
- [x] Build & artifact
  - Built shaded JAR: `environments-plugin-1.0.0.jar` under `/workspace/environments-plugin/target/`.
  - Copied to `/workspace/plugins/` which can be pointed to via `LOWCODER_PLUGINS_DIR` or `common.plugin-dirs`.
- [ ] Hardening
  - Redact API keys on read; validate URLs; unique `isMaster` per org.
  - Add unit tests for storage and mappers.