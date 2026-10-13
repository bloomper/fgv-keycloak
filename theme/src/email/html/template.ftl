<#macro emailLayout>
<html lang="${locale.language}" dir="${(ltr)?then('ltr','rtl')}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="color-scheme" content="light">
</head>
<body style="margin:0;padding:0;background-color:#f4f4f5;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f4f4f5;">
    <tr>
        <td align="center" style="padding:32px 16px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:560px;">
                <tr>
                    <td style="padding:0 8px 16px 8px;font-family:Georgia,'Times New Roman',serif;font-size:22px;line-height:28px;color:#18181b;">
                        F&ouml;reningen Gamla V&auml;star
                    </td>
                </tr>
                <tr>
                    <td style="padding:28px 32px;background-color:#ffffff;border:1px solid #e4e4e7;border-radius:12px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:15px;line-height:24px;color:#27272a;">
                        <#nested>
                    </td>
                </tr>
                <tr>
                    <td style="padding:16px 8px 0 8px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:12px;line-height:18px;color:#71717a;">
                        ${realmName!"FGV"}
                    </td>
                </tr>
            </table>
        </td>
    </tr>
</table>
</body>
</html>
</#macro>
