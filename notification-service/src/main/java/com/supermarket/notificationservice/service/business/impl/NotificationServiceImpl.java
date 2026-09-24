package com.supermarket.notificationservice.service.business.impl;

import com.supermarket.notificationservice.dto.notification.NotificationResponse;
import com.supermarket.commons.exception.ResourceNotFoundException;
import com.supermarket.notificationservice.mapper.NotificationMapper;
import com.supermarket.notificationservice.model.notification.Notification;
import com.supermarket.notificationservice.model.notification.NotificationType;
import com.supermarket.notificationservice.model.notification.ReferenceType;
import com.supermarket.notificationservice.model.notification.Recipient;
import com.supermarket.notificationservice.repository.NotificationRepository;
import com.supermarket.commons.security.AuthenticatedUser;
import com.supermarket.commons.security.CurrentUserProvider;
import com.supermarket.notificationservice.service.business.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class NotificationServiceImpl implements NotificationService {
    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public void createNotification(
            Recipient recipient, NotificationType type, String message, String data,
            ReferenceType referenceType, Long referenceId) {
        Notification notification = Notification.builder()
                .userId(recipient.id())
                .username(recipient.username())
                .type(type)
                .message(message)
                .data(data)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .read(false)
                .build();
        notificationRepository.save(notification);
        log.info("Notification created for user {} - type: {}", recipient.username(), type);
    }

    @Override
    public void createNotificationForUsers(
            List<Recipient> recipients, NotificationType type, String message, String data,
            ReferenceType referenceType, Long referenceId) {
        if (recipients == null || recipients.isEmpty()) return;
        List<Notification> notifications = recipients.stream()
                .map(user -> Notification.builder()
                        .userId(user.id())
                        .username(user.username())
                        .type(type)
                        .message(message)
                        .data(data)
                        .referenceType(referenceType)
                        .referenceId(referenceId)
                        .read(false)
                        .build())
                .collect(Collectors.toList());
        notificationRepository.saveAll(notifications);
        log.info("Notification created for {} users - type: {}", recipients.size(), type);
    }

    @Override
    public NotificationResponse markAsRead(Long notificationId) {
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with ID: " + notificationId));
        notification.setRead(true);
        return notificationMapper.toResponse(notificationRepository.save(notification));
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadNotifications() {
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();
        return notificationMapper.toResponseList(
                notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(currentUser.id()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getAllNotifications() {
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();
        return notificationMapper.toResponseList(
                notificationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.id()));
    }

    @Override
    public void deleteNotification(Long notificationId) {
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with ID: " + notificationId));
        notificationRepository.delete(notification);
        log.info("Notification {} deleted by user {}", notificationId, currentUser.email());
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread() {
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();
        return notificationRepository.countByUserIdAndReadFalse(currentUser.id());
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deleteOldNotifications() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        int deleted = notificationRepository.deleteOlderThan(cutoff);
        log.info("Scheduled cleanup: deleted {} notifications older than 30 days", deleted);
    }

    @Override
    public int markAllAsRead() {
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();
        int updated = notificationRepository.markAllAsReadByUserId(currentUser.id());
        log.info("Marked {} notifications as read for user {}", updated, currentUser.email());
        return updated;
    }
}