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
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormActionFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;

/**
 * @author Anders Jacobsson
 * @since 2.0
 */
public class TurnstileFormActionFactory implements FormActionFactory {

    private static final Logger log = Logger.getLogger(TurnstileFormActionFactory.class);
    private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.DISABLED
    };

    private String siteKey;
    private TurnstileSiteverify siteverify;

    private static String valueOrNull(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override
    public FormAction create(final KeycloakSession keycloakSession) {
        return new TurnstileFormAction(siteKey, siteverify);
    }

    @Override
    public void init(final Config.Scope scope) {
        final String configuredSiteKey = valueOrNull(scope.get("site-key"));
        final String secretKey = valueOrNull(scope.get("secret-key"));

        if (configuredSiteKey == null || secretKey == null) {
            log.warn("No Turnstile site key or secret key configured, registration is not protected by a CAPTCHA");
            return;
        }

        siteKey = configuredSiteKey;
        siteverify = new TurnstileSiteverify(secretKey);
    }

    @Override
    public void postInit(final KeycloakSessionFactory keycloakSessionFactory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return TurnstileFormAction.ID;
    }

    @Override
    public String getDisplayType() {
        return "FGV Turnstile";
    }

    @Override
    public String getReferenceCategory() {
        return null;
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Requires a Cloudflare Turnstile challenge to be solved before registration";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of();
    }
}
