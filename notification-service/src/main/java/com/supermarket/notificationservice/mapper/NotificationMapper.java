package com.supermarket.notificationservice.mapper;

import com.supermarket.notificationservice.dto.notification.NotificationResponse;
import com.supermarket.notificationservice.model.notification.Notification;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotificationMapper {
    public NotificationResponse toResponse(Notification notification) {
        if (notification == null) return null;
        return NotificationResponse.builder()
                .id(notification.getId())
                .userId(notification.getUserId())
                .username(notification.getUsername())
                .type(notification.getType())
                .message(notification.getMessage())
                .data(notification.getData())
                .referenceType(notification.getReferenceType())
                .referenceId(notification.getReferenceId())
                .read(notification.getRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }

    public List<NotificationResponse> toResponseList(List<Notification> notifications) {
        if (notifications == null) return null;
        return notifications.stream()
                .map(this::toResponse)
                .toList();
    }
}