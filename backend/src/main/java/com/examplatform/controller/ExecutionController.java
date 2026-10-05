package com.examplatform.controller;

import com.examplatform.config.ExecutionRateLimiter;
import com.examplatform.domain.model.User;
import com.examplatform.domain.service.CorrectionService;
import com.examplatform.dto.AnswerDto;
import com.examplatform.dto.ExecutionResultDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
public class ExecutionController {

    private final CorrectionService correctionService;
    private final ExecutionRateLimiter rateLimiter;
    private final com.examplatform.domain.service.AuditLogService auditLog;
    private final com.examplatform.config.ExecucionsInteractives execucions;

    @PostMapping("/{answerId}/run")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ExecutionResultDto> run(@PathVariable UUID answerId,
                                                  @AuthenticationPrincipal User currentUser) {
        if (!rateLimiter.tryConsume(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Massa execucions. Espera un minut.");
        }
        return ResponseEntity.ok(execucions.executa(() -> correctionService.executeAs(answerId, currentUser)));
    }

    @PatchMapping("/{answerId}/score")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ResponseEntity<AnswerDto> setScore(@PathVariable UUID answerId,
                                              @RequestBody AnswerDto.ScoreRequest req,
                                              @AuthenticationPrincipal User currentUser) {
        if (req.manualScore() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Puntuació invàlida");
        }
        AnswerDto result = correctionService.setManualScore(answerId, req.manualScore(), currentUser);
        auditLog.log(currentUser.getId(), "SCORE_SET", "resposta " + answerId + " → " + req.manualScore());
        return ResponseEntity.ok(result);
    }

    /** Comentari del professor a una resposta. */
    @PatchMapping("/{answerId}/comment")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ResponseEntity<AnswerDto> setComment(@PathVariable UUID answerId,
                                                @RequestBody AnswerDto.ComentariRequest req,
                                                @AuthenticationPrincipal User currentUser) {
        AnswerDto result = correctionService.setComentari(answerId, req.comentari(), currentUser);
        auditLog.log(currentUser.getId(), "COMMENT_SET", "resposta " + answerId);
        return ResponseEntity.ok(result);
    }

    /** Accepta la nota proposada d'una resposta com a nota revisada. */
    @PostMapping("/{answerId}/accept")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ResponseEntity<AnswerDto> accept(@PathVariable UUID answerId,
                                            @AuthenticationPrincipal User currentUser) {
        AnswerDto result = correctionService.acceptaProposta(answerId, currentUser);
        auditLog.log(currentUser.getId(), "PROPOSAL_ACCEPTED", "resposta " + answerId + " → " + result.manualScore());
        return ResponseEntity.ok(result);
    }

    /** Accepta totes les propostes pendents d'un examen (o d'una sola sessió amb ?sessionId=). */
    @PostMapping("/exam/{examId}/accept-all")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ResponseEntity<java.util.Map<String, Integer>> acceptAll(
            @PathVariable UUID examId,
            @RequestParam(required = false) UUID sessionId,
            @AuthenticationPrincipal User currentUser) {
        int acceptades = correctionService.acceptaPropostes(examId, sessionId, currentUser);
        auditLog.log(currentUser.getId(), "PROPOSALS_ACCEPTED", "examen " + examId
                + (sessionId != null ? " sessió " + sessionId : "") + ": " + acceptades + " respostes");
        return ResponseEntity.ok(java.util.Map.of("acceptades", acceptades));
    }
}
