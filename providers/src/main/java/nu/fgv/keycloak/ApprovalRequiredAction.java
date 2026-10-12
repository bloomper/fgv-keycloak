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

/**
 * @author Anders Jacobsson
 * @since 2.0
 */
public class ApprovalRequiredAction implements RequiredActionProvider {

    public static final String ID = "fgv-approval";
    private static final String HEADER_KEY = "fgvApprovalPendingHeader";
    private static final String MESSAGE_KEY = "fgvApprovalPending";

    private final ApprovalPolicy policy;

    public ApprovalRequiredAction(final ApprovalPolicy policy) {
        this.policy = policy;
    }

    @Override
    public void evaluateTriggers(final RequiredActionContext context) {
        if (policy.appliesTo(context.getAuthenticationSession().getClient()) && !isApproved(context)) {
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

    private boolean isApproved(final RequiredActionContext context) {
        return policy.isApproved(context.getRealm(), context.getUser());
    }
}
