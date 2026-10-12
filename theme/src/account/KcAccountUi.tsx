/**
 * This file has been claimed for ownership from @keycloakify/keycloak-account-ui version 260700.0.3.
 * To relinquish ownership and restore this file to its original content, run the following command:
 *
 * $ npx keycloakify own --path "account/KcAccountUi.tsx" --revert
 */

import "@patternfly/patternfly/patternfly-addons.css";
import "@patternfly/react-core/dist/styles/base.css";
import "./main.css";

import {useEffect, useReducer} from "react";
import {startColorSchemeManagement} from "./colorScheme";
import {KeycloakProvider} from "../shared/keycloak-ui-shared";
import {environment} from "./environment";
import {i18n} from "./i18n/i18n";
import {Root} from "./root/Root";
import {SessionExpirationWarningOverlay} from "../shared/SessionExpirationWarningOverlay";

const prI18nInitialized = i18n.init();
startColorSchemeManagement();

export default function KcAccountUi() {
    const [isI18nInitialized, setI18nInitialized] = useReducer(() => true, false);

    useEffect(() => {
        prI18nInitialized.then(() => setI18nInitialized());
    }, []);

    useEffect(() => {
        if (!isI18nInitialized) {
            return;
        }

        const setTitle = () => {
            document.title = i18n.t("accountManagementTitle", { defaultValue: "Account Management" });
        };

        setTitle();

        i18n.on("languageChanged", setTitle);

        return () => {
            i18n.off("languageChanged", setTitle);
        };
    }, [isI18nInitialized]);

    if (!isI18nInitialized) {
        return null;
    }

    return (
        <KeycloakProvider environment={environment}>
            <Root/>
            <SessionExpirationWarningOverlay warnUserSecondsBeforeAutoLogout={45}/>
        </KeycloakProvider>
    );
}
