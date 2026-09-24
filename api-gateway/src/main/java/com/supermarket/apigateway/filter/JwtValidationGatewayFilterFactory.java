package com.supermarket.apigateway.filter;

import com.supermarket.apigateway.security.JsonErrorWriter;
import com.supermarket.apigateway.security.UserHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

@Component
@Slf4j
public class JwtValidationGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

    private final WebClient webClient;
    private final Duration timeout;

    public JwtValidationGatewayFilterFactory(
            WebClient.Builder webClientBuilder,
            @Value("${auth.service.url:http://auth-service}") String authServiceUrl,
            @Value("${auth.service.timeout:3s}") Duration timeout) {
        this.webClient = webClientBuilder.baseUrl(authServiceUrl).build();
        this.timeout = timeout;
    }

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> {
            String token = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (token == null || !token.startsWith("Bearer ")) {
                return JsonErrorWriter.write(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized",
                        "Invalid or expired token");
            }
            return validate(token)
                    .map(Optional::of)
                    .defaultIfEmpty(Optional.empty())
                    .flatMap(outcome -> {
                        if (outcome.isEmpty()) {
                            return authUnavailable(exchange);
                        }
                        ResponseEntity<Void> validation = outcome.get();
                        if (validation.getStatusCode().is2xxSuccessful()) {
                            return chain.filter(withIdentity(exchange, validation.getHeaders()));
                        }
                        if (validation.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                            String reason = validation.getHeaders().getFirst(UserHeaders.AUTH_ERROR);
                            return JsonErrorWriter.write(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized",
                                    reason != null ? reason : "Invalid or expired token");
                        }
                        return authUnavailable(exchange);
                    });
        };
    }

    private Mono<ResponseEntity<Void>> validate(String token) {
        return webClient.get()
                .uri("/auth/validate")
                .header(HttpHeaders.AUTHORIZATION, token)
                .exchangeToMono(response -> response.toBodilessEntity())
                .timeout(timeout)
                .onErrorResume(error -> {
                    log.warn("Token validation against auth-service failed: {}", error.getMessage());
                    return Mono.empty();
                });
    }

    private ServerWebExchange withIdentity(ServerWebExchange exchange, HttpHeaders identity) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> UserHeaders.IDENTITY.forEach(name -> {
                    headers.remove(name);
                    String value = identity.getFirst(name);
                    if (value != null) {
                        headers.set(name, value);
                    }
                }))
                .build();
        return exchange.mutate().request(request).build();
    }

    private Mono<Void> authUnavailable(ServerWebExchange exchange) {
        return JsonErrorWriter.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                "Authentication service is temporarily unavailable. Please try again later.");
    }
}
