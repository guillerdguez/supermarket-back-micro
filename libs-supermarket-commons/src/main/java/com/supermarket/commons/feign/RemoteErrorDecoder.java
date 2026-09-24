package com.supermarket.commons.feign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supermarket.commons.exception.DuplicateResourceException;
import com.supermarket.commons.exception.InsufficientPermissionsException;
import com.supermarket.commons.exception.InsufficientStockException;
import com.supermarket.commons.exception.InvalidOperationException;
import com.supermarket.commons.exception.RemoteServiceException;
import com.supermarket.commons.exception.ResourceNotFoundException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.InputStream;

@RequiredArgsConstructor
public class RemoteErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper;

    @Override
    public Exception decode(String methodKey, Response response) {
        String service = resolveServiceName(response);
        JsonNode body = readBody(response);
        String message = extractMessage(body, service, response.status());
        String error = body != null && body.hasNonNull("error") ? body.get("error").asText() : "";
        return switch (response.status()) {
            case 400 -> "Inventory Conflict".equals(error)
                    ? new InsufficientStockException(message)
                    : new InvalidOperationException(message);
            case 403 -> new InsufficientPermissionsException(message);
            case 404 -> new ResourceNotFoundException(message);
            case 409 -> new DuplicateResourceException(message);
            default -> new RemoteServiceException(service,
                    service + " responded with status " + response.status() + ": " + message);
        };
    }

    private String resolveServiceName(Response response) {
        if (response.request() != null && response.request().requestTemplate() != null
                && response.request().requestTemplate().feignTarget() != null) {
            return response.request().requestTemplate().feignTarget().name();
        }
        return "remote-service";
    }

    private JsonNode readBody(Response response) {
        if (response.body() == null) {
            return null;
        }
        try (InputStream stream = response.body().asInputStream()) {
            return objectMapper.readTree(stream);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private String extractMessage(JsonNode body, String service, int status) {
        if (body == null || !body.hasNonNull("message")) {
            return service + " returned status " + status;
        }
        JsonNode message = body.get("message");
        return message.isTextual() ? message.asText() : message.toString();
    }
}
