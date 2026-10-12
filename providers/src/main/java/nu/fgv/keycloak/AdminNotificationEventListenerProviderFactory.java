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
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

import java.util.Arrays;
import java.util.List;

/**
 * @author Anders Jacobsson
 * @since 2.0
 */
public class AdminNotificationEventListenerProviderFactory implements EventListenerProviderFactory {

    private static final Logger log = Logger.getLogger(AdminNotificationEventListenerProviderFactory.class);
    private static final String ID = "fgv-admin-notification";

    private List<String> recipients = List.of();
    private ApprovalPolicy policy;

    @Override
    public EventListenerProvider create(final KeycloakSession keycloakSession) {
        return new AdminNotificationEventListenerProvider(keycloakSession, recipients, policy);
    }

    @Override
    public void init(final Config.Scope scope) {
        final String value = scope.get("recipients", "");

        policy = ApprovalPolicy.from(scope);
        recipients = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(recipient -> !recipient.isEmpty())
                .toList();

        if (recipients.isEmpty()) {
            log.warn("No recipients configured, administrators will not be notified about new users");
        }
    }

    @Override
    public void postInit(final KeycloakSessionFactory keycloakSessionFactory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return ID;
    }
}
