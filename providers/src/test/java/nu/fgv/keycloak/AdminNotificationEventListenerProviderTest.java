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
import org.keycloak.email.EmailSenderProvider;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakTransaction;
import org.keycloak.models.KeycloakTransactionManager;
import org.keycloak.models.KeycloakUriInfo;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Anders Jacobsson
 * @since 2.0
 */
class AdminNotificationEventListenerProviderTest {

    private static final String REALM_ID = "realm-id";
    private static final String USER_ID = "user-id";
    private static final String PENDING_GROUP = "pending-approval";
    private static final String ADMIN = "admin@example.org";
    private static final Map<String, String> SMTP_CONFIG = Map.of("host", "localhost");

    private final KeycloakSession session = mock(KeycloakSession.class);
    private final KeycloakContext context = mock(KeycloakContext.class);
    private final KeycloakTransactionManager transactionManager = mock(KeycloakTransactionManager.class);
    private final EmailSenderProvider emailSender = mock(EmailSenderProvider.class);
    private final RealmModel realm = mock(RealmModel.class);
    private final UserModel user = mock(UserModel.class);
    private final RoleModel defaultRole = mock(RoleModel.class);
    private final GroupModel group = mock(GroupModel.class);
    private final AdminNotificationEventListenerProvider provider =
            new AdminNotificationEventListenerProvider(session, List.of(ADMIN), new ApprovalPolicy(PENDING_GROUP));

    @BeforeEach
    void setUp() {
        final RealmProvider realms = mock(RealmProvider.class);
        final UserProvider users = mock(UserProvider.class);
        final KeycloakUriInfo uriInfo = mock(KeycloakUriInfo.class);

        when(session.realms()).thenReturn(realms);
        when(session.users()).thenReturn(users);
        when(session.getContext()).thenReturn(context);
        when(session.getTransactionManager()).thenReturn(transactionManager);
        when(session.getProvider(EmailSenderProvider.class)).thenReturn(emailSender);
        when(realms.getRealm(REALM_ID)).thenReturn(realm);
        when(users.getUserById(realm, USER_ID)).thenReturn(user);
        when(context.getUri()).thenReturn(uriInfo);
        when(context.resolveLocale(user)).thenReturn(Locale.forLanguageTag("sv"));
        when(uriInfo.getBaseUri()).thenReturn(URI.create("https://auth.example.org/auth/"));
        when(realm.getName()).thenReturn("fgv");
        when(realm.getDisplayName()).thenReturn("FGV");
        when(realm.getDefaultRole()).thenReturn(defaultRole);
        when(realm.getSmtpConfig()).thenReturn(SMTP_CONFIG);
        when(realm.isVerifyEmail()).thenReturn(true);
        when(user.getId()).thenReturn(USER_ID);
        when(user.getEmail()).thenReturn("anna@example.org");
        when(user.getFirstName()).thenReturn("Anna");
        when(user.getLastName()).thenReturn("Andersson");
        when(user.isEmailVerified()).thenReturn(true);
        when(user.getRoleMappingsStream()).thenAnswer(_ -> Stream.of(defaultRole));
    }

    @Test
    void should_mark_registered_user_as_pending_without_notifying_before_email_is_verified() {
        try (MockedStatic<KeycloakModelUtils> utils = pendingGroupExists()) {
            provider.onEvent(event(EventType.REGISTER));

            verify(user).joinGroup(group);
            verify(transactionManager, never()).enlistAfterCompletion(any());
        }
    }

    @Test
    void should_notify_administrators_on_registration_when_email_is_not_verified_by_realm() throws Exception {
        when(realm.isVerifyEmail()).thenReturn(false);
        when(user.isEmailVerified()).thenReturn(false);

        try (MockedStatic<KeycloakModelUtils> utils = pendingGroupExists()) {
            provider.onEvent(event(EventType.REGISTER));
        }

        assertThat(sentBody(ADMIN, "Ny användare i Keycloak väntar på godkännande"), not(containsString("verifierat")));
    }

    @Test
    void should_notify_administrators_when_email_is_verified() throws Exception {
        when(user.getFirstAttribute("registrationMessage")).thenReturn("  Jag var med 1998  ");

        provider.onEvent(event(EventType.VERIFY_EMAIL));

        final String body = sentBody(ADMIN, "Ny användare i Keycloak väntar på godkännande");

        assertThat(body, containsString("Anna Andersson (anna@example.org) har registrerat sig och verifierat sin e-postadress."));
        assertThat(body, containsString("Meddelande från användaren:\nJag var med 1998\n"));
        assertThat(body, containsString("https://auth.example.org/auth/admin/master/console/#/fgv/users/user-id/settings"));
        verify(user).removeAttribute("registrationMessage");
        verify(user, never()).joinGroup(any());
    }

    @Test
    void should_not_notify_administrators_about_approved_user() {
        approve();

        provider.onEvent(event(EventType.VERIFY_EMAIL));

        verify(transactionManager, never()).enlistAfterCompletion(any());
    }

