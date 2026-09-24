package com.supermarket.notificationservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "auth-service", contextId = "userClient", path = "/internal/users")
public interface UserClient {

    @GetMapping("/by-role")
    List<UserSummary> getByRoles(@RequestParam("roles") List<String> roles);
}
