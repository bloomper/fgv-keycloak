/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nu.fgv.keycloak;

import org.jboss.logging.Logger;
import org.keycloak.util.JsonSerialization;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Anders Jacobsson
 * @since 2.0
 */
public class TurnstileSiteverify {

    static final URI ENDPOINT = URI.create("https://challenges.cloudflare.com/turnstile/v0/siteverify");

    private static final Logger log = Logger.getLogger(TurnstileSiteverify.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final URI endpoint;
    private final String secretKey;

    public TurnstileSiteverify(final String secretKey) {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), ENDPOINT, secretKey);
    }

    TurnstileSiteverify(final HttpClient httpClient, final URI endpoint, final String secretKey) {
        this.httpClient = httpClient;
        this.endpoint = endpoint;
        this.secretKey = secretKey;
    }

    private static String encode(final Map<String, String> parameters) {
        return parameters.entrySet().stream()
                .map(e -> "%s=%s".formatted(e.getKey(), URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)))
                .collect(Collectors.joining("&"));
    }

    public boolean verify(final String token, final String remoteIp) {
        final Map<String, String> parameters = new LinkedHashMap<>();

        parameters.put("secret", secretKey);
        parameters.put("response", token);
        if (remoteIp != null && !remoteIp.isBlank()) {
            parameters.put("remoteip", remoteIp);
        }

        final HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encode(parameters)))
                .build();

        try {
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warnf("Turnstile siteverify answered with status %d", response.statusCode());
                return false;
            }

            final Map<?, ?> result = JsonSerialization.readValue(response.body(), Map.class);

            if (Boolean.TRUE.equals(result.get("success"))) {
                return true;
            }

            log.debugf("Turnstile rejected the response: %s", result.get("error-codes"));
            return false;
        } catch (final IOException e) {
            log.warn("Could not verify the Turnstile response", e);
            return false;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