    @Test
    void should_not_notify_when_no_recipients_are_configured() {
        new AdminNotificationEventListenerProvider(session, List.of(), new ApprovalPolicy(PENDING_GROUP))
                .onEvent(event(EventType.VERIFY_EMAIL));

        verify(transactionManager, never()).enlistAfterCompletion(any());
    }

    @Test
    void should_ignore_other_user_events() {
        provider.onEvent(event(EventType.LOGIN));

        verifyNoInteractions(user);
        verify(transactionManager, never()).enlistAfterCompletion(any());
    }

    @Test
    void should_notify_user_in_swedish_when_approved() throws Exception {
        approve();
        when(user.isMemberOf(group)).thenReturn(true);

        try (MockedStatic<KeycloakModelUtils> utils = pendingGroupExists()) {
            provider.onEvent(roleMappingEvent(ResourceType.CLIENT_ROLE_MAPPING, "users/user-id/role-mappings/clients/client-id"), false);
        }

        verify(user).leaveGroup(group);
        assertThat(sentBody("anna@example.org", "Ditt konto hos FGV är godkänt"), containsString("Ditt konto har godkänts"));
    }

    @Test
    void should_notify_user_in_english_when_approved() throws Exception {
        approve();
        when(user.isMemberOf(group)).thenReturn(true);
        when(context.resolveLocale(user)).thenReturn(Locale.ENGLISH);

        try (MockedStatic<KeycloakModelUtils> utils = pendingGroupExists()) {
            provider.onEvent(roleMappingEvent(ResourceType.REALM_ROLE_MAPPING, "users/user-id/role-mappings/realm"), false);
        }

        assertThat(sentBody("anna@example.org", "Your FGV account has been approved"), containsString("Your account has been approved"));
    }

    @Test
    void should_not_notify_user_that_was_never_pending() {
        approve();
        when(user.isMemberOf(group)).thenReturn(false);

        try (MockedStatic<KeycloakModelUtils> utils = pendingGroupExists()) {
            provider.onEvent(roleMappingEvent(ResourceType.CLIENT_ROLE_MAPPING, "users/user-id/role-mappings/clients/client-id"), false);
        }

        verify(user, never()).leaveGroup(any());
        verify(transactionManager, never()).enlistAfterCompletion(any());
    }

    @Test
    void should_keep_user_pending_when_only_default_role_is_assigned() {
        when(user.isMemberOf(group)).thenReturn(true);

        try (MockedStatic<KeycloakModelUtils> utils = pendingGroupExists()) {
            provider.onEvent(roleMappingEvent(ResourceType.REALM_ROLE_MAPPING, "users/user-id/role-mappings/realm"), false);
        }

        verify(user, never()).leaveGroup(any());
        verify(transactionManager, never()).enlistAfterCompletion(any());
    }

    @Test
    void should_ignore_other_admin_events() {
        final AdminEvent removed = roleMappingEvent(ResourceType.CLIENT_ROLE_MAPPING, "users/user-id/role-mappings/clients/client-id");
        removed.setOperationType(OperationType.DELETE);

        provider.onEvent(removed, false);
        provider.onEvent(roleMappingEvent(ResourceType.USER, "users/user-id"), false);
        provider.onEvent(roleMappingEvent(ResourceType.REALM_ROLE_MAPPING, "groups/group-id/role-mappings/realm"), false);

        verifyNoInteractions(user);
    }

    private MockedStatic<KeycloakModelUtils> pendingGroupExists() {
        final MockedStatic<KeycloakModelUtils> utils = mockStatic(KeycloakModelUtils.class);

        utils.when(() -> KeycloakModelUtils.findGroupByPath(session, realm, "/" + PENDING_GROUP)).thenReturn(group);

        return utils;
    }

    private void approve() {
        when(user.getRoleMappingsStream()).thenAnswer(_ -> Stream.of(defaultRole, mock(RoleModel.class)));
    }

    private Event event(final EventType type) {
        final Event event = new Event();

        event.setType(type);
        event.setRealmId(REALM_ID);
        event.setUserId(USER_ID);

        return event;
    }

    private AdminEvent roleMappingEvent(final ResourceType resourceType, final String resourcePath) {
        final AdminEvent event = new AdminEvent();

        event.setOperationType(OperationType.CREATE);
        event.setResourceType(resourceType);
        event.setResourcePath(resourcePath);
        event.setRealmId(REALM_ID);

        return event;
    }

    private String sentBody(final String recipient, final String subject) throws Exception {
        final ArgumentCaptor<KeycloakTransaction> transaction = ArgumentCaptor.forClass(KeycloakTransaction.class);
        final ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);

        verify(transactionManager).enlistAfterCompletion(transaction.capture());
        verify(emailSender, never()).send(any(), any(String.class), any(), any(), any());

        transaction.getValue().begin();
        transaction.getValue().commit();

        verify(emailSender).send(eq(SMTP_CONFIG), eq(recipient), eq(subject), body.capture(), isNull());

        return body.getValue();
    }
}
