package com.supermarket.commons.feign;

import com.supermarket.commons.security.UserHeaders;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class UserHeadersRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        for (String header : UserHeaders.ALL) {
            String value = request.getHeader(header);
            if (StringUtils.hasText(value) && !template.headers().containsKey(header)) {
                template.header(header, value);
            }
        }
    }
}
