package com.supermarket.commons.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(RequestInterceptor.class)
public class CommonsFeignAutoConfiguration {

    @Bean
    public RequestInterceptor userHeadersRequestInterceptor() {
        return new UserHeadersRequestInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(ErrorDecoder.class)
    public ErrorDecoder remoteErrorDecoder(ObjectMapper objectMapper) {
        return new RemoteErrorDecoder(objectMapper);
    }
}
