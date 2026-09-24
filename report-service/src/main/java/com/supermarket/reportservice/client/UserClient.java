package com.supermarket.reportservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;

@FeignClient(name = "auth-service", contextId = "userClient", path = "/internal/users")
public interface UserClient {

    @GetMapping
    List<UserSummary> getByIds(@RequestParam("ids") Collection<Long> ids);
}
