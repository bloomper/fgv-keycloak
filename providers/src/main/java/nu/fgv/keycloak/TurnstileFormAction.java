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

import jakarta.ws.rs.core.MultivaluedMap;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;

import java.util.List;

/**
 * @author Anders Jacobsson
 * @since 2.0
 */
public class TurnstileFormAction implements FormAction {

    public static final String ID = "fgv-turnstile";
    static final String SCRIPT = "https://challenges.cloudflare.com/turnstile/v0/api.js?compat=recaptcha";
    static final String RESPONSE_FIELD = "g-recaptcha-response";
    static final String FAILED_MESSAGE = "recaptchaFailed";
    static final String ACTION = "register";

    private final String siteKey;
    private final TurnstileSiteverify siteverify;

    public TurnstileFormAction(final String siteKey, final TurnstileSiteverify siteverify) {
        this.siteKey = siteKey;
        this.siteverify = siteverify;
    }

    private boolean isEnabled() {
        return siteKey != null && siteverify != null;
    }

    @Override
    public void buildPage(final FormContext context, final LoginFormsProvider form) {
        if (!isEnabled()) {
            return;
        }

        form.setAttribute("recaptchaRequired", true);
        form.setAttribute("recaptchaVisible", true);
        form.setAttribute("recaptchaSiteKey", siteKey);
        form.setAttribute("recaptchaAction", ACTION);
        form.addScript(SCRIPT);
    }

    @Override
    public void validate(final ValidationContext context) {
        if (!isEnabled()) {
            context.success();
            return;
        }

        final MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        final String token = formData.getFirst(RESPONSE_FIELD);

        if (token != null && !token.isBlank() && siteverify.verify(token, context.getConnection().getRemoteAddr())) {
            context.success();
            return;
        }

        formData.remove(RESPONSE_FIELD);
        context.error(Errors.INVALID_REGISTRATION);
        context.validationError(formData, List.of(new FormMessage(null, FAILED_MESSAGE)));
        context.excludeOtherErrors();
    }

    @Override
    public void success(final FormContext context) {
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(final KeycloakSession session, final RealmModel realm, final UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(final KeycloakSession session, final RealmModel realm, final UserModel user) {
    }

    @Override
    public void close() {
    }
}
