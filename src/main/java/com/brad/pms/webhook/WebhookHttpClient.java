package com.brad.pms.webhook;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

public interface WebhookHttpClient {
    int post(URI uri, byte[] body, Map<String, String> headers, int timeoutMs) throws IOException;
}
