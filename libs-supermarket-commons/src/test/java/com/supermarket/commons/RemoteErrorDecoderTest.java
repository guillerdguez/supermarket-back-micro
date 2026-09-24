package com.supermarket.commons;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supermarket.commons.exception.DuplicateResourceException;
import com.supermarket.commons.exception.InsufficientStockException;
import com.supermarket.commons.exception.InvalidOperationException;
import com.supermarket.commons.exception.RemoteFailures;
import com.supermarket.commons.exception.RemoteServiceException;
import com.supermarket.commons.exception.ResourceNotFoundException;
import com.supermarket.commons.feign.RemoteErrorDecoder;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteErrorDecoderTest {

    private final RemoteErrorDecoder decoder = new RemoteErrorDecoder(new ObjectMapper());

    private Response response(int status, String body) {
        Request request = Request.create(Request.HttpMethod.GET, "http://catalog-service/internal/products/1",
                Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());
        return Response.builder().status(status).request(request).headers(Map.of())
                .body(body, StandardCharsets.UTF_8).build();
    }

    @Test
    @DisplayName("a remote 404 keeps its message and becomes a local not-found error")
    void notFound_ShouldMapToResourceNotFound() {
        Exception exception = decoder.decode("key", response(404,
                "{\"status\":404,\"error\":\"Not Found\",\"message\":\"Product not found with ID: 1\"}"));

        assertThat(exception).isInstanceOf(ResourceNotFoundException.class).hasMessage("Product not found with ID: 1");
    }

    @Test
    @DisplayName("a remote inventory conflict becomes an insufficient stock error")
    void inventoryConflict_ShouldMapToInsufficientStock() {
        Exception exception = decoder.decode("key", response(400,
                "{\"status\":400,\"error\":\"Inventory Conflict\",\"message\":\"Insufficient stock\"}"));

        assertThat(exception).isInstanceOf(InsufficientStockException.class);
    }

    @Test
    @DisplayName("other 400 and 409 responses map to invalid operation and duplicate errors")
    void badRequestAndConflict_ShouldMap() {
        assertThat(decoder.decode("key", response(400, "{\"error\":\"Invalid Operation\",\"message\":\"nope\"}")))
                .isInstanceOf(InvalidOperationException.class);
        assertThat(decoder.decode("key", response(409, "{\"error\":\"Conflict\",\"message\":\"dup\"}")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("a 5xx becomes a remote service failure")
    void serverError_ShouldMapToRemoteServiceException() {
        assertThat(decoder.decode("key", response(503, "")))
                .isInstanceOf(RemoteServiceException.class);
    }

    @Test
    @DisplayName("propagate keeps business errors and turns technical failures into a friendly 503 message")
    void propagate_ShouldNormalizeFailures() {
        ResourceNotFoundException business = new ResourceNotFoundException("Branch not found");

        assertThat(RemoteFailures.propagate("branch-service", business)).isSameAs(business);
        assertThat(RemoteFailures.propagate("branch-service", new RuntimeException("Read timed out")))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessage("branch-service is temporarily unavailable. Please try again later.");
    }
}
