package com.supermarket.commons;

import com.supermarket.commons.web.CommonExceptionHandler;
import com.supermarket.commons.web.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class CommonExceptionHandlerTest {

    private final CommonExceptionHandler handler = new CommonExceptionHandler();

    @Test
    @DisplayName("an unknown path answers 404 instead of a generic 500")
    void noResource_ShouldReturnNotFound() {
        ResponseEntity<ErrorResponse> response = handler.handleNoResource(
                new NoResourceFoundException(HttpMethod.GET, "inventory/branches/1/products/2/stock"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("an unsupported method answers 405 instead of a generic 500")
    void methodNotSupported_ShouldReturnMethodNotAllowed() {
        ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("DELETE"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }
}
