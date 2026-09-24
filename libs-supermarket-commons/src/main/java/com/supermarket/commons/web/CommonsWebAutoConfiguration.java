package com.supermarket.commons.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonsWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CommonExceptionHandler commonExceptionHandler() {
        return new CommonExceptionHandler();
    }
}
