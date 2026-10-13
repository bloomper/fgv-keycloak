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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.models.ClientModel;
import org.keycloak.models.Constants;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.mockito.MockedStatic;

import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Anders Jacobsson
 * @since 2.0
 */
class ApprovalPolicyTest {

    private static final String PENDING_GROUP = "pending-approval";

    private final ApprovalPolicy policy = new ApprovalPolicy(PENDING_GROUP);
    private final KeycloakSession session = mock(KeycloakSession.class);
    private final RealmModel realm = mock(RealmModel.class);
    private final UserModel user = mock(UserModel.class);
    private final RoleModel defaultRole = mock(RoleModel.class);
    private final GroupModel group = mock(GroupModel.class);

    @BeforeEach
    void setUp() {
        when(realm.getDefaultRole()).thenReturn(defaultRole);
    }

    @Test
    void should_apply_when_client_is_unknown() {
        assertThat(policy.appliesTo(null), is(true));
    }

    @Test
    void should_apply_to_ordinary_client() {
        assertThat(policy.appliesTo(client("spexregister")), is(true));
    }

    @Test
    void should_not_apply_to_account_clients() {
        assertThat(policy.appliesTo(client(Constants.ACCOUNT_MANAGEMENT_CLIENT_ID)), is(false));
        assertThat(policy.appliesTo(client(Constants.ACCOUNT_CONSOLE_CLIENT_ID)), is(false));
    }

    @Test
    void should_not_approve_user_with_only_default_role() {
        when(user.getRoleMappingsStream()).thenReturn(Stream.of(defaultRole));

        assertThat(policy.isApproved(realm, user), is(false));
    }

    @Test
    void should_not_approve_user_without_roles() {
        when(user.getRoleMappingsStream()).thenReturn(Stream.empty());

        assertThat(policy.isApproved(realm, user), is(false));
    }

    @Test
    void should_approve_user_with_additional_role() {
        when(user.getRoleMappingsStream()).thenReturn(Stream.of(defaultRole, mock(RoleModel.class)));

        assertThat(policy.isApproved(realm, user), is(true));
    }

    @Test
    void should_mark_user_as_pending() {
        try (MockedStatic<KeycloakModelUtils> utils = mockStatic(KeycloakModelUtils.class)) {
            utils.when(() -> KeycloakModelUtils.findGroupByPath(session, realm, "/" + PENDING_GROUP)).thenReturn(group);

            assertThat(policy.markPending(session, realm, user), is(true));
            verify(user).joinGroup(group);
        }
    }

    @Test
    void should_not_mark_user_as_pending_when_group_is_missing() {
        try (MockedStatic<KeycloakModelUtils> utils = mockStatic(KeycloakModelUtils.class)) {
            utils.when(() -> KeycloakModelUtils.findGroupByPath(session, realm, "/" + PENDING_GROUP)).thenReturn(null);

            assertThat(policy.markPending(session, realm, user), is(false));
            verify(user, never()).joinGroup(group);
        }
    }

    @Test
    void should_clear_pending_user() {
        try (MockedStatic<KeycloakModelUtils> utils = mockStatic(KeycloakModelUtils.class)) {
            utils.when(() -> KeycloakModelUtils.findGroupByPath(session, realm, "/" + PENDING_GROUP)).thenReturn(group);
            when(user.isMemberOf(group)).thenReturn(true);

            assertThat(policy.clearPending(session, realm, user), is(true));
            verify(user).leaveGroup(group);
        }
    }

    @Test
    void should_not_clear_user_that_is_not_pending() {
        try (MockedStatic<KeycloakModelUtils> utils = mockStatic(KeycloakModelUtils.class)) {
            utils.when(() -> KeycloakModelUtils.findGroupByPath(session, realm, "/" + PENDING_GROUP)).thenReturn(group);
            when(user.isMemberOf(group)).thenReturn(false);

            assertThat(policy.clearPending(session, realm, user), is(false));
            verify(user, never()).leaveGroup(group);
        }
    }

    private ClientModel client(final String clientId) {
        final ClientModel client = mock(ClientModel.class);

        when(client.getClientId()).thenReturn(clientId);

        return client;
    }
}
