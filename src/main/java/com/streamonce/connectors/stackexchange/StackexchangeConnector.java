package com.streamonce.connectors.stackexchange;

//import com.streamonce.dummy.framework.JacksonJson;

import com.streamonce.sdk.v1.connector.*;
import com.streamonce.sdk.v1.connector.config.ConfigInputType;
import com.streamonce.sdk.v1.connector.config.ConnectorConfig;
import com.streamonce.sdk.v1.framework.Framework;
import com.streamonce.sdk.v1.framework.FrameworkFactory;
import com.streamonce.sdk.v1.framework.Json;
import com.streamonce.sdk.v1.framework.Logger;
import com.streamonce.sdk.v1.framework.http.Http;
import com.streamonce.sdk.v1.framework.http.HttpResponse;
import com.streamonce.sdk.v1.model.*;
import com.streamonce.sdk.v1.model.impl.AuthorImpl;
import com.streamonce.sdk.v1.model.impl.ContentContainerImpl;
import com.streamonce.sdk.v1.model.impl.ContentImpl;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.JsonNode;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * User: dov.amir
 * Date: 4/6/14
 * Time: 16:40
 */
@SuppressWarnings("UnusedDeclaration")
@Connector(type = StackexchangeConnector.TYPE,
        metadata = @Metadata(
                author = "StreamOnce",
                name = "StackExchange",
                description = "Enrich your Jive group with posts from StackExchange"
        ),
        authentication = StackexchangeOAuth.class,
        validator = StackexchangeSettingsValidator.class,
        comments = StackexchangeConnector.class,
        configuration = @ConnectorConfig(          //TODO
                type = ConfigInputType.text,
                name = "Tag",
                // inputPrefix = "",
                placeholder = "Stackoverflow tag",
                description = "Tag to follow"
        )
)
public class StackexchangeConnector implements ContentWriter, ScheduledReader {

    //https://api.stackexchange.com/docs/questions#order=desc&sort=activity&filter=default&site=stackoverflow&run=true


    public static final String TYPE = "stackexchange";
    public static final int MAX_ITEMS_FOR_INITIAL_FETCH = 5;
    public final static String ENDPOINT_BASE = "https://api.stackexchange.com/2.2/";
    public final static String SITE = "stackoverflow";
    public final static String ENDPOINT_ID_PARAMS =
            "site=" + SITE + "&access_token={1}&key=" + StackexchangeOAuth.CLIENT_KEY;

    //read
    //https://api.stackexchange.com/docs/questions
    private final static String ENDPOINT_GET_RECENT_QUESTIONS =
            ENDPOINT_BASE + "questions?pagesize=5&order=desc&sort=activity&tagged={0}&filter=!)rbHx(OEPCs7_*eUjylT&" +
                    ENDPOINT_ID_PARAMS;
    //https://api.stackexchange.com/docs/answers-on-questions#page=1&pagesize=10&order=desc&sort=activity&ids=7398462&filter=!9WA((ItYa&site=stackoverflow&run=true
    private final static String ENDPOINT_GET_RECENT_ANSWERS =
            ENDPOINT_BASE + "questions/{0}/answers?pagesize=5&order=desc&sort=activity&" + ENDPOINT_ID_PARAMS;


    //write
    //https://api.stackexchange.com/docs/comments-on-questions
    //private final static String ENDPOINT_GET_MEDIA_COMMENTS = ENDPOINT_BASE+ "questions/{0}/comments?pagesize=5&order=desc&sort=creation&" + ENDPOINT_ID_PARAMS;
    //https://api.stackexchange.com/docs/create-comment#id=123&body=123123&key=dsad&preview=true&filter=default&site=stackoverflow&run=true
    //private final static String ENDPOINT_POST_COMMENT = ENDPOINT_BASE+ "posts/{0}/comments/add?" + ENDPOINT_ID_PARAMS;
    //https://api.stackexchange.com/docs/create-answer#id=23&body=322&preview=false&filter=default&site=stackoverflow&run=true
    private final static String ENDPOINT_POST_ANSWER =
            ENDPOINT_BASE + "questions/{0}/answers/add?" + ENDPOINT_ID_PARAMS;

    private final static String PARAM_MIN_ID = "&page=";

    @Override
    public long getSchedulingPeriod() {
        return TimeUnit.MINUTES.toSeconds(1);
    }

