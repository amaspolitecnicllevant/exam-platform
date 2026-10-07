package com.examplatform.controller;

import com.examplatform.domain.model.Question;
import com.examplatform.domain.model.User;
import com.examplatform.domain.service.AuditLogService;
import com.examplatform.domain.service.EditorPreguntesService;
import com.examplatform.dto.QuestionDto;
import com.examplatform.dto.QuestionEditRequest;
import com.examplatform.dto.QuestionFileDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/** Edició del contingut d'un examen (preguntes i imatges) mentre no té sessions d'alumnes. */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
public class EditorPreguntesController {

    private final EditorPreguntesService editor;
    private final AuditLogService auditLog;

    @GetMapping("/api/exams/{examId}/editable")
    public EditorPreguntesService.Estat estat(@PathVariable UUID examId, @AuthenticationPrincipal User user) {
        return editor.estat(examId, user);
    }

    @PostMapping("/api/exams/{examId}/questions")
    public QuestionDto afegeix(@PathVariable UUID examId, @RequestBody QuestionEditRequest req,
                               @AuthenticationPrincipal User user) {
        Question q = editor.afegeix(examId, req, user);
        auditLog.log(user.getId(), "QUESTION_ADDED", "examen " + examId + ", pregunta " + q.getOrdre() + " (" + q.getTipus() + ")");
        return QuestionDto.from(q);
    }

    @PutMapping("/api/exams/{examId}/questions/{questionId}")
    public QuestionDto actualitza(@PathVariable UUID examId, @PathVariable UUID questionId,
                                  @RequestBody QuestionEditRequest req, @AuthenticationPrincipal User user) {
        Question q = editor.actualitza(examId, questionId, req, user);
        auditLog.log(user.getId(), "QUESTION_EDITED", "examen " + examId + ", pregunta " + q.getOrdre() + " (" + questionId + ")");
        return QuestionDto.from(q);
    }

    @DeleteMapping("/api/exams/{examId}/questions/{questionId}")
    public ResponseEntity<Void> elimina(@PathVariable UUID examId, @PathVariable UUID questionId,
                                        @AuthenticationPrincipal User user) {
        editor.elimina(examId, questionId, user);
        auditLog.log(user.getId(), "QUESTION_DELETED", "examen " + examId + ", pregunta " + questionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/exams/{examId}/questions/{questionId}/move")
    public ResponseEntity<Void> mou(@PathVariable UUID examId, @PathVariable UUID questionId,
                                    @RequestBody Map<String, Integer> body, @AuthenticationPrincipal User user) {
        Integer posicio = body.get("posicio");
        if (posicio == null) throw new IllegalArgumentException("Falta la posició de destí");
        editor.mou(examId, questionId, posicio, user);
        auditLog.log(user.getId(), "QUESTION_MOVED", "examen " + examId + ", pregunta " + questionId + " → " + posicio);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/api/questions/{questionId}/images", consumes = "multipart/form-data")
    public QuestionFileDto pujaImatge(@PathVariable UUID questionId, @RequestParam("file") MultipartFile file,
                                      @AuthenticationPrincipal User user) throws IOException {
        QuestionFileDto r = editor.pujaImatge(questionId, file, user);
        auditLog.log(user.getId(), "QUESTION_IMAGE_UPLOADED", "pregunta " + questionId + ": " + r.filename());
        return r;
    }
}
