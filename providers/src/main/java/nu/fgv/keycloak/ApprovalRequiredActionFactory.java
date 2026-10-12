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
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

/**
 * @author Anders Jacobsson
 * @since 2.0
 */
public class ApprovalRequiredActionFactory implements RequiredActionFactory {

    private ApprovalPolicy policy;

    @Override
    public RequiredActionProvider create(final KeycloakSession keycloakSession) {
        return new ApprovalRequiredAction(policy);
    }

    @Override
    public void init(final Config.Scope scope) {
        policy = ApprovalPolicy.from(scope);
    }

    @Override
    public void postInit(final KeycloakSessionFactory keycloakSessionFactory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return ApprovalRequiredAction.ID;
    }

    @Override
    public String getDisplayText() {
        return "FGV approval";
    }
}
