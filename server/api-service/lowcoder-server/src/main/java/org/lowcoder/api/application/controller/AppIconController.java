package org.lowcoder.api.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.lowcoder.domain.application.service.ApplicationRecordService;
import org.lowcoder.domain.application.service.ApplicationService;
import org.lowcoder.api.util.GidService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal per-app icon conversion and serving endpoints.
 * Supports PNG/JPEG data URLs and HTTP/HTTPS images that ImageIO can decode.
 * Scales to requested size and returns image/png.
 */
@RestController
@RequestMapping({"/api/v1/applications", "/api/applications"})
@RequiredArgsConstructor
public class AppIconController {

    private final ApplicationRecordService applicationRecordService;
    private final ApplicationService applicationService;
    private final GidService gidService;

    private final WebClient webClient = WebClient.builder().build();

    @GetMapping("/{applicationId}/icons")
    public Mono<ResponseEntity<String>> listIcons(@PathVariable String applicationId) {
        // Provide a simple JSON with URLs for common sizes
        Map<String, Object> body = new HashMap<>();
        Map<String, String> icons = new HashMap<>();
        icons.put("192", String.format("/api/applications/%s/icons/192.png", applicationId));
        icons.put("512", String.format("/api/applications/%s/icons/512.png", applicationId));
        body.put("icons", icons);
        try {
            return Mono.just(ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ObjectMapper().writeValueAsString(body)));
        } catch (JsonProcessingException e) {
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{}"));
        }
    }

    @GetMapping("/{applicationId}/icons/{size}.png")
    public Mono<ResponseEntity<byte[]>> getIconPng(
            @PathVariable String applicationId,
            @PathVariable int size
    ) {
        if (size <= 0 || (size != 192 && size != 512)) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
        }

        return gidService.convertApplicationIdToObjectId(applicationId)
                .flatMap(appId -> applicationRecordService.getLatestRecordByApplicationId(appId)
                        .map(record -> record.getApplicationDSL())
                        .switchIfEmpty(applicationService.findById(appId).map(app -> app.getEditingApplicationDSL()))
                        .onErrorResume(__ -> Mono.empty())
                )
                .flatMap(dsl -> {
                    try {
                        Map<String, Object> safeDsl = dsl == null ? new HashMap<>() : dsl;
                        Map<String, Object> settings = (Map<String, Object>) safeDsl.get("settings");
                        String icon = settings != null && settings.get("icon") instanceof String
                                ? (String) settings.get("icon")
                                : null;
                        return renderIconBytes(icon, size)
                                .onErrorResume(__ -> Mono.empty());
                    } catch (Exception ignore) {
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(generateFallback(size))
                .map(bytes -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.IMAGE_PNG);
                    headers.setCacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic());
                    return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
                });
    }

    private Mono<byte[]> renderIconBytes(String iconSource, int size) {
        if (iconSource == null || iconSource.isBlank()) {
            return Mono.empty();
        }
        // data URL
        if (iconSource.startsWith("data:")) {
            return decodeDataUrl(iconSource)
                    .flatMap(bytes -> scaleToPng(bytes, size));
        }
        // http/https URL
        try {
            URI uri = URI.create(iconSource);
            String scheme = uri.getScheme();
            if (scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return webClient.get()
                        .uri(iconSource)
                        .retrieve()
                        .bodyToMono(byte[].class)
                        .flatMap(bytes -> scaleToPng(bytes, size))
                        .onErrorResume(__ -> Mono.empty());
            }
        } catch (IllegalArgumentException ignored) {
            // fall through to empty
        }
        // Unsupported source; give up
        return Mono.empty();
    }

    private Mono<byte[]> decodeDataUrl(String dataUrl) {
        try {
            int comma = dataUrl.indexOf(',');
            if (comma < 0) return Mono.empty();
            String meta = dataUrl.substring(5, comma); // after "data:"
            String data = dataUrl.substring(comma + 1);
            boolean isBase64 = meta.contains(";base64");
            byte[] bytes = isBase64 ? Base64.getDecoder().decode(data) : data.getBytes();
            return Mono.just(bytes);
        } catch (Exception e) {
            return Mono.empty();
        }
    }

    private Mono<byte[]> scaleToPng(byte[] inputBytes, int size) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(inputBytes));
            if (src == null) return Mono.empty();
            int target = size;
            BufferedImage dst = new BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = dst.createGraphics();
            try {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                // cover fit while preserving aspect ratio, centered
                double scale = Math.min((double) target / src.getWidth(), (double) target / src.getHeight());
                int newW = (int) Math.round(src.getWidth() * scale);
                int newH = (int) Math.round(src.getHeight() * scale);
                int x = (target - newW) / 2;
                int y = (target - newH) / 2;
                g2.drawImage(src, x, y, newW, newH, null);
            } finally {
                g2.dispose();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(dst, "PNG", baos);
            return Mono.just(baos.toByteArray());
        } catch (Exception e) {
            return Mono.empty();
        }
    }

    private Mono<byte[]> generateFallback(int size) {
        // Simple solid background with centered circle; brand-agnostic
        int s = size;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // background
            g2.setColor(new Color(180, 128, 222)); // #b480de default theme
            g2.fillRect(0, 0, s, s);
            // circle
            g2.setColor(Color.WHITE);
            int pad = Math.max(8, s / 10);
            g2.fillOval(pad, pad, s - pad * 2, s - pad * 2);
        } finally {
            g2.dispose();
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            return Mono.just(baos.toByteArray());
        } catch (Exception e) {
            return Mono.just(new byte[0]);
        }
    }
}


