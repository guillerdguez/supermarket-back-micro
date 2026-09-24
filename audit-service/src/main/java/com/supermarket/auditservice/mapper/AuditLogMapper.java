package com.supermarket.auditservice.mapper;

import com.supermarket.auditservice.dto.audit.AuditLogResponse;
import com.supermarket.auditservice.model.audit.AuditLog;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuditLogMapper {

    public AuditLogResponse toResponse(AuditLog log) {
        if (log == null) return null;

        return AuditLogResponse.builder()
                .id(log.getId())
                .username(log.getUsername())
                .action(log.getAction())
                .details(log.getDetails())
                .ipAddress(log.getIpAddress())
                .timestamp(log.getTimestamp())
                .status(log.getStatus())
                .build();
    }

    public List<AuditLogResponse> toResponseList(List<AuditLog> logs) {
        if (logs == null) return null;
        return logs.stream()
                .map(this::toResponse)
                .toList();
    }
}