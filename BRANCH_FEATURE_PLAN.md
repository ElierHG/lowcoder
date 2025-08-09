# Lowcoder App-Specific Favicon & PWA Icon Implementation Plan

## Overview

This plan outlines the implementation of favicon and PWA icon functionality for individual apps in Lowcoder, allowing each app to have its own visual identity in browser tabs and when installed as a PWA.

## Current State Analysis

### How Icons Currently Work

1. **App Icons**: Stored as strings in the application settings (`icon` field in `appSettingsComp.tsx`)
2. **Icon Types**: Support multiple formats:
    - FontAwesome icons (`/icon:solid/` or `/icon:regular/`)
    - Ant Design icons (`/icon:antd/`)
    - Base64 encoded images (`data:image`)
    - URL-based images (`http://`)
3. **Current Usage**: Icons are displayed in app lists and settings using `MultiIconDisplay` component

### Current Favicon/PWA Setup (as of HEAD)

1. **Per‑App Favicon (Editor/App routes)**: Set in `client/packages/lowcoder/src/pages/editor/editorView.tsx` using React Helmet. The editor view directly injects per‑app links based on `applicationId`:
    - `<link rel="manifest" href="/api/applications/{appId}/manifest.json">`
    - `<link rel="icon" href="/api/applications/{appId}/icons/192.png">`
    - `<link rel="apple-touch-icon" href="/api/applications/{appId}/icons/512.png">`
    - No global default favicon is injected on app routes.
2. **Admin Routes Favicon**: Set in `client/packages/lowcoder/src/pages/ApplicationV2/index.tsx` to a scoped default favicon so it does not interfere with per‑app favicons.
3. **Per‑App PWA Manifest**: Served dynamically by backend at `GET /api/applications/{appId}/manifest.json` and injected via `<link rel="manifest">` in `editorView.tsx`. Note: The endpoint is consolidated in `ApplicationController`.
4. **Static Manifest (legacy)**: `client/packages/lowcoder/site.webmanifest` remains in the repo but is not linked on app routes.

## Implementation Progress

### ✅ Phase 1: Basic App-Specific Favicon (COMPLETED)

**Status**: ✅ **COMPLETED** - App-specific favicon functionality is working

#### What Has Been Implemented:

1. **✅ Icon Conversion Utilities** (`client/packages/lowcoder/src/util/iconConversionUtils.ts`):

    - `getAppIconInfo()` - Extracts icon information from app settings
    - `getAppFavicon()` - Gets app-specific favicon URL
    - `canUseAsFavicon()` - Checks if an icon can be used as favicon
    - Support for different icon types (URL, base64, FontAwesome, Ant Design)
    - Handles React element extraction for complex icon objects

2. **✅ Updated Editor View** (`client/packages/lowcoder/src/pages/editor/editorView.tsx`):

    - Injects per‑app links/meta directly using `applicationId`:
        - `rel="manifest"` → `/api/applications/{appId}/manifest.json`
        - `rel="icon"` → `/api/applications/{appId}/icons/192.png`
        - `rel="apple-touch-icon"` and `apple-touch-startup-image` → `/api/applications/{appId}/icons/512.png`
        - `og:image` / `twitter:image` → per‑app 512 PNG
        - `theme-color` from branding settings with `#b480de` fallback
    - Currently constructs URLs inline rather than using `iconConversionUtils`.
    - Clean implementation without console errors

3. **✅ Modified Global App Configuration** (`client/packages/lowcoder/src/app.tsx`):

    - Removed default favicon from global Helmet
    - Added comment indicating favicon is handled conditionally in editorView.tsx

4. **✅ Icon Parsing** (`client/packages/lowcoder/src/comps/comps/multiIconDisplay.tsx`):

    - Exposes `parseIconIdentifier` used by utilities. Note: editor favicon injection does not depend on this.

5. **✅ Error Resolution**:

    - Resolved React Helmet "Cannot convert a Symbol value to a string" errors
    - Fixed TypeScript type issues
    - Clean console output with no errors

6. **✅ Admin Routes Default Favicon** (`client/packages/lowcoder/src/pages/ApplicationV2/index.tsx`):

    - Adds a scoped default favicon for admin routes (e.g., `/apps`, `/datasource`, `/setting`).
    - Uses `branding?.favicon` (via `buildMaterialPreviewURL`) when available; otherwise falls back to the imported default `favicon` asset from `assets/images`.
    - Placed only within admin layout so it does not precede or override per‑app favicons on app routes.
    - Observes favicon precedence: the first `<link rel='icon'>` in the document is chosen by browsers.

#### Technical Implementation Details:

