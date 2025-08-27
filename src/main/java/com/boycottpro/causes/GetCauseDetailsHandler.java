package com.boycottpro.causes;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.boycottpro.models.Causes;
import com.boycottpro.models.ResponseMessage;
import com.boycottpro.utilities.CausesUtility;
import com.boycottpro.utilities.JwtUtility;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.util.Map;

public class GetCauseDetailsHandler implements
        RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final String TABLE = "causes";
    private final DynamoDbClient dynamoDb;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GetCauseDetailsHandler() {
        this.dynamoDb = DynamoDbClient.create();
    }

    public GetCauseDetailsHandler(DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String sub = null;
        try {
            sub = JwtUtility.getSubFromRestEvent(event);
            if (sub == null) return response(401, Map.of("message", "Unauthorized"));
            Map<String, String> pathParams = event.getPathParameters();
            String causeId = (pathParams != null) ? pathParams.get("cause_id") : null;
            if (causeId == null || causeId.isEmpty()) {
                ResponseMessage message = new ResponseMessage(400,
                        "sorry, there was an error processing your request",
                        "cause_id not present");
                return response(400,message);
            }
            Causes causes = getCausesById(causeId);
            if (causes == null) {
                ResponseMessage message = new ResponseMessage(500,
                        "sorry, there was an error processing your request",
                        "causes record could not be found");
                return response(500,message);
            }
            System.out.println("RESPONSE = " + causes);
            return response(200,causes);
        } catch (Exception e) {
            System.out.println(e.getMessage() + " for user " + sub);
            return response(500,Map.of("error", "Unexpected server error: " + e.getMessage()) );
        }
    }
    private APIGatewayProxyResponseEvent response(int status, Object body) {
        String responseBody = null;
        try {
            responseBody = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(responseBody);
    }
    private Causes getCausesById(String causesId) {
        GetItemRequest request = GetItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of("cause_id", AttributeValue.fromS(causesId)))
                .build();

        GetItemResponse response = dynamoDb.getItem(request);

        if (response.hasItem()) {
            return CausesUtility.mapToCauses(response.item());
        } else {
            return null; // or throw an exception if preferred
        }
    }
}
