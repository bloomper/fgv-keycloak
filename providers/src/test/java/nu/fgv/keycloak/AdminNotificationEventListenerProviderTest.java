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
import org.keycloak.models.ClientModel;
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
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.util.JsonSerialization;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
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
    private final ClientModel revival = application("revival-id", "fgv-revival", "Nyuppsättning", "https://revival.example.org/");
    private final ClientModel spexregister = application("spexregister-id", "spexregister", "Spexregister", "https://register.example.org/");
    private final ClientModel revivalMcp = application("revival-mcp-id", "fgv-revival-mcp", "Nyuppsättning MCP", "");
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
        List.of(revival, spexregister, revivalMcp).forEach(client -> when(realm.getClientById(client.getId())).thenReturn(client));
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
    void should_list_fgv_revival_when_pending_user_is_approved_for_it() throws Exception {
        final RoleModel revivalUser = clientRole("revival-user", revival);
        holds(revivalUser);
        when(user.isMemberOf(group)).thenReturn(true);

        try (MockedStatic<KeycloakModelUtils> utils = pendingGroupExists()) {
            provider.onEvent(clientRoleMappingEvent(revival, revivalUser), false);
        }

        final String body = sentBody("anna@example.org", "Ditt konto hos FGV är godkänt");

        assertThat(body, containsString("Ditt konto har godkänts och du kan nu logga in i:"));
        assertThat(body, containsString("- Nyuppsättning: https://revival.example.org/"));
        assertThat(body, not(containsString("Spexregister")));
    }

    @Test
    void should_list_every_application_in_swedish() throws Exception {
        final RoleModel revivalUser = clientRole("revival-user", revival);
        holds(clientRole("spexregister-user", spexregister), revivalUser);
        when(user.isMemberOf(group)).thenReturn(true);

        try (MockedStatic<KeycloakModelUtils> utils = pendingGroupExists()) {
            provider.onEvent(clientRoleMappingEvent(revival, revivalUser), false);
        }

        final String body = sentBody("anna@example.org", "Ditt konto hos FGV är godkänt");

        assertThat(body, containsString("- Spexregister: https://register.example.org/"));
        assertThat(body, containsString("- Nyuppsättning: https://revival.example.org/"));
    }

    @Test
    void should_list_every_application_in_english() throws Exception {
        final RoleModel revivalUser = clientRole("revival-user", revival);
        holds(clientRole("spexregister-user", spexregister), revivalUser);
        when(user.isMemberOf(group)).thenReturn(true);
        when(context.resolveLocale(user)).thenReturn(Locale.ENGLISH);

        try (MockedStatic<KeycloakModelUtils> utils = pendingGroupExists()) {
            provider.onEvent(clientRoleMappingEvent(revival, revivalUser), false);
        }

        final String body = sentBody("anna@example.org", "Your FGV account has been approved");

        assertThat(body, containsString("Your account has been approved and you can now log in to:"));
        assertThat(body, containsString("- Spexregister: https://register.example.org/"));
        assertThat(body, containsString("- Nyuppsättning: https://revival.example.org/"));
    }

    @Test
    void should_keep_todays_text_when_pending_user_is_approved_without_application() throws Exception {
        approve();
        when(user.isMemberOf(group)).thenReturn(true);

        try (MockedStatic<KeycloakModelUtils> utils = pendingGroupExists()) {
            provider.onEvent(roleMappingEvent(ResourceType.REALM_ROLE_MAPPING, "users/user-id/role-mappings/realm"), false);
        }

        final String body = sentBody("anna@example.org", "Ditt konto hos FGV är godkänt");

        assertThat(body, containsString("Ditt konto har godkänts och du kan nu logga in."));
        assertThat(body, not(containsString("logga in i:")));
    }

    @Test
    void should_tell_approved_user_about_first_role_on_another_application() throws Exception {
        final RoleModel revivalUser = clientRole("revival-user", revival);
        holds(clientRole("spexregister-user", spexregister), revivalUser);

        try (MockedStatic<KeycloakModelUtils> utils = pendingGroupExists()) {
            provider.onEvent(clientRoleMappingEvent(revival, revivalUser), false);
        }

        verify(user, never()).leaveGroup(any());
        assertThat(sentBody("anna@example.org", "Du har nu tillgång till Nyuppsättning"),
                containsString("Du har nu tillgång till Nyuppsättning:\nhttps://revival.example.org/"));
    }

    @Test
    void should_tell_approved_user_about_new_application_in_english() throws Exception {
        final RoleModel revivalUser = clientRole("revival-user", revival);
        holds(clientRole("spexregister-user", spexregister), revivalUser);
        when(context.resolveLocale(user)).thenReturn(Locale.ENGLISH);

        try (MockedStatic<KeycloakModelUtils> utils = pendingGroupExists()) {
            provider.onEvent(clientRoleMappingEvent(revival, revivalUser), false);
        }

        assertThat(sentBody("anna@example.org", "You now have access to Nyuppsättning"),
                containsString("You now have access to Nyuppsättning:\nhttps://revival.example.org/"));
    }

    @Test
    void should_send_one_email_when_two_roles_on_a_new_application_are_added_at_once() throws Exception {
        final RoleModel revivalUser = clientRole("revival-user", revival);
        final RoleModel revivalAdmin = clientRole("revival-admin", revival);
        holds(clientRole("spexregister-user", spexregister), revivalUser, revivalAdmin);

        try (MockedStatic<KeycloakModelUtils> utils = pendingGroupExists()) {
            provider.onEvent(clientRoleMappingEvent(revival, revivalUser, revivalAdmin), false);
        }

        assertThat(sentBody("anna@example.org", "Du har nu tillgång till Nyuppsättning"), containsString("https://revival.example.org/"));
    }

    @Test
    void should_not_email_when_another_role_is_added_on_an_application_in_use() {
        final RoleModel revivalAdmin = clientRole("revival-admin", revival);
        holds(clientRole("revival-user", revival), revivalAdmin);

        try (MockedStatic<KeycloakModelUtils> utils = pendingGroupExists()) {
            provider.onEvent(clientRoleMappingEvent(revival, revivalAdmin), false);
        }

        verify(transactionManager, never()).enlistAfterCompletion(any());
    }

    @Test
    void should_not_email_existing_spexregister_user_given_another_spexregister_role() {
        final RoleModel editor = clientRole("spexregister-editor", spexregister);
        holds(clientRole("spexregister-user", spexregister), editor);

        try (MockedStatic<KeycloakModelUtils> utils = pendingGroupExists()) {
            provider.onEvent(clientRoleMappingEvent(spexregister, editor), false);
        }

        verify(transactionManager, never()).enlistAfterCompletion(any());
    }

    @Test
    void should_not_email_about_client_without_base_url() {
        final RoleModel mcpRole = clientRole("revival-mcp-role", revivalMcp);
        holds(clientRole("spexregister-user", spexregister), mcpRole);

        try (MockedStatic<KeycloakModelUtils> utils = pendingGroupExists()) {
            provider.onEvent(clientRoleMappingEvent(revivalMcp, mcpRole), false);
        }

        verify(transactionManager, never()).enlistAfterCompletion(any());
    }

    @Test
    void should_never_list_client_without_base_url() throws Exception {
        final RoleModel revivalUser = clientRole("revival-user", revival);
        holds(clientRole("revival-mcp-role", revivalMcp), revivalUser);
        when(user.isMemberOf(group)).thenReturn(true);

        try (MockedStatic<KeycloakModelUtils> utils = pendingGroupExists()) {
            provider.onEvent(clientRoleMappingEvent(revival, revivalUser), false);
        }

        assertThat(sentBody("anna@example.org", "Ditt konto hos FGV är godkänt"), not(containsString("MCP")));
    }

    @Test
    void should_not_email_when_representation_is_missing_or_unreadable() {
        final RoleModel revivalUser = clientRole("revival-user", revival);
        holds(clientRole("spexregister-user", spexregister), revivalUser);
        final AdminEvent missing = roleMappingEvent(ResourceType.CLIENT_ROLE_MAPPING, "users/user-id/role-mappings/clients/revival-id");
        final AdminEvent unreadable = roleMappingEvent(ResourceType.CLIENT_ROLE_MAPPING, "users/user-id/role-mappings/clients/revival-id");
        unreadable.setRepresentation("not json");

        try (MockedStatic<KeycloakModelUtils> utils = pendingGroupExists()) {
            provider.onEvent(missing, false);
            provider.onEvent(unreadable, true);
        }

        verify(transactionManager, never()).enlistAfterCompletion(any());
    }

    @Test
    void should_resolve_placeholders_in_application_name_and_url() throws Exception {
        final ClientModel placeholders = application("placeholder-id", "fgv-revival",
                "${FGV_TEST_UNSET_NAME:Nyuppsättning}", "${FGV_TEST_UNSET_URL:https://revival.example.org}/");
        when(realm.getClientById("placeholder-id")).thenReturn(placeholders);
        final RoleModel revivalUser = clientRole("revival-user", placeholders);
        holds(clientRole("spexregister-user", spexregister), revivalUser);

        try (MockedStatic<KeycloakModelUtils> utils = pendingGroupExists()) {
            provider.onEvent(clientRoleMappingEvent(placeholders, revivalUser), false);
        }

        assertThat(sentBody("anna@example.org", "Du har nu tillgång till Nyuppsättning"),
                containsString("Nyuppsättning:\nhttps://revival.example.org/"));
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

    private ClientModel application(final String id, final String clientId, final String name, final String baseUrl) {
        final ClientModel client = mock(ClientModel.class);

        when(client.getId()).thenReturn(id);
        when(client.getClientId()).thenReturn(clientId);
        when(client.getName()).thenReturn(name);
        when(client.getBaseUrl()).thenReturn(baseUrl);

        return client;
    }

    private RoleModel clientRole(final String id, final ClientModel client) {
        final RoleModel role = mock(RoleModel.class);
        final String clientId = client.getId();

        when(role.getId()).thenReturn(id);
        when(role.getName()).thenReturn(id);
        when(role.isClientRole()).thenReturn(true);
        when(role.getContainer()).thenReturn(client);
        when(role.getContainerId()).thenReturn(clientId);

        return role;
    }

    private void holds(final RoleModel... roles) {
        when(user.getRoleMappingsStream()).thenAnswer(_ -> Stream.concat(Stream.of(defaultRole), Arrays.stream(roles)));
        List.of(revival, spexregister, revivalMcp).forEach(client -> when(user.getClientRoleMappingsStream(client))
                .thenAnswer(_ -> Arrays.stream(roles).filter(role -> role.getContainer() == client)));
        Arrays.stream(roles)
                .map(role -> (ClientModel) role.getContainer())
                .distinct()
                .forEach(client -> when(user.getClientRoleMappingsStream(client))
                        .thenAnswer(_ -> Arrays.stream(roles).filter(role -> role.getContainer() == client)));
    }

    private AdminEvent clientRoleMappingEvent(final ClientModel client, final RoleModel... added) {
        final String clientId = client.getId();
        final AdminEvent event = roleMappingEvent(ResourceType.CLIENT_ROLE_MAPPING,
                "users/user-id/role-mappings/clients/" + clientId);
        final List<RoleRepresentation> representation = Arrays.stream(added).map(role -> {
            final RoleRepresentation rep = new RoleRepresentation();

            rep.setId(role.getId());
            rep.setName(role.getName());
            rep.setClientRole(true);
            rep.setContainerId(clientId);

            return rep;
        }).toList();

        try {
            event.setRepresentation(JsonSerialization.writeValueAsString(representation));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }

        return event;
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
