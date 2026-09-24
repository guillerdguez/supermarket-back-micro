package com.supermarket.auditservice.service.business;

import com.supermarket.auditservice.dto.audit.AuditLogResponse;
import com.supermarket.auditservice.event.AuthActivityEvent;
import com.supermarket.auditservice.model.processed.ProcessedEvent;
import com.supermarket.auditservice.model.processed.ProcessedEventRepository;
import com.supermarket.commons.exception.ResourceNotFoundException;
import com.supermarket.auditservice.mapper.AuditLogMapper;
import com.supermarket.auditservice.model.audit.AuditLog;
import com.supermarket.auditservice.model.audit.AuditStatus;
import com.supermarket.auditservice.repository.AuditLogRepository;
import com.supermarket.auditservice.specification.AuditLogSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;
    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public void record(AuthActivityEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("Skipping duplicated event {} [{}]", event.eventType(), event.eventId());
            return;
        }
        processedEventRepository.save(new ProcessedEvent(event.eventId(), event.eventType(), LocalDateTime.now()));
        AuditLog auditLog = AuditLog.builder()
                .username(event.username())
                .action(event.action())
                .details(event.details())
                .ipAddress(event.ipAddress())
                .timestamp(event.occurredAt() != null ? event.occurredAt() : LocalDateTime.now())
                .status(AuditStatus.valueOf(event.status()))
                .build();
        auditLogRepository.save(auditLog);
        log.debug("Audit log created: {} by {} from {}", event.action(), event.username(), event.ipAddress());
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAll(
            String username,
            String action,
            AuditStatus status,
            LocalDateTime fromDate,
            LocalDateTime toDate) {
        Specification<AuditLog> spec =
                AuditLogSpecifications.withFilters(username, action, status, fromDate, toDate);
        Sort sort = Sort.by(Sort.Direction.DESC, "timestamp");
        return auditLogRepository.findAll(spec, sort).stream().map(auditLogMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AuditLogResponse getById(Long id) {
        AuditLog auditLog = auditLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Audit log not found with ID: " + id));
        return auditLogMapper.toResponse(auditLog);
    }
}
