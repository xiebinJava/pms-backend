package com.brad.pms.webhook;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class JdkWebhookHttpClient implements WebhookHttpClient {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Override
    public int post(URI uri, byte[] body, Map<String, String> headers, int timeoutMs) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        headers.forEach(builder::header);
        try {
            return client.send(builder.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("webhook interrupted", ex);
        }
    }
}
