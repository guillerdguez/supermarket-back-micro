package com.supermarket.notificationservice.unit.service;

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
import com.supermarket.notificationservice.service.business.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @InjectMocks
    private NotificationServiceImpl notificationService;

    private AuthenticatedUser mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new AuthenticatedUser(1L, "cashier@test.com", "cashier-test", "CASHIER", 1L);
    }

    @Test
    @DisplayName("createNotification - should save notification for given user")
    void createNotification_ShouldSaveNotification() {
        Recipient recipient = new Recipient(2L, "manager-test");
        notificationService.createNotification(recipient, NotificationType.LOW_STOCK, "Low stock alert", null, null, null);
        then(notificationRepository).should().save(argThat(n ->
                n.getUserId().equals(recipient.id()) && n.getUsername().equals("manager-test") &&
                        n.getType() == NotificationType.LOW_STOCK &&
                        n.getMessage().equals("Low stock alert") &&
                        !n.getRead()
        ));
    }

    @Test
    @DisplayName("createNotificationForUsers - should save one notification per user")
    void createNotificationForUsers_ShouldSaveAll() {
        List<Recipient> users = List.of(new Recipient(3L, "admin-test"), new Recipient(2L, "manager-test"));
        notificationService.createNotificationForUsers(users, NotificationType.SALE_CANCELLED, "Sale cancelled", null, ReferenceType.SALE, 1L);
        then(notificationRepository).should().saveAll(argThat(list -> {
            List<?> items = (List<?>) list;
            return items.size() == 2;
        }));
    }

    @Test
    @DisplayName("createNotificationForUsers - should do nothing when list is empty")
    void createNotificationForUsers_EmptyList_ShouldDoNothing() {
        notificationService.createNotificationForUsers(List.of(), NotificationType.LOW_STOCK, "msg", null, null, null);
        then(notificationRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("markAsRead - should mark notification as read")
    void markAsRead_ShouldSetReadTrue() {
        given(currentUserProvider.getCurrentUser()).willReturn(mockUser);
        Notification notification = Notification.builder()
                .id(1L).userId(mockUser.id()).username(mockUser.username()).type(NotificationType.LOW_STOCK)
                .message("Low stock").read(false).build();
        NotificationResponse response = NotificationResponse.builder().id(1L).read(true).build();
        given(notificationRepository.findByIdAndUserId(1L, mockUser.id()))
                .willReturn(Optional.of(notification));
        given(notificationRepository.save(notification)).willReturn(notification);
        given(notificationMapper.toResponse(notification)).willReturn(response);
        NotificationResponse result = notificationService.markAsRead(1L);
        assertThat(result.getRead()).isTrue();
        assertThat(notification.getRead()).isTrue();
        then(notificationRepository).should().save(notification);
    }

    @Test
    @DisplayName("markAsRead - should throw when notification not found or not owned")
    void markAsRead_WhenNotFound_ShouldThrow() {
        given(currentUserProvider.getCurrentUser()).willReturn(mockUser);
        given(notificationRepository.findByIdAndUserId(99L, mockUser.id()))
                .willReturn(Optional.empty());
        assertThatThrownBy(() -> notificationService.markAsRead(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getUnreadNotifications - should return only unread for current user")
    void getUnreadNotifications_ShouldReturnUnread() {
        given(currentUserProvider.getCurrentUser()).willReturn(mockUser);
        Notification notification = Notification.builder()
                .id(1L).userId(mockUser.id()).username(mockUser.username()).type(NotificationType.LOW_STOCK)
                .message("Low stock").read(false).build();
        NotificationResponse response = NotificationResponse.builder().id(1L).read(false).build();
        given(notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(mockUser.id()))
                .willReturn(List.of(notification));
        given(notificationMapper.toResponseList(List.of(notification))).willReturn(List.of(response));
        List<NotificationResponse> result = notificationService.getUnreadNotifications();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRead()).isFalse();
    }

    @Test
    @DisplayName("getAllNotifications - should return all notifications for current user")
    void getAllNotifications_ShouldReturnAll() {
        given(currentUserProvider.getCurrentUser()).willReturn(mockUser);
        Notification n1 = Notification.builder().id(1L).userId(mockUser.id()).username(mockUser.username())
                .type(NotificationType.LOW_STOCK).message("msg1").read(false).build();
        Notification n2 = Notification.builder().id(2L).userId(mockUser.id()).username(mockUser.username())
                .type(NotificationType.SALE_CANCELLED).message("msg2").read(true).build();
        given(notificationRepository.findByUserIdOrderByCreatedAtDesc(mockUser.id()))
                .willReturn(List.of(n1, n2));
        given(notificationMapper.toResponseList(List.of(n1, n2)))
                .willReturn(List.of(
                        NotificationResponse.builder().id(1L).read(false).build(),
                        NotificationResponse.builder().id(2L).read(true).build()));
        List<NotificationResponse> result = notificationService.getAllNotifications();
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("deleteNotification - should delete when owned by current user")
    void deleteNotification_WhenOwned_ShouldDelete() {
        given(currentUserProvider.getCurrentUser()).willReturn(mockUser);
        Notification notification = Notification.builder()
                .id(1L).userId(mockUser.id()).username(mockUser.username()).type(NotificationType.LOW_STOCK)
                .message("msg").read(false).build();
        given(notificationRepository.findByIdAndUserId(1L, mockUser.id()))
                .willReturn(Optional.of(notification));
        notificationService.deleteNotification(1L);
        then(notificationRepository).should().delete(notification);
    }

    @Test
    @DisplayName("deleteNotification - should throw when not found or not owned")
    void deleteNotification_WhenNotFound_ShouldThrow() {
        given(currentUserProvider.getCurrentUser()).willReturn(mockUser);
        given(notificationRepository.findByIdAndUserId(99L, mockUser.id()))
                .willReturn(Optional.empty());
        assertThatThrownBy(() -> notificationService.deleteNotification(99L))
                .isInstanceOf(ResourceNotFoundException.class);
        then(notificationRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("countUnread - should return count for current user")
    void countUnread_ShouldReturnCount() {
        given(currentUserProvider.getCurrentUser()).willReturn(mockUser);
        given(notificationRepository.countByUserIdAndReadFalse(mockUser.id())).willReturn(5L);
        long count = notificationService.countUnread();
        assertThat(count).isEqualTo(5L);
    }

    @Test
    @DisplayName("deleteOldNotifications - should delete notifications older than 30 days")
    void deleteOldNotifications_ShouldInvokeRepository() {
        given(notificationRepository.deleteOlderThan(any(LocalDateTime.class))).willReturn(12);
        notificationService.deleteOldNotifications();
        then(notificationRepository).should().deleteOlderThan(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("markAllAsRead - should return count of updated notifications")
    void markAllAsRead_ShouldReturnCount() {
        given(currentUserProvider.getCurrentUser()).willReturn(mockUser);
        given(notificationRepository.markAllAsReadByUserId(mockUser.id())).willReturn(4);

        int result = notificationService.markAllAsRead();

        assertThat(result).isEqualTo(4);
        then(notificationRepository).should().markAllAsReadByUserId(mockUser.id());
    }
}