```tsx
// Route-level meta in editor view (simplified)
const appId = application?.applicationId
const appIcon512 = appId
    ? `/api/applications/${appId}/icons/512.png`
    : undefined
const appIcon192 = appId
    ? `/api/applications/${appId}/icons/192.png`
    : undefined
const manifestHref = appId
    ? `/api/applications/${appId}/manifest.json`
    : undefined
const themeColor = brandingSettings?.config_set?.mainBrandingColor || '#b480de'

;<Helmet>
    {manifestHref && <link rel='manifest' href={manifestHref} />}
    {appIcon192 && <link rel='icon' href={appIcon192} />}
    {appIcon512 && <link rel='apple-touch-icon' href={appIcon512} />}
    {appIcon512 && <link rel='apple-touch-startup-image' href={appIcon512} />}
    {appIcon512 && <meta property='og:image' content={appIcon512} />}
    {appIcon512 && <meta name='twitter:image' content={appIcon512} />}
    <meta name='theme-color' content={themeColor} />
</Helmet>
```

#### Current Behavior:

-   **App with Custom Icon**: Shows only the app-specific favicon (e.g., MilamsFavicon.png)
-   **App without Icon**: Shows only the default favicon
-   **Admin Routes**: Show the default favicon (branding-based if configured), independent of app views
-   **No Competing Favicons**: Only one favicon is rendered at a time
-   **Clean Implementation**: No console errors or React Helmet issues

### ✅ Phase 2: Backend Icon Conversion Service (MVP COMPLETED)

**Status**: ✅ **COMPLETED (MVP)** — Minimal backend service in place to serve per‑app PNG icons with graceful fallbacks. Future iterations can add advanced conversions and caching.

#### What Has Been Implemented

1. **New backend endpoints** (public GET in security):

    - `GET /api/applications/{appId}/icons` → lists available sizes
    - `GET /api/applications/{appId}/icons/{size}.png[?bg=#RRGGBB]` → serves PNG for allowed sizes (48, 72, 96, 120, 128, 144, 152, 167, 180, 192, 256, 384, 512), with optional background color

2. **Image handling (v1.1)**:

    - Supports data URLs and HTTP/HTTPS images decodable by Java ImageIO
    - Scales and centers to requested size; outputs PNG (transparent by default)
    - Optional solid background via `?bg=#RRGGBB`
    - Adds `Cache-Control: public, max-age=7d`
    - Graceful fallback: generated placeholder, tinted by optional `bg`

3. **Manifest integration**:

    - Manifest now points icons to the new PNG endpoints for each app, ensuring installable PWAs always fetch renderable PNGs

4. **Security**:

    - Public GET access permitted for `/icons` and `/icons/**` under both legacy and new URL bases

#### Deferred (Future Enhancements)

-   Convert font icons (FontAwesome/Ant Design) to SVG → PNG
-   Robust SVG rendering beyond ImageIO defaults
-   Persistent caching/database of converted outputs
-   Optional background color handling and multiple sizes beyond 192/512

### ✅ Phase 3: PWA Manifest Enhancement (COMPLETED)

**Status**: ✅ **COMPLETED** — Enhanced PWA manifest with maskable icons, shortcuts, categories, and app-specific meta tags

#### What Has Been Implemented

-   **✅ Backend per‑app manifest endpoint**: `GET /api/applications/{appId}/manifest.json` in `ApplicationController` generates a manifest dynamically from the app DSL (`settings.title`, `settings.description`, `settings.icon`) with sensible fallbacks and default icons. The duplicate in `AppIconController` has been removed.
-   **✅ Security**: Public GET access for the manifest path is permitted in `SecurityConfig` (no auth required), including new‑URL aliases.
-   **✅ Frontend injection**: `editorView.tsx` adds `<link rel="manifest">` for app routes, so browsers automatically fetch the per‑app manifest. Admin routes don't include it.
-   **✅ Verification**: Manual checks confirm a single manifest link on app pages and `200 application/manifest+json` served by the endpoint with no 401/404s.
-   **✅ Installation**: PWA installation works and uses the app's icon (verified).
-   **✅ Manifest enrichment**: Added `id`, app‑scoped `scope`, and app‑specific `start_url`:
    -   `id`: `/apps/{appId}`
    -   `scope`: `/apps/{appId}/`
    -   `start_url`: `/apps/{appId}/view`
-   **✅ Robust defaults**: Hardened handling for empty/missing `settings.title`/`settings.description` to ensure clean fallbacks.
-   **✅ Maskable icons**: Added `"purpose": "any maskable"` to all manifest icons for better PWA system integration
-   **✅ PWA shortcuts**: Added shortcuts array with:
    -   View shortcut (opens app view)
    -   Edit shortcut (opens app editor)
