package org.logevents.util.openid;

import org.logevents.util.Configuration;
import org.logevents.util.JsonParser;
import org.logevents.util.NetUtils;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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

    private String getRedirectUri(String defaultValue) {
        return redirectUri.orElse(defaultValue);
    }

    public String getScopes() {
        return scopes.orElse("openid+email+profile");
    }

    public String getAuthorizationUrl(String state, String baseUrl) throws IOException {
        return getAuthorizationEndpoint() + "?" +
                "response_type=code" +
                "&client_id=" + clientId +
                "&redirect_uri=" + getRedirectUri(baseUrl + "/oauth2callback") +
                "&scope=" + getScopes() +
                "&state=" + state;
    }

    private String getAuthorizationEndpoint() throws IOException {
        return (String) loadOpenIdConfiguration().get("authorization_endpoint");
    }

    public Map<String, Object> fetchIdToken(String code, String defaultRedirectUri) throws IOException {
        Map<String, String> formPayload = createTokenRequestPayload(code, defaultRedirectUri);
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
}
