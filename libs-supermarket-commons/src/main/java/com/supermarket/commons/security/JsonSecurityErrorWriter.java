package com.supermarket.commons.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RequiredArgsConstructor
public class JsonSecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletRequest request, HttpServletResponse response,
                      int status, String error, String message) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(status);
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("message", message);
        body.put("path", request.getServletPath());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
