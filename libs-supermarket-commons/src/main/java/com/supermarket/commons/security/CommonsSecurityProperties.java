package com.supermarket.commons.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "supermarket.security")
public class CommonsSecurityProperties {

    private List<String> publicPaths = new ArrayList<>();
}
