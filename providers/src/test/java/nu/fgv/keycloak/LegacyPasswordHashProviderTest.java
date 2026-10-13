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

import org.junit.jupiter.api.Test;
import org.keycloak.models.credential.PasswordCredentialModel;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * @author Anders Jacobsson
 * @since 2.0
 */
class LegacyPasswordHashProviderTest {

    private static final String PROVIDER_ID = "legacy-fgv";
    private static final byte[] SALT = "c2FsdA==".getBytes(StandardCharsets.US_ASCII);
    private static final int ITERATIONS = 20;
    private static final String HASH = "a9d71801ec4d882ee50d34871f7c17075c13d78c51215616e7ac1601790fac840b82f8ed6f1251f2f757f682a5aacc944a59d584c3e85965c1167217d7a14178";

    private final LegacyPasswordHashProvider provider = new LegacyPasswordHashProvider(PROVIDER_ID);

    @Test
    void should_verify_migrated_password() {
        final PasswordCredentialModel credential = PasswordCredentialModel.createFromValues(PROVIDER_ID, SALT, ITERATIONS, HASH);

        assertThat(provider.verify("secret", credential), is(true));
    }

    @Test
    void should_reject_wrong_password() {
        final PasswordCredentialModel credential = PasswordCredentialModel.createFromValues(PROVIDER_ID, SALT, ITERATIONS, HASH);

        assertThat(provider.verify("Secret", credential), is(false));
    }

    @Test
    void should_reject_password_hashed_with_other_iterations() {
        final PasswordCredentialModel credential = PasswordCredentialModel.createFromValues(PROVIDER_ID, SALT, ITERATIONS + 1, HASH);

        assertThat(provider.verify("secret", credential), is(false));
    }

    @Test
    void should_verify_password_it_encoded() {
        final PasswordCredentialModel credential = provider.encodedCredential("secret", ITERATIONS);

        assertThat(credential.getPasswordCredentialData().getAlgorithm(), is(PROVIDER_ID));
        assertThat(provider.verify("secret", credential), is(true));
        assertThat(provider.verify("other", credential), is(false));
    }
}
