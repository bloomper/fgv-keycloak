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

import org.keycloak.Config;
import org.keycloak.models.ClientModel;
import org.keycloak.models.Constants;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;

import java.util.Set;

/**
 * @author Anders Jacobsson
 * @since 2.0
 */
public record ApprovalPolicy(String pendingGroup) {

    private static final String DEFAULT_PENDING_GROUP = "pending-approval";
    private static final Set<String> EXEMPT_CLIENT_IDS = Set.of(
            Constants.ACCOUNT_MANAGEMENT_CLIENT_ID,
            Constants.ACCOUNT_CONSOLE_CLIENT_ID
    );

    public static ApprovalPolicy from(final Config.Scope scope) {
        return new ApprovalPolicy(scope.get("pending-group", DEFAULT_PENDING_GROUP));
    }

    public boolean appliesTo(final ClientModel client) {
        return client == null || !EXEMPT_CLIENT_IDS.contains(client.getClientId());
    }

    public boolean isApproved(final RealmModel realm, final UserModel user) {
        return user.getRoleMappingsStream().anyMatch(role -> !role.equals(realm.getDefaultRole()));
    }

    public boolean markPending(final KeycloakSession session, final RealmModel realm, final UserModel user) {
        final GroupModel group = pendingGroup(session, realm);

        if (group == null) {
            return false;
        }

        user.joinGroup(group);

        return true;
    }

    public boolean clearPending(final KeycloakSession session, final RealmModel realm, final UserModel user) {
        final GroupModel group = pendingGroup(session, realm);

        if (group == null || !user.isMemberOf(group)) {
            return false;
        }

        user.leaveGroup(group);

        return true;
    }

    private GroupModel pendingGroup(final KeycloakSession session, final RealmModel realm) {
        return KeycloakModelUtils.findGroupByPath(session, realm, "/" + pendingGroup);
    }
}
