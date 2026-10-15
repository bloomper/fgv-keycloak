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

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * @author Anders Jacobsson
 * @since 2.0
 */
class TurnstileSiteverifyTest {

    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private HttpServer server;
    private int status;
    private String responseBody;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/siteverify", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            final byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private TurnstileSiteverify siteverify() {
        return new TurnstileSiteverify(HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:%d/siteverify".formatted(server.getAddress().getPort())), "s3cr3t+key");
    }

    @Test
    void should_accept_successful_response() {
        status = 200;
        responseBody = "{\"success\":true,\"error-codes\":[]}";

        assertThat(siteverify().verify("a token", "192.0.2.1"), equalTo(true));
        assertThat(requestBody.get(), equalTo("secret=s3cr3t%2Bkey&response=a+token&remoteip=192.0.2.1"));
    }

    @Test
    void should_reject_unsuccessful_response() {
        status = 200;
        responseBody = "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}";

        assertThat(siteverify().verify("token", null), equalTo(false));
        assertThat(requestBody.get(), equalTo("secret=s3cr3t%2Bkey&response=token"));
    }

    @Test
    void should_reject_when_turnstile_fails() {
        status = 500;
        responseBody = "";

        assertThat(siteverify().verify("token", "192.0.2.1"), equalTo(false));
    }

    @Test
    void should_reject_when_turnstile_is_unreachable() {
        server.stop(0);

        assertThat(siteverify().verify("token", "192.0.2.1"), equalTo(false));
    }
}
