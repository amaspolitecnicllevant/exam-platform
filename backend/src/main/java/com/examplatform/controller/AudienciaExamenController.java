package com.examplatform.controller;

import com.examplatform.domain.model.Exam;
import com.examplatform.domain.model.ExamStatus;
import com.examplatform.domain.model.User;
import com.examplatform.domain.service.AuditLogService;
import com.examplatform.domain.service.AudienciaExamenService;
import com.examplatform.domain.service.ExamService;
import com.examplatform.dto.AlumneAccesDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Alumnes amb accés a un examen restringit: veure'ls, afegir-ne i treure'n (p. ex. qui el fa un altre dia). */
@RestController
@RequestMapping("/api/exams/{examId}/alumnes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
public class AudienciaExamenController {

    private final ExamService examService;
    private final AudienciaExamenService audiencia;
    private final AuditLogService auditLog;

    public record AfegeixRequest(List<UUID> alumneIds) {}

    private Exam examGestionat(UUID examId, User user) {
        Exam exam = examService.getEntity(examId);
        examService.assertOwnership(exam, user);
        return exam;
    }

    @GetMapping
    public AlumneAccesDto.Llista llista(@PathVariable UUID examId, @AuthenticationPrincipal User user) {
        return audiencia.llista(examGestionat(examId, user));
    }

    @PostMapping
    public AlumneAccesDto.Llista afegeix(@PathVariable UUID examId, @RequestBody AfegeixRequest req,
                                         @AuthenticationPrincipal User user) {
        Exam exam = examGestionat(examId, user);
        if (!exam.isRestringit()) {
            throw new IllegalStateException("Aquest examen és per a tots els alumnes del mòdul: no cal afegir-hi ningú");
        }
        if (exam.getStatus() != ExamStatus.PUBLISHED) {
            throw new IllegalStateException("L'examen ha d'estar actiu per afegir-hi alumnes");
        }
        int n = audiencia.assigna(exam, req.alumneIds());
        auditLog.log(user.getId(), "EXAM_ALUMNES_AFEGITS", "examen " + examId + ": " + n + " alumne(s)");
        return audiencia.llista(exam);
    }

    @DeleteMapping("/{alumneId}")
    public ResponseEntity<Void> treu(@PathVariable UUID examId, @PathVariable UUID alumneId,
                                     @AuthenticationPrincipal User user) {
        audiencia.treu(examGestionat(examId, user), alumneId);
        auditLog.log(user.getId(), "EXAM_ALUMNE_TRET", "examen " + examId + ", alumne " + alumneId);
        return ResponseEntity.noContent().build();
    }
}
