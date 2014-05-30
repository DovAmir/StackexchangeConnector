package com.streamonce.connectors.stackexchange;

import com.streamonce.sdk.v1.connector.authentication.OAuthProvider;
import com.streamonce.sdk.v1.framework.Framework;
import com.streamonce.sdk.v1.framework.FrameworkFactory;
import com.streamonce.sdk.v1.framework.Logger;
import com.streamonce.sdk.v1.framework.http.Http;
import com.streamonce.sdk.v1.framework.http.HttpRequest;
import com.streamonce.sdk.v1.framework.http.HttpResponse;
import com.streamonce.sdk.v1.model.Status;
import com.streamonce.sdk.v1.model.UserAccount;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: dov.amir
 * Date: 06/04/2014
 * Time: 16:48
 * To change this template use File | Settings | File Templates.
 */
public class StackexchangeOAuth extends OAuthProvider {

    //https://api.stackexchange.com/docs/authentication

    public final static String CLIENT_ID = "2982";
    public final static String CLIENT_SECRET = ")lMdSqfQbCEyCjoPk8k0FQ((";
    public final static String CLIENT_KEY = "kl)aRzNT6iS2uP*lLTZG4Q((";

    /*
        client_id
        scope (details)
        redirect_uri - must be under an apps registered domain
        state - optional*/
    public static final String URL_AUTHORIZATION = "https://stackexchange.com/oauth/?client_id={0}&redirect_uri={1}&scope={2}";
    /*
        client_id
        client_secret
        code - from the previous step
        redirect_uri - must be the same as the provided in the first step
     */
    public static final String URL_ACCESS_TOKEN = "https://stackexchange.com/oauth/access_token";
    public final static String SCOPE = "no_expiry,write_access";
    public final static String REDIRECT_URI =
            "http://127.0.0.1:8000/external/callback/" + StackexchangeConnector.TYPE;

    public OAuthEndpoint getOauthEndpoint() {
        String url = MessageFormat.format(URL_AUTHORIZATION, CLIENT_ID, REDIRECT_URI, SCOPE);
        return new OAuthEndpoint(url, 700, 700);
    }

    public OAuthResult handleCallback(Map<String, String> requestParameters) {
        String err = requestParameters.get("error");
        String errReason = requestParameters.get("error_reason");
        String errDesc = requestParameters.get("error_description");

        if (StringUtils.isNotEmpty(err) || StringUtils.isNotEmpty(errReason) || StringUtils.isNotEmpty(errDesc)) {
            String message = "Error: " + err + ". Reason: " + errReason + ". Description: " + errDesc;
            return new OAuthResult(null, new Status(false, message));
        }

        String code = requestParameters.get("code");

        Framework framework = FrameworkFactory.createFramework(StackexchangeConnector.TYPE);
        Http http = framework.getHttp();
        Map<String, String> params = new HashMap<>();
        params.put("client_id", CLIENT_ID);
        params.put("client_secret", CLIENT_SECRET);
        params.put("redirect_uri", REDIRECT_URI);
        params.put("code", code);
        HttpRequest request = http.post(URL_ACCESS_TOKEN, params).withFormParamsContentType();
        HttpResponse response = request.execute();
        String body = response.getResponseBody();
        int statusCode = response.getStatusCode();
        Logger logger = framework.getLogger();
        logger.info("Stackexchange access token response [" + statusCode + "]: " + body);
        if (statusCode == 200) {
            try {

                String accessToken = parseAccessTokenResponse(body);
                UserAccount userAccount = getUserAccount(framework, accessToken);

                return new OAuthResult(userAccount, Status.OK);

            } catch (IOException e) {
                String msg = e.getMessage();
                logger.error("Failed parsing access token response: " + body, e);
                return new OAuthResult(null,
                        new Status(false, "Failed [" + msg + "] parsing access token response: " + body));
            }
        }

        String msg = "Received invalid response from access token exchange [" + statusCode + "]. Message: " + body;
        return new OAuthResult(null, new Status(false, msg));
    }

    private UserAccount getUserAccount(Framework framework, String accessToken)
            throws IOException {
        String url = MessageFormat.format(StackexchangeSettingsValidator.SELF_URL, "", accessToken);
        HttpResponse responseUser = framework.getHttp().get(url).execute();
        String userbody = responseUser.getResponseBody();

        int userstatusCode = responseUser.getStatusCode();
        if (userstatusCode == 200) {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode node = objectMapper.readValue(userbody, JsonNode.class);
            JsonNode user = node.path("items").get(0);
            String userId = "";
            String username = "";
            String userDisplayName = "";
            if (user != null) {
                userId = user.path("user_id").getValueAsText();   //should be account_id ?
                username = user.path("display_name").getValueAsText();
                userDisplayName = user.path("display_name").getValueAsText();
            }
            UserAccount userAccount = new UserAccount(userId, username, accessToken);
            userAccount.setDisplayName(userDisplayName);
            return userAccount;
        }
        return null;

    }

    public AuthenticationType getType() {
        return AuthenticationType.OAUTH;
    }

    public String parseAccessTokenResponse(String query) {
        Map<String, String> query_pairs = new LinkedHashMap<String, String>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            try {
                query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
                        URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
        return query_pairs.get("access_token");
    }
}