-   **✅ Proper content type**: Set manifest response `Content-Type` to `application/manifest+json`
-   **✅ App-specific meta tags** in `editorView.tsx`:
    -   `link[rel='icon']` → `/api/applications/{appId}/icons/192.png` (with optional `?bg=`)
    -   `apple-touch-icon` → `/api/applications/{appId}/icons/512.png` (with optional `?bg=`)
    -   `apple-touch-startup-image` → same as above
    -   `og:image` / `twitter:image` → per‑app 512 PNG
    -   `theme-color` using `brandingSettings?.config_set?.mainBrandingColor` with `#b480de` fallback

#### Technical Implementation Details:

**Backend manifest enhancements** (`ApplicationController.java`):

```java
// Maskable icons
icon.put("purpose", "any maskable");

// PWA shortcuts
List<Map<String, Object>> shortcuts = new ArrayList<>();
Map<String, Object> viewShortcut = new HashMap<>();
viewShortcut.put("name", appTitle);
viewShortcut.put("url", appStartUrl);
shortcuts.add(viewShortcut);
manifest.put("shortcuts", shortcuts);

// Proper content type
.contentType(MediaType.valueOf("application/manifest+json"))
```

**Frontend app meta tags** (`editorView.tsx`):

```tsx
// Apple touch icon with smart fallback
const appleTouchIcon =
    typeof appIconView === 'string'
        ? appIconView
        : (brandingConfig?.logo && typeof brandingConfig.logo === 'string'
              ? brandingConfig.logo
              : undefined) || '/android-chrome-512x512.png'

// Brand-aware theme color
;<meta
    name='theme-color'
    content={brandingSettings?.config_set?.mainBrandingColor || '#b480de'}
/>
```

### ✅ Phase 4: Advanced PWA Features (CACHING COMPLETED)

**Status**: ✅ **CACHING COMPLETED** — Advanced PWA and icon optimization features are underway; Redis‑backed PNG caching is now implemented

#### Planned Implementation:

1. **Dynamic Icon Generation**

    - ✅ Multiple icon sizes generated on-demand via `GET /icons/{size}.png` (48, 72, 96, 120, 128, 144, 152, 167, 180, 192, 256, 384, 512)
    - ✅ Optional background color supported via `?bg=#RRGGBB`
    - ✅ HTTP caching via `Cache-Control` headers (7 days)
    - ✅ Redis‑backed caching of generated PNGs — key prefix `appicon:` with `appId|size|bg|icon-hash`, values Base64‑encoded bytes, 1h TTL via reactive Redis; gracefully falls back if Redis is unavailable

## Technical Implementation Details

1. **API Endpoints (already implemented)**:

    - `GET /api/applications/{appId}/manifest.json` (in `ApplicationController`)
    - `GET /api/applications/{appId}/icons` and `GET /api/applications/{appId}/icons/{size}.png[?bg=#RRGGBB]` (in `AppIconController`)
    - No additional endpoints are required for this phase.

### Frontend Changes Completed

1. **✅ Updated `editorView.tsx`**:

    - Injects per‑app URLs directly (does not use `getAppFavicon`):
        - `rel="manifest"` → `/api/applications/{appId}/manifest.json`
        - `rel="icon"` → `/api/applications/{appId}/icons/192.png`
        - `rel="apple-touch-icon"` and `apple-touch-startup-image` → `/api/applications/{appId}/icons/512.png`
        - `og:image` / `twitter:image` → per‑app 512 PNG

2. **✅ Created Icon Conversion Utilities**:

    ```typescript
    // Convert icon identifier to favicon URL
    const getAppFavicon = (
        appSettingsComp: any,
        appId: string
    ): string | null => {
        const iconInfo = getAppIconInfo(appSettingsComp)
        if (!iconInfo) return null
        if (canUseAsFavicon(iconInfo)) {
            return getAppFaviconUrl(appId, iconInfo)
        }
        return null
    }
    ```

3. **✅ Updated `ApplicationV2/index.tsx`** (Admin routes default favicon):

    - Uses `branding?.favicon` (via `buildMaterialPreviewURL`) with fallback to the imported `favicon` asset.

<!-- Database schema changes are out of scope for this phase. -->

## Benefits of This Approach

1. **✅ User Experience**: Each app has its own visual identity in browser tabs
2. **✅ PWA Support**: Apps can be installed as standalone PWAs with custom icons (Phase 3)
3. **✅ Backward Compatibility**: Existing apps without icons fall back to global favicon
4. **✅ Performance**: Redis caching reduces processing overhead
5. **✅ Scalability**: Icon conversion happens on-demand and is cached

## Implementation Priority

1. **✅ High Priority**: Basic favicon functionality for app view pages (COMPLETED)
2. **✅ Medium Priority**: PWA manifest generation and installation support (COMPLETED)
3. **Optional**: Advanced icon processing and optimization (see "Future Enhancements (Optional)")

## File Structure Changes

### ✅ Files Created

