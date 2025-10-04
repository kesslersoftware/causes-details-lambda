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
import java.lang.reflect.Field;

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

    @Test
    public void testDefaultConstructor() {
        // Test the default constructor coverage
        // Note: This may fail in environments without AWS credentials/region configured
        try {
            GetCauseDetailsHandler handler = new GetCauseDetailsHandler();
            assertNotNull(handler);

            // Verify DynamoDbClient was created (using reflection to access private field)
            try {
                Field dynamoDbField = GetCauseDetailsHandler.class.getDeclaredField("dynamoDb");
                dynamoDbField.setAccessible(true);
                DynamoDbClient dynamoDb = (DynamoDbClient) dynamoDbField.get(handler);
                assertNotNull(dynamoDb);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                fail("Failed to access DynamoDbClient field: " + e.getMessage());
            }
        } catch (software.amazon.awssdk.core.exception.SdkClientException e) {
            // AWS SDK can't initialize due to missing region configuration
            // This is expected in Jenkins without AWS credentials - test passes
            System.out.println("Skipping DynamoDbClient verification due to AWS SDK configuration: " + e.getMessage());
        }
    }

    @Test
    public void testUnauthorizedUser() {
        // Test the unauthorized block coverage
        handler = new GetCauseDetailsHandler(dynamoDb);

        // Create event without JWT token (or invalid token that returns null sub)
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        // No authorizer context, so JwtUtility.getSubFromRestEvent will return null

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, null);

        assertEquals(401, response.getStatusCode());
        assertTrue(response.getBody().contains("Unauthorized"));
    }

    @Test
    public void testJsonProcessingExceptionInResponse() throws Exception {
        // Test JsonProcessingException coverage in response method by using reflection
        handler = new GetCauseDetailsHandler(dynamoDb);

        // Use reflection to access the private response method
        java.lang.reflect.Method responseMethod = GetCauseDetailsHandler.class.getDeclaredMethod("response", int.class, Object.class);
        responseMethod.setAccessible(true);

        // Create an object that will cause JsonProcessingException
        Object problematicObject = new Object() {
            public Object writeReplace() throws java.io.ObjectStreamException {
                throw new java.io.NotSerializableException("Not serializable");
            }
        };

        // Create a circular reference object that will cause JsonProcessingException
        Map<String, Object> circularMap = new HashMap<>();
        circularMap.put("self", circularMap);

        // This should trigger the JsonProcessingException -> RuntimeException path
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            try {
                responseMethod.invoke(handler, 500, circularMap);
            } catch (java.lang.reflect.InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) e.getCause();
                }
                throw new RuntimeException(e.getCause());
            }
        });

        // Verify it's ultimately caused by JsonProcessingException
        Throwable cause = exception.getCause();
        assertTrue(cause instanceof JsonProcessingException,
                "Expected JsonProcessingException, got: " + cause.getClass().getSimpleName());
    }

    @Test
    public void testNullPathParametersReturns400() throws JsonProcessingException {
        // Test when pathParameters is null (line 48 branch: pathParams == null)
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);
        event.setPathParameters(null);

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

        assertEquals(400, response.getStatusCode());
        ResponseMessage message = objectMapper.readValue(response.getBody(), ResponseMessage.class);
        assertTrue(message.getDevMsg().contains("cause_id not present"));
    }

    @Test
    public void testPathParamsWithoutCauseIdKeyReturns400() throws JsonProcessingException {
        // Test when pathParams exists but doesn't contain "cause_id" key (line 48: pathParams.get("cause_id") returns null)
        Map<String, String> pathParams = Map.of("other_param", "value");
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);
        event.setPathParameters(pathParams);

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

        assertEquals(400, response.getStatusCode());
        ResponseMessage message = objectMapper.readValue(response.getBody(), ResponseMessage.class);
        assertTrue(message.getDevMsg().contains("cause_id not present"));
    }

}
