package com.boycottpro.causes;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.boycottpro.causes.GetCauseDetailsHandler;
import com.boycottpro.models.ResponseMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GetCauseDetailsHandlerTest {

    @Mock
    private DynamoDbClient dynamoDb;

    @Mock
    private Context context;

    @InjectMocks
    private GetCauseDetailsHandler handler;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testValidCauseIdReturnsCause() throws Exception {
        String causeId = "c1";
        Map<String, String> pathParams = Map.of("cause_id", causeId); // note: still using "user_id" key as in handler
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withPathParameters(pathParams);
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);

        Map<String, AttributeValue> item = Map.ofEntries(
                Map.entry("cause_id", AttributeValue.fromS("c1")),
                Map.entry("cause_desc", AttributeValue.fromS("Apple")),
                Map.entry("category", AttributeValue.fromS("computer cause")),
                Map.entry("follower_count", AttributeValue.fromN("10"))
        );

        when(dynamoDb.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().item(item).build());

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("Apple"));
        assertTrue(response.getBody().contains("c1"));
    }

    @Test
    public void testMissingCauseIdReturns400() throws JsonProcessingException {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withPathParameters(Map.of("cause_id", ""));
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

        assertEquals(400, response.getStatusCode());
        ResponseMessage message = objectMapper.readValue(response.getBody(), ResponseMessage.class);
        assertTrue(message.getDevMsg().contains("cause_id not present"));
    }

    @Test
    public void testCauseNotFoundReturnsError() throws JsonProcessingException {
        String causeId = "unknown";
        Map<String, String> pathParams = Map.of("cause_id", causeId);
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withPathParameters(pathParams);
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);
        when(dynamoDb.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().build());

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

        assertEquals(500, response.getStatusCode());
        ResponseMessage message = objectMapper.readValue(response.getBody(), ResponseMessage.class);
        assertTrue(message.getDevMsg().contains("causes record could not be found"));
    }

    @Test
    public void testUnexpectedExceptionReturns500() throws JsonProcessingException {
        String causeId = "crash";
        Map<String, String> pathParams = Map.of("cause_id", causeId);
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withPathParameters(pathParams);
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);
        when(dynamoDb.getItem(any(GetItemRequest.class)))
                .thenThrow(new RuntimeException("Boom"));

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("Unexpected server error"));
    }
}
