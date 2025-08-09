package org.example.branding.util;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.lowcoder.plugin.api.data.EndpointResponse;

public final class Responses {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Map<String, List<String>> JSON_HEADERS = Map.of(
        "Content-Type", List.of("application/json")
    );

    private Responses() {}

    public static EndpointResponse ok(Object body) {
        return new SimpleEndpointResponse(200, toBytes(body), JSON_HEADERS, Collections.emptyMap());
    }

    public static EndpointResponse status(int code, Object body) {
        return new SimpleEndpointResponse(code, toBytes(body), JSON_HEADERS, Collections.emptyMap());
    }

    private static byte[] toBytes(Object body) {
        if (body == null) return null;
        if (body instanceof byte[] b) return b;
        if (body instanceof String s) return s.getBytes(StandardCharsets.UTF_8);
        try {
            return mapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            return String.valueOf(body).getBytes(StandardCharsets.UTF_8);
        }
    }

    private static final class SimpleEndpointResponse implements EndpointResponse {
        private final int statusCode;
        private final byte[] body;
        private final Map<String, List<String>> headers;
        private final Map<String, List<Map.Entry<String, String>>> cookies;

        private SimpleEndpointResponse(int statusCode, byte[] body,
                                       Map<String, List<String>> headers,
                                       Map<String, List<Map.Entry<String, String>>> cookies) {
            this.statusCode = statusCode;
            this.body = body;
            this.headers = headers;
            this.cookies = cookies;
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public byte[] body() {
            return body;
        }

        @Override
        public Map<String, List<String>> headers() {
            return headers;
        }

        @Override
        public Map<String, List<Map.Entry<String, String>>> cookies() {
            return cookies;
        }
    }
}