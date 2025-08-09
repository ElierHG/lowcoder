package org.example.environments.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.lowcoder.plugin.api.data.EndpointResponse;

public final class Responses {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Responses() {}

    public static EndpointResponse ok(Object body) {
        return withStatus(200, body);
    }

    public static EndpointResponse badRequest(Object body) {
        return withStatus(400, body);
    }

    public static EndpointResponse notFound() {
        return of(404, Collections.emptyMap(), Collections.emptyMap(), null);
    }

    public static EndpointResponse of(int status, Map<String, List<String>> headers,
                                      Map<String, List<Entry<String,String>>> cookies, byte[] body) {
        return new SimpleResponse(status, headers, cookies, body);
    }

    public static EndpointResponse withStatus(int status, Object bodyObj) {
        byte[] bytes = null;
        if (bodyObj != null) {
            try {
                if (bodyObj instanceof byte[] b) {
                    bytes = b;
                } else if (bodyObj instanceof String s) {
                    bytes = s.getBytes(StandardCharsets.UTF_8);
                } else {
                    bytes = MAPPER.writeValueAsBytes(bodyObj);
                }
            } catch (Exception ignore) {
                bytes = null;
            }
        }
        return of(status, Collections.emptyMap(), Collections.emptyMap(), bytes);
    }

    private static class SimpleResponse implements EndpointResponse {
        private final int status;
        private final Map<String, List<String>> headers;
        private final Map<String, List<Entry<String, String>>> cookies;
        private final byte[] body;

        private SimpleResponse(int status,
                              Map<String, List<String>> headers,
                              Map<String, List<Entry<String, String>>> cookies,
                              byte[] body) {
            this.status = status;
            this.headers = headers;
            this.cookies = cookies;
            this.body = body;
        }

        @Override
        public int statusCode() { return status; }

        @Override
        public Map<String, List<String>> headers() { return headers; }

        @Override
        public Map<String, List<Entry<String, String>>> cookies() { return cookies; }

        @Override
        public byte[] body() { return body; }
    }
}