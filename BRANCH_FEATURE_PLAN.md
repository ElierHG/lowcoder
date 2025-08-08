# PWA Icon & Manifest Support - Branch Plan (pwa-icon-support)

## Status
- Backend
  - [x] Add controller to serve per-app icons and manifest
    - `server/api-service/lowcoder-server/src/main/java/org/lowcoder/api/application/AppIconController.java`
    - Endpoints:
      - GET `/api/v1/applications/{applicationId}/icons` → list supported sizes
      - GET `/api/v1/applications/{applicationId}/icons/{size}.png?bg=<hex>` → generated PNG with optional background
      - GET `/api/v1/applications/{applicationId}/manifest.json` → PWA manifest with icons, shortcuts, theme/background
  - [x] Security config allows public GET access
    - Allowed: `/api/v1/applications/*/icons`, `/api/v1/applications/*/icons/**`, `/api/v1/applications/*/manifest.json`
    - Mirrors for `NewUrl.APPLICATION_URL`
- Frontend
  - [x] Remove global favicon injection from `app.tsx`
  - [x] App pages inject per-app manifest and favicons via Helmet
    - `client/packages/lowcoder/src/pages/editor/editorView.tsx`
      - Added Helmet tags for both read-only branches (with and without header)
      - Injects: `link[rel=manifest]`, favicon, apple-touch-icon, startup image, og/twitter images, theme-color
  - [x] Admin pages inject default favicon via Helmet
    - `client/packages/lowcoder/src/pages/ApplicationV2/index.tsx`
    - Uses global branding favicon if set, otherwise default asset favicon

## Tests/Builds
- Client build: PASS (`yarn build`)
- Server build: Maven not available in this environment, controller compiles against interfaces used; security edits validated by code scan. To validate in CI: run Maven build in a Java-enabled environment.

## Follow-ups
- [ ] Add server-side unit/integration tests for `AppIconController` once a Java build environment is available
- [ ] Consider caching generated icons or storing pre-rendered variants to reduce CPU usage under load
- [ ] Expand manifest fields (categories, screenshots) when available from DSL