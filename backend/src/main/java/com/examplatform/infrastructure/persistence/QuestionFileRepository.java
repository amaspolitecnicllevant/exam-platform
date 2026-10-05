package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.QuestionFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestionFileRepository extends JpaRepository<QuestionFile, UUID> {
    List<QuestionFile> findByQuestionId(UUID questionId);
    Optional<QuestionFile> findByIdAndQuestionId(UUID id, UUID questionId);
}
