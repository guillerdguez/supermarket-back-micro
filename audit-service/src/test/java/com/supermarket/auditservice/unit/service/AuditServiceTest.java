package com.supermarket.auditservice.unit.service;

import com.supermarket.auditservice.dto.audit.AuditLogResponse;
import com.supermarket.commons.exception.ResourceNotFoundException;
import com.supermarket.auditservice.mapper.AuditLogMapper;
import com.supermarket.auditservice.model.audit.AuditLog;
import com.supermarket.auditservice.model.audit.AuditStatus;
import com.supermarket.auditservice.repository.AuditLogRepository;
import com.supermarket.auditservice.event.AuthActivityEvent;
import com.supermarket.auditservice.model.processed.ProcessedEventRepository;
import com.supermarket.auditservice.service.business.AuditService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private AuditLogMapper auditLogMapper;
    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private AuditService auditService;

    @Nested
    @DisplayName("record")
    class Record {
        private AuthActivityEvent event(String id) {
            return new AuthActivityEvent(id, "auth.login.success", LocalDateTime.of(2026, 9, 24, 10, 15),
                    "admin@supermarket.com", "LOGIN_SUCCESS", "User logged in successfully", "10.0.0.7", "SUCCESS");
        }

        @Test
        @DisplayName("should save an audit log with the data carried by the event, including the original IP and time")
        void record_SavesAuditLog() {
            auditService.record(event("evt-1"));

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            then(auditLogRepository).should().save(captor.capture());

            AuditLog saved = captor.getValue();
            assertThat(saved.getUsername()).isEqualTo("admin@supermarket.com");
            assertThat(saved.getAction()).isEqualTo("LOGIN_SUCCESS");
            assertThat(saved.getDetails()).isEqualTo("User logged in successfully");
            assertThat(saved.getIpAddress()).isEqualTo("10.0.0.7");
            assertThat(saved.getStatus()).isEqualTo(AuditStatus.SUCCESS);
            assertThat(saved.getTimestamp()).isEqualTo(LocalDateTime.of(2026, 9, 24, 10, 15));
        }

        @Test
        @DisplayName("should ignore a redelivered event")
        void record_DuplicatedEvent_IsSkipped() {
            given(processedEventRepository.existsById("evt-1")).willReturn(true);

            auditService.record(event("evt-1"));

            then(auditLogRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should propagate storage failures so the Kafka error handler can retry and dead-letter the event")
        void record_SaveFails_Propagates() {
            given(auditLogRepository.save(any())).willThrow(new RuntimeException("DB down"));

            assertThatThrownBy(() -> auditService.record(event("evt-2")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DB down");
        }
    }

    @Nested
    @DisplayName("getAll")
    class GetAll {
        @Test
        @DisplayName("should filter with a specification and map results")
        void getAll_ReturnsMappedList() {
            AuditLog log = AuditLog.builder()
                    .id(1L).username("admin").action("LOGIN_SUCCESS")
                    .timestamp(LocalDateTime.now()).status(AuditStatus.SUCCESS)
                    .build();
            AuditLogResponse response = AuditLogResponse.builder()
                    .id(1L).username("admin").action("LOGIN_SUCCESS").status(AuditStatus.SUCCESS)
                    .build();

            given(auditLogRepository.findAll(any(Specification.class), any(Sort.class))).willReturn(List.of(log));
            given(auditLogMapper.toResponse(log)).willReturn(response);

            List<AuditLogResponse> result = auditService.getAll(
                    "admin", "LOGIN_SUCCESS", AuditStatus.SUCCESS, null, null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUsername()).isEqualTo("admin");
        }
    }

    @Nested
    @DisplayName("getById")
    class GetById {
        @Test
        @DisplayName("should return the mapped audit log when found")
        void getById_Found_ReturnsMapped() {
            AuditLog log = AuditLog.builder().id(1L).username("admin").action("LOGIN_SUCCESS").build();
            AuditLogResponse response = AuditLogResponse.builder().id(1L).username("admin").build();

            given(auditLogRepository.findById(1L)).willReturn(Optional.of(log));
            given(auditLogMapper.toResponse(log)).willReturn(response);

            AuditLogResponse result = auditService.getById(1L);

            assertThat(result.getUsername()).isEqualTo("admin");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when not found")
        void getById_NotFound_Throws() {
            given(auditLogRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> auditService.getById(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }
}
