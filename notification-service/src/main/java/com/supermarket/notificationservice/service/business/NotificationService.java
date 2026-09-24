package com.supermarket.notificationservice.service.business;

import com.supermarket.notificationservice.dto.notification.NotificationResponse;
import com.supermarket.notificationservice.model.notification.NotificationType;
import com.supermarket.notificationservice.model.notification.ReferenceType;
import com.supermarket.notificationservice.model.notification.Recipient;

import java.util.List;

public interface NotificationService {
    void createNotification(
            Recipient recipient, NotificationType type, String message, String data,
            ReferenceType referenceType, Long referenceId);

    void createNotificationForUsers(
            List<Recipient> recipients, NotificationType type, String message, String data,
            ReferenceType referenceType, Long referenceId);

    NotificationResponse markAsRead(Long notificationId);

    List<NotificationResponse> getUnreadNotifications();

    List<NotificationResponse> getAllNotifications();

    void deleteNotification(Long notificationId);

    long countUnread();

    int markAllAsRead();
}