package com.examplatform.domain.service;

import com.examplatform.domain.model.AuditLog;
import com.examplatform.infrastructure.persistence.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repository;

    public void log(UUID userId, String action, String resource) {
        if (resource != null && resource.length() > 255) resource = resource.substring(0, 252) + "...";
        repository.save(AuditLog.builder()
                .userId(userId)
                .action(action)
                .resource(resource)
                .ipAddress(extractIp())
                .build());
    }

    /** L'usuari ja havia fet aquesta acció abans (des de qualsevol adreça)? */
    public boolean jaHaFet(UUID userId, String action) {
        return repository.existsByUserIdAndAction(userId, action);
    }

    /** L'usuari ja havia fet aquesta acció des d'aquesta adreça? */
    public boolean jaHaFetDes(UUID userId, String action, String ip) {
        return ip != null && repository.existsByUserIdAndActionAndIpAddress(userId, action, ip);
    }

    private String extractIp() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest req = attrs.getRequest();
            return com.examplatform.util.IpUtil.clientIp(req);
        } catch (Exception ignored) {
            return null;
        }
    }
}