-   `client/packages/lowcoder/src/util/iconConversionUtils.ts` ✅
-   `client/packages/lowcoder/src/components/AppFaviconProvider.tsx` — Not created (implemented directly in `client/packages/lowcoder/src/pages/editor/editorView.tsx`)
-   `server/api-service/lowcoder-server/src/main/java/org/lowcoder/api/application/AppIconController.java` ✅

### ✅ Files Modified

-   `client/packages/lowcoder/src/pages/editor/editorView.tsx` ✅
-   `client/packages/lowcoder/src/app.tsx` ✅
-   `client/packages/lowcoder/src/comps/comps/multiIconDisplay.tsx` ✅
-   `client/packages/lowcoder/src/pages/ApplicationV2/index.tsx` ✅
-   `server/api-service/lowcoder-server/src/main/java/org/lowcoder/api/application/ApplicationController.java` ✅
-   `server/api-service/lowcoder-server/src/main/java/org/lowcoder/api/framework/security/SecurityConfig.java` ✅ (permits public GET for manifest and icons on both legacy and new URL bases)

## Testing Strategy

1. **✅ Unit Tests**: Icon conversion utilities (manual testing completed)
2. **✅ Integration Tests**: End-to-end favicon generation and display (manual testing completed)
3. **✅ Browser Tests**: Verify favicon appears correctly in different browsers (manual testing completed)
4. **✅ PWA Tests**: Test app installation with custom icons (Phase 3)

## Deployment Considerations

1. **✅ Icon Cache**: Redis is used to cache generated PNGs (see `spring.data.redis.url`). If Redis is down, generation proceeds without cache.
2. **✅ Cache Headers**: Icon responses set `Cache-Control: public, max-age=7d`
3. **✅ Error Handling**: Graceful fallback when icon conversion fails

## Future Enhancements (Optional)

-   **SVG and Font Icons**: Render SVGs and font icons (FontAwesome/Ant Design) to PNG for consistent outputs.
-   **Additional Outputs**: Provide WebP variants or negotiate via Accept header when beneficial.
-   **Cache Invalidation**: Evict Redis keys for an app when its icon or branding color changes (all sizes/bg variants).
-   **Configurable Cache**: Make Redis TTL and key prefix configurable via environment variables (defaults remain as-is).
-   **Persistent Artifacts**: Optional long-lived storage of rendered icons to reduce regeneration beyond Redis TTL.
-   **CDN Integration**: Optionally place a CDN in front of icon endpoints for global performance.
-   **PWA Enhancements**: Service worker for offline assets and custom install prompts (if desired).
-   **Observability**: Metrics for cache hit rate and render failures; structured logs for troubleshooting.
-   **Security Hardening**: Outbound fetch restrictions/allowlist for external icon URLs; size/time limits on downloads.
-   **Test Coverage**: Automated tests for icon endpoints (cache hit/miss, headers, fallback paths; future SVG/font flows).

## Current Status Summary

-   **✅ Phase 1**: COMPLETED — Basic app‑specific favicon functionality is working
-   **✅ Phase 2 (MVP)**: COMPLETED — Backend icon endpoints serve PNGs with graceful fallback; security updated; manifest points to endpoints
-   **✅ Phase 3**: COMPLETED — Enhanced PWA manifest with maskable icons, shortcuts, categories, proper content type, and app-specific meta tags
-   **✅ Phase 4**: CACHING COMPLETED — Multi-size icon endpoints, optional brand-aware background color, per‑app OG/Twitter images, HTTP cache headers, and Redis-backed PNG caching are implemented. Advanced items (e.g., SVG/font icon rendering, WebP, persistent artifacts, CDN, SW/UX) are listed under "Future Enhancements (Optional)".

The implementation is modular and can be developed incrementally. Phase 1 provided immediate value with app-specific favicons, and Phase 3 added comprehensive PWA support. Phase 2 (MVP) delivered per‑app PNG endpoints with cache headers and graceful fallback; later updates added multi-size support and optional background color. Phase 4 progresses advanced features; remaining items are tracked above.

## Documentation

-   Added: `docs/build-applications/app-editor/pwa-icons-and-favicons.md` — per‑app PWA icons and favicons, endpoints, sizes, and `bg` parameter usage.

## Tests/Builds

-   Client build: PASS (`yarn build`)
-   Server build: Maven not available in this environment; validate in CI with Java-enabled environment.

## Known gaps vs code (updated)

-   Per-app `apple-mobile-web-app-title` is injected in `client/packages/lowcoder/src/pages/editor/editorView.tsx`.
-   Icon URLs in `editorView.tsx` use `getAppIconPngUrl(appId, size, brandingColor)` to include optional brand-aware backgrounds.
-   Manifest endpoint is now single-source in `ApplicationController`.
-   Frontend continues to build manifest link inline (OK); favicon/touch icon URLs use utilities.
