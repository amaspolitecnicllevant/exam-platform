package com.examplatform.controller;

import com.examplatform.domain.model.Exam;
import com.examplatform.domain.model.User;
import com.examplatform.domain.service.AuditLogService;
import com.examplatform.domain.service.ExamService;
import com.examplatform.domain.service.PesosPartsService;
import com.examplatform.dto.ExamDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pes (%) de cada part de l'examen sobre la nota total; es pot canviar un cop fet l'examen. */
@RestController
@RequestMapping("/api/exams/{examId}/pesos-parts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
public class PesosPartsController {

    private final ExamService examService;
    private final PesosPartsService pesosParts;
    private final AuditLogService auditLog;

    public record PesosRequest(List<PesosPartsService.PesPart> pesos) {}

    private Exam examGestionat(UUID examId, User user) {
        Exam exam = examService.getEntity(examId);
        examService.assertOwnership(exam, user);
        return exam;
    }

    @PutMapping
    public ResponseEntity<?> fixa(@PathVariable UUID examId, @RequestBody PesosRequest req,
                                  @AuthenticationPrincipal User user) {
        Exam exam = examGestionat(examId, user);
        try {
            String resum = pesosParts.fixa(exam, req.pesos());
            auditLog.log(user.getId(), "EXAM_PESOS_PARTS", "examen " + examId + ": " + resum);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.ok(examService.findById(examId, user));
    }

    /** Torna a la puntuació original (cada pregunta amb els seus punts). */
    @DeleteMapping
    public ExamDto treu(@PathVariable UUID examId, @AuthenticationPrincipal User user) {
        pesosParts.treu(examGestionat(examId, user));
        auditLog.log(user.getId(), "EXAM_PESOS_PARTS_TRETS", "examen " + examId);
        return examService.findById(examId, user);
    }
}
