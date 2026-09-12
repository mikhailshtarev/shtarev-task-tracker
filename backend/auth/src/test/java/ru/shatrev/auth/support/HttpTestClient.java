package ru.shatrev.auth.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/** Простой HTTP-клиент для интеграционных тестов: полный контроль над заголовками и cookie. */
public class HttpTestClient {

    public record SimpleResponse(int status, String body, HttpResponse<String> raw) {

        public String cookieValue(String name) {
            return raw.headers().allValues("set-cookie").stream()
                    .filter(header -> header.startsWith(name + "="))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No " + name + " cookie in response"));
        }
    }

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String baseUrl;

    public HttpTestClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public SimpleResponse get(String path, Map<String, String> headers) {
        return send(HttpRequest.newBuilder().uri(URI.create(baseUrl + path)).GET(), headers);
    }

    public SimpleResponse post(String path, Map<String, String> headers) {
        return send(HttpRequest.newBuilder().uri(URI.create(baseUrl + path))
                .POST(HttpRequest.BodyPublishers.noBody()), headers);
    }

    public SimpleResponse postJson(String path, Object body, Map<String, String> headers) {
        try {
            String json = objectMapper.writeValueAsString(body);
            return send(HttpRequest.newBuilder().uri(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json)), headers);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public Map<String, Object> json(SimpleResponse response) {
        try {
            return objectMapper.readValue(response.body(), new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse JSON: " + response.body(), e);
        }
    }

    private SimpleResponse send(HttpRequest.Builder builder, Map<String, String> headers) {
        headers.forEach(builder::header);
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new SimpleResponse(response.statusCode(), response.body(), response);
        } catch (Exception e) {
            throw new IllegalStateException("HTTP call failed: " + builder.build().uri(), e);
        }
    }
}
