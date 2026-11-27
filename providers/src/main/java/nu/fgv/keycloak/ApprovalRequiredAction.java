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

import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.ClientModel;
import org.keycloak.models.Constants;

import java.util.Set;

/**
 * Stops a user from logging in until approved: assigned any role beyond the realm's default role. The roles are assigned
 * in Medlemskap, which also keeps the group {@code pending-approval}; this only enforces the result at login. The
 * account console is exempt.
 *
 * @author Anders Jacobsson
 * @since 2.0
 */
public class ApprovalRequiredAction implements RequiredActionProvider {

    public static final String ID = "fgv-approval";
    private static final String HEADER_KEY = "fgvApprovalPendingHeader";
    private static final String MESSAGE_KEY = "fgvApprovalPending";

    private static final Set<String> EXEMPT_CLIENT_IDS = Set.of(
            Constants.ACCOUNT_MANAGEMENT_CLIENT_ID,
            Constants.ACCOUNT_CONSOLE_CLIENT_ID
    );

    @Override
    public void evaluateTriggers(final RequiredActionContext context) {
        if (appliesTo(context.getAuthenticationSession().getClient()) && !isApproved(context)) {
            context.getAuthenticationSession().addRequiredAction(ID);
        }
    }

    @Override
    public void requiredActionChallenge(final RequiredActionContext context) {
        if (isApproved(context)) {
            context.success();
        } else {
            context.challenge(context.form()
                    .setAttribute("messageHeader", HEADER_KEY)
                    .setInfo(MESSAGE_KEY)
                    .createInfoPage());
        }
    }

    @Override
    public void processAction(final RequiredActionContext context) {
        requiredActionChallenge(context);
    }

    @Override
    public void close() {
    }

    private boolean appliesTo(final ClientModel client) {
        return client == null || !EXEMPT_CLIENT_IDS.contains(client.getClientId());
    }

    private boolean isApproved(final RequiredActionContext context) {
        final var defaultRole = context.getRealm().getDefaultRole();

        return context.getUser().getRoleMappingsStream().anyMatch(role -> !role.equals(defaultRole));
    }
}
