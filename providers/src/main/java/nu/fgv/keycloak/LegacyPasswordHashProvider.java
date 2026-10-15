/*
 * Copyright 2024 the original author or authors.
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

import org.keycloak.credential.hash.PasswordHashProvider;
import org.keycloak.models.PasswordPolicy;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.models.credential.dto.PasswordCredentialData;
import org.keycloak.models.credential.dto.PasswordSecretData;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * @author Anders Jacobsson
 * @since 2.0
 */
public class LegacyPasswordHashProvider implements PasswordHashProvider {

    private final String providerId;

    public LegacyPasswordHashProvider(final String providerId) {
        this.providerId = providerId;
    }

    private static String bytesToHex(final byte[] hashBytes) {
        final StringBuilder sb = new StringBuilder();

        for (final byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }

    @Override
    public boolean policyCheck(final PasswordPolicy passwordPolicy, final PasswordCredentialModel passwordCredentialModel) {
        return true;
    }

    @Override
    public PasswordCredentialModel encodedCredential(final String plainPassword, final int iterations) {
        final String encodedPassword = hashPassword(plainPassword, new byte[0], iterations);

        return PasswordCredentialModel.createFromValues(providerId, new byte[0], iterations, encodedPassword);
    }

    @Override
    public boolean verify(final String plainPassword, final PasswordCredentialModel passwordCredentialModel) {
        final PasswordCredentialData passwordCredentialData = passwordCredentialModel.getPasswordCredentialData();
        final PasswordSecretData secretData = passwordCredentialModel.getPasswordSecretData();

        if (passwordCredentialData == null || secretData == null || secretData.getValue() == null) {
            return false;
        }

        final String hashedPassword = hashPassword(plainPassword, secretData.getSalt(), passwordCredentialData.getHashIterations());

        return hashedPassword != null
                && MessageDigest.isEqual(hashedPassword.getBytes(StandardCharsets.US_ASCII), secretData.getValue().getBytes(StandardCharsets.US_ASCII));
    }

    private String hashPassword(final String plainPassword, final byte[] salt, final int iterations) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-512");
            String hashedPassword = "%s%s".formatted(plainPassword, new String(Base64.getDecoder().decode(salt), StandardCharsets.US_ASCII));

            for (int i = 0; i < iterations; i++) {
                hashedPassword = bytesToHex(digest.digest(hashedPassword.getBytes(StandardCharsets.US_ASCII)));
            }

            return hashedPassword;
        } catch (final NoSuchAlgorithmException e) {
            // Will not happen
        }

        return null;
    }

    @Override
    public void close() {
    }
}
