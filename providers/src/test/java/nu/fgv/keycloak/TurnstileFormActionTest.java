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

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.common.ClientConnection;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.utils.FormMessage;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Anders Jacobsson
 * @since 2.0
 */
class TurnstileFormActionTest {

    private final TurnstileSiteverify siteverify = mock(TurnstileSiteverify.class);
    private final TurnstileFormAction action = new TurnstileFormAction("site-key", siteverify);
    private final ValidationContext context = mock(ValidationContext.class);
    private final HttpRequest httpRequest = mock(HttpRequest.class);
    private final ClientConnection connection = mock(ClientConnection.class);
    private final MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();

    @BeforeEach
    void setUp() {
        when(context.getHttpRequest()).thenReturn(httpRequest);
        when(context.getConnection()).thenReturn(connection);
        when(httpRequest.getDecodedFormParameters()).thenReturn(formData);
        when(connection.getRemoteAddr()).thenReturn("192.0.2.1");
    }

    @Test
    void should_add_widget_to_registration_page() {
        final LoginFormsProvider form = mock(LoginFormsProvider.class);

        action.buildPage(mock(FormContext.class), form);

        verify(form).setAttribute("recaptchaRequired", true);
        verify(form).setAttribute("recaptchaVisible", true);
        verify(form).setAttribute("recaptchaSiteKey", "site-key");
        verify(form).setAttribute("recaptchaAction", TurnstileFormAction.ACTION);
        verify(form).addScript(TurnstileFormAction.SCRIPT);
    }

    @Test
    void should_accept_verified_response() {
        formData.putSingle(TurnstileFormAction.RESPONSE_FIELD, "token");
        when(siteverify.verify("token", "192.0.2.1")).thenReturn(true);

        action.validate(context);

        verify(context).success();
        verify(context, never()).validationError(any(), anyList());
    }

    @Test
    void should_reject_rejected_response() {
        formData.putSingle(TurnstileFormAction.RESPONSE_FIELD, "token");
        when(siteverify.verify("token", "192.0.2.1")).thenReturn(false);

        action.validate(context);

        verifyRejected();
    }

    @Test
    void should_reject_missing_response_without_calling_turnstile() {
        action.validate(context);

        verify(siteverify, never()).verify(anyString(), any());
        verifyRejected();
    }

    @Test
    void should_do_nothing_when_not_configured() {
        final TurnstileFormAction unconfigured = new TurnstileFormAction(null, null);
        final LoginFormsProvider form = mock(LoginFormsProvider.class);

        unconfigured.buildPage(mock(FormContext.class), form);
        unconfigured.validate(context);

        verify(form, never()).addScript(anyString());
        verify(context).success();
    }

    @SuppressWarnings("unchecked")
    private void verifyRejected() {
        final ArgumentCaptor<List<FormMessage>> errors = ArgumentCaptor.forClass(List.class);

        verify(context).error(Errors.INVALID_REGISTRATION);
        verify(context).validationError(eq(formData), errors.capture());
        verify(context).excludeOtherErrors();
        verify(context, never()).success();
        assertThat(errors.getValue().stream().map(FormMessage::getMessage).toList(), contains(TurnstileFormAction.FAILED_MESSAGE));
        assertThat(formData.containsKey(TurnstileFormAction.RESPONSE_FIELD), equalTo(false));
    }
}