    @Override
    public ContentContainer read(Account account) throws ConnectorException {
        List<Content> contents = new ArrayList<>();
        Map<Mapping, String> states = new HashMap<>();
        String token = account.getPassword();

        Framework framework = FrameworkFactory.createFramework(TYPE);
        Http http = framework.getHttp();
        Json json = framework.getJson();
        Logger logger = framework.getLogger();
        List<Mapping> mappings = framework.getAccounts().getMappings(account);
        Map<String, List<Mapping>> ongoingMappings = new HashMap<>();
        for (Mapping mapping : mappings) {
            String mappingName = mapping.getMappingName();
            try {
                int limit = MAX_ITEMS_FOR_INITIAL_FETCH;
                String url = MessageFormat.format(ENDPOINT_GET_RECENT_QUESTIONS, mappingName, token);
                String lastState = mapping.getLastState();
//               if (StringUtils.isNotEmpty(lastState)) {    //TODO
//                    url += PARAM_MIN_ID + lastState;
//                    limit = Integer.MAX_VALUE;
//                }

                HttpResponse response = http.get(url).execute();
                String body = response.getResponseBody();
                if (response.getStatusCode() != 200) {
                    throw new ConnectorException(
                            "Failed fetching recent media for: " + mappingName + ". Error: " + body);
                }

                JsonNode node = json.fromString(body, JsonNode.class);


                String newState = node.path("has_more").getTextValue();
                if (StringUtils.isEmpty(newState)) {
                    newState = lastState;
                }
                states.put(mapping, newState);      //TODO

                JsonNode array = node.path("items");
                int contentCounter = 0;
                for (JsonNode dataNode : array) {
                    String id = dataNode.path("question_id").getTextValue();
                    String title = dataNode.path("title").getTextValue();
                    String postBody = createBody(dataNode);
                    Date date = getCreationDate(dataNode);
                    Author author = getAuthor(dataNode.path("owner"));
                    Content content = new ContentImpl(id, title, postBody, date, author, true);

                    content.setContentUrl(dataNode.path("link").getTextValue());

                    contents.add(content);
                    mapContentMapping(id, mapping, ongoingMappings);

                    JsonNode comments = dataNode.path("answers");
                    int commentCounter = 0;
                    if (comments != null) {
                        for (JsonNode commentNode : comments) {
                            String commentId = commentNode.path("answer_id").getTextValue();
                            String text = commentNode.path("body").getTextValue();
                            Author commentAuthor = getAuthor(commentNode.path("owner"));
                            Date commentDate = getCreationDate(commentNode);
                            String commenttitle = text;
                            if (commenttitle.length() > 40) {
                                commenttitle = commenttitle.substring(0, 39);
                            }
                            Content comment = new ContentImpl(commentId, commenttitle, text, commentDate, commentAuthor,
                                    true);
                            comment.setContentUrl(commentNode.path("link").getTextValue());
                            comment.setParentId(id);

                            contents.add(comment);
                            mapContentMapping(commentId, mapping, ongoingMappings);

                            if (++commentCounter >= limit) {
                                break;
                            }
                        }
                    }

                    if (++contentCounter >= limit) {
                        break;
                    }
                }
            } catch (IOException e) {
                logger.error("Failed fetching media for: " + mappingName);
            }
        }

        for (Content content : contents) {
            content.setMappings(ongoingMappings.get(content.getContentId()));
        }
        return new ContentContainerImpl(contents, states);
    }

    public void mapContentMapping(String contentId, Mapping mapping, Map<String, List<Mapping>> ongoingMappings) {
        List<Mapping> list = ongoingMappings.get(contentId);
        if (list == null) {
            list = new ArrayList<>();
            ongoingMappings.put(contentId, list);
        }
        list.add(mapping);
    }

    public Date getCreationDate(JsonNode dataNode) {
        Long creationDate = dataNode.path("creation_date").getLongValue();
        return new Date(1000 * creationDate);
    }

    public Author getAuthor(JsonNode authorNode) {
        String authorName = authorNode.path("display_name").getTextValue();
        return new AuthorImpl(authorName, "");
    }

    public String createBody(JsonNode node) {
        StringBuilder sb = new StringBuilder();


        String body = node.path("body").getTextValue();
        sb.append(body);

        String likesStr = String.valueOf(node.path("score").getIntValue());
        if (StringUtils.isNotEmpty(likesStr)) {
            sb.append("<div style=\"padding-bottom: 1em;\"><strong>score:</strong> ")
                    .append(likesStr).append("</div>");
        }


        return sb.toString();
    }

    public String write(Content comment) {
        Framework framework = FrameworkFactory.createFramework(TYPE);
        Logger logger = framework.getLogger();
        logger.debug("Got new comment from Jive with subject " + comment.getTitle());

        Http http = framework.getHttp();
        List<Mapping> mappings = comment.getMappings();
        // TODO: We don't really loop through all... since we return a single String..... :)
        for (Mapping mapping : mappings) {
            String accessToken = mapping.getAccount().getPassword();
            Map<String, String> params = new HashMap<>();
            String mediaId = comment.getParentId();

            params.put("access_token", accessToken);
            params.put("body", comment.getBody());
            params.put("site", SITE);
            params.put("id", mediaId);
            params.put("key", StackexchangeOAuth.CLIENT_KEY);


            String url = MessageFormat.format(ENDPOINT_POST_ANSWER, mediaId);
            HttpResponse response = http.post(url, params).withFormParamsContentType().execute();
            int statusCode = response.getStatusCode();
            String body = response.getResponseBody();
            if (statusCode != 200) {
                logger.error("Failed posting comment in Instagram. Received [" + statusCode + "]: " + body);
                return null;
            }

            try {
                JsonNode node = framework.getJson().fromString(body, JsonNode.class);
//                JsonNode codeNode = node.path("meta").path("code");
//                if (codeNode.getIntValue() != 200) {
//                    logger.error("Failed posting comment in Instagram. Unexpected meta response: " + body);
//                    return null;
//                }

            } catch (IOException e) {
                logger.error("Failed parsing comment creation response", e);
                return null;
            }

            url = MessageFormat.format(ENDPOINT_GET_RECENT_ANSWERS, mediaId, accessToken);
            response = http.get(url).execute();
            statusCode = response.getStatusCode();
            body = response.getResponseBody();
            if (statusCode != 200) {
                logger.error("Failed reading new comment in Instagram. Received [" + statusCode + "]: " + body);
                return null;
            }

            try {
                JsonNode node = framework.getJson().fromString(body, JsonNode.class);


                JsonNode datas = node.path("items");
                JsonNode first = datas.path(0);
                JsonNode idNode = first.path("answer_id");
                return idNode.getTextValue();
            } catch (IOException e) {
                logger.error("Failed parsing comment creation response", e);
                return null;
            }
        }

        return null;
    }


}
