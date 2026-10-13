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

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.ClientModel;
import org.keycloak.models.Constants;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.util.stream.Stream;

import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Anders Jacobsson
 * @since 2.0
 */
class ApprovalRequiredActionTest {

    private final ApprovalRequiredAction action = new ApprovalRequiredAction(new ApprovalPolicy("pending-approval"));
    private final RequiredActionContext context = mock(RequiredActionContext.class);
    private final AuthenticationSessionModel authenticationSession = mock(AuthenticationSessionModel.class);
    private final ClientModel client = mock(ClientModel.class);
    private final RealmModel realm = mock(RealmModel.class);
    private final UserModel user = mock(UserModel.class);
    private final RoleModel defaultRole = mock(RoleModel.class);

    @BeforeEach
    void setUp() {
        when(context.getAuthenticationSession()).thenReturn(authenticationSession);
        when(context.getRealm()).thenReturn(realm);
        when(context.getUser()).thenReturn(user);
        when(authenticationSession.getClient()).thenReturn(client);
        when(client.getClientId()).thenReturn("spexregister");
        when(realm.getDefaultRole()).thenReturn(defaultRole);
        when(user.getRoleMappingsStream()).thenAnswer(_ -> Stream.of(defaultRole));
    }

    @Test
    void should_require_action_for_unapproved_user() {
        action.evaluateTriggers(context);

        verify(authenticationSession).addRequiredAction(ApprovalRequiredAction.ID);
    }

    @Test
    void should_not_require_action_for_approved_user() {
        approve();

        action.evaluateTriggers(context);

        verify(authenticationSession, never()).addRequiredAction(ApprovalRequiredAction.ID);
    }

    @Test
    void should_not_require_action_in_account_console() {
        when(client.getClientId()).thenReturn(Constants.ACCOUNT_CONSOLE_CLIENT_ID);

        action.evaluateTriggers(context);

        verify(authenticationSession, never()).addRequiredAction(ApprovalRequiredAction.ID);
    }

    @Test
    void should_show_info_page_to_unapproved_user() {
        final LoginFormsProvider form = mock(LoginFormsProvider.class, RETURNS_SELF);
        final Response page = mock(Response.class);

        when(context.form()).thenReturn(form);
        when(form.createInfoPage()).thenReturn(page);

        action.requiredActionChallenge(context);

        verify(form).setInfo("fgvApprovalPending");
        verify(context).challenge(page);
        verify(context, never()).success();
    }

    @Test
    void should_let_approved_user_through() {
        approve();

        action.requiredActionChallenge(context);

        verify(context).success();
        verify(context, never()).challenge(any());
    }

    @Test
    void should_challenge_again_when_unapproved_user_submits() {
        final LoginFormsProvider form = mock(LoginFormsProvider.class, RETURNS_SELF);
        final Response page = mock(Response.class);

        when(context.form()).thenReturn(form);
        when(form.createInfoPage()).thenReturn(page);

        action.processAction(context);

        verify(context).challenge(page);
    }

    private void approve() {
        when(user.getRoleMappingsStream()).thenAnswer(_ -> Stream.of(defaultRole, mock(RoleModel.class)));
    }
}
