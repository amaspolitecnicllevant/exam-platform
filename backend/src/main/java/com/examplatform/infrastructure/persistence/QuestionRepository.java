package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID> {
    List<Question> findByExamIdOrderByOrdreAsc(UUID examId);
}
