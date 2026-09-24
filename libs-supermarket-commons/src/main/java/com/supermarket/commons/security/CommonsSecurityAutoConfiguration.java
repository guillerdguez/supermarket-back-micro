package com.supermarket.commons.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.ArrayList;
import java.util.List;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(CommonsSecurityProperties.class)
public class CommonsSecurityAutoConfiguration {

    private static final List<String> ALWAYS_PUBLIC = List.of(
            "/internal/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/**",
            "/error"
    );

    @Bean
    @ConditionalOnMissingBean
    public CurrentUserProvider currentUserProvider() {
        return new CurrentUserProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public JsonSecurityErrorWriter jsonSecurityErrorWriter(ObjectMapper objectMapper) {
        return new JsonSecurityErrorWriter(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain headerSecurityFilterChain(
            HttpSecurity http,
            CommonsSecurityProperties properties,
            JsonSecurityErrorWriter errorWriter) throws Exception {
        List<String> publicPaths = new ArrayList<>(ALWAYS_PUBLIC);
        publicPaths.addAll(properties.getPublicPaths());
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicPaths.toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, ex) -> errorWriter.write(
                                request, response, 401, "Unauthorized", "Invalid or expired token"))
                        .accessDeniedHandler((request, response, ex) -> errorWriter.write(
                                request, response, 403, "Forbidden",
                                "You don't have permission to access this resource")))
                .addFilterBefore(new HeaderAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
