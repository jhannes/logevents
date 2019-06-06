package org.logevents.util.openid;

import org.logevents.config.Configuration;
import org.logevents.util.JsonParser;
import org.logevents.util.NetUtils;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Supports any standards compliant OpenID Connect provider as identity provider for
 * {@link org.logevents.extend.servlets.LogEventsServlet}.
 *
 * <h2>To set up authorization with Azure Active Directory</h2>
 *
 * This description assumes that you are member of an organization that uses Active Directory,
 * but that you only want some users to have access to the LogEventsServlet.
 *
 * <ol>
 *     <li>Go to <a href="https://portal.azure.com">the Azure portal</a> and log in with your organization account</li>
 *     <li>Click "Create a resource" and enter "active directory" to create a new directory. You can name the directory what you want.</li>
 *     <li>You have to wait a little while for the directory to be created, then click the funnel + book icon to switch to your new directory</li>
 *     <li>Select "Azure Active Directory" from the menu in your new directory to go to directory management</li>
 *     <li>Select "App Registration (preview)" and "New registration" to create your application</li>
 *     <li>In the application configuration, you need to find the Application (client) ID, generate a new client secret (under Certificates and secrets) and setup your redirect URI (under Authentication) and use this to configure {@link org.logevents.observers.WebLogEventObserver}</li>
 *     <li>In the Azure Active Directory menu, you can select "Users" and add a guest user from your organization to add to your limited Active Directory (this feature of Active Directory is known as B2B)</li>
 *     <li>In the Azure Active Directory menu, you can select "Enterprise Applications" to limited the users that can access the logging application</li>
 * </ol>
 */
public class OpenIdConfiguration {
    private final String clientId;
    private final String clientSecret;
    private final String openIdIssuer;
    private Optional<String> redirectUri = Optional.empty();
    private Optional<String> scopes = Optional.empty();

    public OpenIdConfiguration(Configuration configuration) {
        this(configuration.getString("openIdIssuer"), configuration.getString("clientId"), configuration.getString("clientSecret"));
        this.redirectUri = configuration.optionalString("redirectUri");
        this.scopes = configuration.optionalString("scopes");
    }

    public OpenIdConfiguration(String openIdIssuer, String clientId, String clientSecret) {
        this.openIdIssuer = openIdIssuer;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    private static final Random random = new Random();

    private static final String CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    public static String randomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private String getRedirectUri(String defaultValue) {
        return redirectUri.orElse(defaultValue);
    }

    public String getScopes() {
        return scopes.orElse("openid+email+profile");
    }

    /**
     * Generate a URL to start the login flow with OpenID Connect. Redirect the web
     * browser to this URL to start the login process.
     */
    public String getAuthorizationUrl(String state, String fallbackRedirectUri) throws IOException {
        return getAuthorizationEndpoint() + "?" +
                "response_type=code" +
                "&client_id=" + clientId +
                "&redirect_uri=" + getRedirectUri(fallbackRedirectUri) +
                "&scope=" + getScopes() +
                "&state=" + state;
    }

    private String getAuthorizationEndpoint() throws IOException {
        return (String) loadOpenIdConfiguration().get("authorization_endpoint");
    }

    /**
     * Complete the login process by fetching ID token from the Identity provider.
     * Call this when the web browser returns to the <code>redirect_uri</code> in
     * your app with the code query parameter.
     */
    public Map<String, Object> fetchIdToken(String code, String fallbackRedirectUri) throws IOException {
        Map<String, String> formPayload = createTokenRequestPayload(code, fallbackRedirectUri);
        Map<String, Object> response = NetUtils.postFormForJson(getTokenUri(), formPayload);
        return getIdToken(response);
    }

    public Map<String, String> createTokenRequestPayload(String code, String defaultRedirectUri) {
        Map<String, String> formPayload = new HashMap<>();
        formPayload.put("client_id", clientId);
        formPayload.put("client_secret", clientSecret);
        formPayload.put("redirect_uri", getRedirectUri(defaultRedirectUri));
        formPayload.put("grant_type", "authorization_code");
        formPayload.put("code", code);
        return formPayload;
    }

    private Map<String, Object> getIdToken(Map<String, Object> response) throws IOException {
        String idToken = response.get("id_token").toString();
        return (Map<String, Object>) JsonParser.parseFromBase64encodedString(idToken.split("\\.")[1]);
    }

    private URL getTokenUri() throws IOException {
        return new URL((String) loadOpenIdConfiguration().get("token_endpoint"));
    }

    private Map<String, Object> loadOpenIdConfiguration() throws IOException {
        return (Map<String, Object>)
                JsonParser.parse(new URL(this.openIdIssuer + "/.well-known/openid-configuration"));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "openIdIssuer='" + openIdIssuer + '\'' +
                '}';
    }
}
