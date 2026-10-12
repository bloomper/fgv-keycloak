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

import org.jboss.logging.Logger;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailSenderProvider;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.AbstractKeycloakTransaction;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Anders Jacobsson
 * @since 2.0
 */
public class AdminNotificationEventListenerProvider implements EventListenerProvider {

    private static final Logger log = Logger.getLogger(AdminNotificationEventListenerProvider.class);
    private static final String REGISTRATION_MESSAGE = "registrationMessage";
    private static final Pattern USER_ROLE_MAPPING_PATH = Pattern.compile("^users/([^/]+)/role-mappings(/.*)?$");

    private final KeycloakSession session;
    private final List<String> recipients;
    private final ApprovalPolicy policy;

    public AdminNotificationEventListenerProvider(final KeycloakSession session, final List<String> recipients, final ApprovalPolicy policy) {
        this.session = session;
        this.recipients = recipients;
        this.policy = policy;
    }

    @Override
    public void onEvent(final Event event) {
        if (event.getType() != EventType.REGISTER && event.getType() != EventType.VERIFY_EMAIL) {
            return;
        }

        final RealmModel realm = session.realms().getRealm(event.getRealmId());
        final UserModel user = session.users().getUserById(realm, event.getUserId());

        if (user == null) {
            return;
        }

        if (event.getType() == EventType.REGISTER) {
            markPending(realm, user);
        }

        if (isNotifiable(event, realm)) {
            final String message = takeRegistrationMessage(user);

            if (!recipients.isEmpty() && !policy.isApproved(realm, user)) {
                notifyAdministrators(realm, user, message);
            }
        }
    }

    private String takeRegistrationMessage(final UserModel user) {
        final String message = user.getFirstAttribute(REGISTRATION_MESSAGE);

        user.removeAttribute(REGISTRATION_MESSAGE);

        return message;
    }

    private boolean isNotifiable(final Event event, final RealmModel realm) {
        return event.getType() == (realm.isVerifyEmail() ? EventType.VERIFY_EMAIL : EventType.REGISTER);
    }

    private void markPending(final RealmModel realm, final UserModel user) {
        if (!policy.markPending(session, realm, user)) {
            log.warnf("Group %s does not exist, user %s is not marked as pending approval", policy.pendingGroup(), user.getId());
        }
    }

    private void notifyAdministrators(final RealmModel realm, final UserModel user, final String message) {
        final String subject = "Ny användare i Keycloak väntar på godkännande";
        final String body = """
                %s (%s) har registrerat sig%s.
                %s
                Godkänn användaren genom att tilldela roller i Keycloak:
                %s
                """.formatted(
                fullName(user),
                user.getEmail(),
                user.isEmailVerified() ? " och verifierat sin e-postadress" : "",
                messageSection(message),
                adminConsoleUrl(realm, user)
        );

        sendAfterCommit(realm, user, recipients, subject, body);
    }

    private void sendAfterCommit(final RealmModel realm, final UserModel user, final List<String> to, final String subject, final String body) {
        final Map<String, String> smtpConfig = realm.getSmtpConfig();

        session.getTransactionManager().enlistAfterCompletion(new AbstractKeycloakTransaction() {
            @Override
            protected void commitImpl() {
                final EmailSenderProvider emailSender = session.getProvider(EmailSenderProvider.class);

                to.forEach(recipient -> {
                    try {
                        emailSender.send(smtpConfig, recipient, subject, body, null);
                    } catch (final EmailException e) {
                        log.errorf(e, "Could not notify %s about user %s", recipient, user.getId());
                    }
                });
            }

            @Override
            protected void rollbackImpl() {
            }
        });
    }

    @Override
    public void onEvent(final AdminEvent event, final boolean includeRepresentation) {
        if (event.getOperationType() != OperationType.CREATE || !isRoleMapping(event.getResourceType()) || event.getResourcePath() == null) {
            return;
        }

        final Matcher matcher = USER_ROLE_MAPPING_PATH.matcher(event.getResourcePath());

        if (!matcher.matches()) {
            return;
        }

        final RealmModel realm = session.realms().getRealm(event.getRealmId());
        final UserModel user = realm == null ? null : session.users().getUserById(realm, matcher.group(1));

        if (user != null && policy.isApproved(realm, user) && policy.clearPending(session, realm, user)) {
            notifyApproved(realm, user);
        }
    }

    private boolean isRoleMapping(final ResourceType resourceType) {
        return resourceType == ResourceType.REALM_ROLE_MAPPING || resourceType == ResourceType.CLIENT_ROLE_MAPPING;
    }

    private void notifyApproved(final RealmModel realm, final UserModel user) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            log.warnf("User %s has no email address, could not notify about approval", user.getId());
            return;
        }

        final boolean swedish = !Locale.ENGLISH.getLanguage().equals(session.getContext().resolveLocale(user).getLanguage());
        final String realmName = realm.getDisplayName() == null || realm.getDisplayName().isBlank() ? realm.getName() : realm.getDisplayName();
        final String subject = (swedish ? "Ditt konto hos %s är godkänt" : "Your %s account has been approved").formatted(realmName);
        final String body = swedish
                ? """
                Hej!

                Ditt konto har godkänts och du kan nu logga in.
                """
                : """
                Hi!

                Your account has been approved and you can now log in.
                """;

        sendAfterCommit(realm, user, List.of(user.getEmail()), subject, body);
    }

    @Override
    public void close() {
    }

    private String messageSection(final String message) {
        return message == null || message.isBlank() ? "" : "\nMeddelande från användaren:\n%s\n".formatted(message.strip());
    }

    private String fullName(final UserModel user) {
        final String fullName = "%s %s".formatted(
                user.getFirstName() == null ? "" : user.getFirstName(),
                user.getLastName() == null ? "" : user.getLastName()
        ).trim();

        return fullName.isEmpty() ? user.getUsername() : fullName;
    }

    private String adminConsoleUrl(final RealmModel realm, final UserModel user) {
        final String baseUri = session.getContext().getUri().getBaseUri().toString().replaceAll("/+$", "");

        return "%s/admin/master/console/#/%s/users/%s/settings".formatted(baseUri, realm.getName(), user.getId());
    }
}
