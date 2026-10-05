package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE (:action IS NULL OR a.action = :action) ORDER BY a.createdAt DESC")
    Page<AuditLog> findFiltered(@Param("action") String action, Pageable pageable);
}
