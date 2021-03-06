package com.streamonce.connectors.stackexchange;


import com.streamonce.sdk.v1.connector.ConnectorSettingsValidator;
import com.streamonce.sdk.v1.connector.config.Configuration;
import com.streamonce.sdk.v1.framework.Framework;
import com.streamonce.sdk.v1.framework.FrameworkFactory;
import com.streamonce.sdk.v1.framework.http.HttpResponse;
import com.streamonce.sdk.v1.model.Account;
import com.streamonce.sdk.v1.model.Status;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.text.MessageFormat;

/**
 * Created with IntelliJ IDEA.
 * User: Zvoykish
 * Date: 4/6/14
 * Time: 16:42
 */
public class StackexchangeSettingsValidator implements ConnectorSettingsValidator {
    public final static String SELF_URL = StackexchangeConnector.ENDPOINT_BASE + "me?order=desc&sort=reputation&" +
            StackexchangeConnector.ENDPOINT_ID_PARAMS;
    public final static String TAG_URL =
            StackexchangeConnector.ENDPOINT_BASE + "tags/{0}/info?" + StackexchangeConnector.ENDPOINT_ID_PARAMS;
    // TODO


    @Override
    public Status validateAccount(Account account) {
        Framework framework = FrameworkFactory.createFramework(StackexchangeConnector.TYPE);
        String url = MessageFormat.format(SELF_URL, "", account.getPassword());
        HttpResponse response = framework.getHttp().get(url).execute();
        return isSuccessResponse(response, framework);
    }

    public Status validateConfiguration(Account account, Configuration configuration) {

        Framework framework = FrameworkFactory.createFramework(StackexchangeConnector.TYPE);
        String url = MessageFormat.format(TAG_URL, configuration.getValueId(), account.getPassword());
        HttpResponse response = framework.getHttp().get(url).execute();
        Status status = isSuccessResponse(response, framework);
        if (status.isOk()) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode node = objectMapper.readValue(response.getResponseBody(), JsonNode.class);
                if (node.path("items").size() > 0) {
                    return new Status(true);
                } else {
                    return new Status(false, "Tag has no content");
                }
            } catch (IOException e) {
                framework.getLogger().error("Failed parsing tag count response", e);
            }
        }
        return status;
    }

    private Status isSuccessResponse(HttpResponse response, Framework framework) {
        String body = response.getResponseBody();
        boolean basicSuccess = response.getStatusCode() == 200 && StringUtils.isNotEmpty(body);
        if (basicSuccess) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode node = objectMapper.readValue(body, JsonNode.class);
                if (node.path("items").size() > 0) {
                    return new Status(true);
                } else {
                    return new Status(false, "Stackexchange responded with bad response: " + body);
                }
            } catch (IOException e) {
                framework.getLogger().error("Failed parsing Stackexchange response", e);
            }
        }
        return new Status(false, "Unknown Stackexchange error: " + body);
    }
}
