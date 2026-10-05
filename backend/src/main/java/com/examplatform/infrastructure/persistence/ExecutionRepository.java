package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.Execution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExecutionRepository extends JpaRepository<Execution, UUID> {
    List<Execution> findByAnswerIdOrderByExecutedAtDesc(UUID answerId);
}
