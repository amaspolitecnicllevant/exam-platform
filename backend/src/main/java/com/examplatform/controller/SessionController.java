package com.examplatform.controller;

import com.examplatform.domain.model.User;
import com.examplatform.domain.service.AuditLogService;
import com.examplatform.domain.service.SessionService;
import com.examplatform.dto.*;
import com.examplatform.util.IpUtil;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final AuditLogService auditLog;

    @PostMapping("/start/{examId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SessionDto> start(@PathVariable UUID examId,
                                            @AuthenticationPrincipal User student,
                                            HttpServletRequest request) {
        String clientIp = IpUtil.clientIp(request);
        return ResponseEntity.ok(sessionService.startOrResume(examId, student, clientIp));
    }

    @PutMapping("/{sessionId}/answers")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<AnswerDto> saveAnswer(@PathVariable UUID sessionId,
                                                @RequestBody AnswerDto.SaveRequest req,
                                                @AuthenticationPrincipal User student,
                                                HttpServletRequest request) {
        return ResponseEntity.ok(sessionService.saveAnswer(sessionId, req, student, IpUtil.clientIp(request)));
    }

    @PostMapping("/{sessionId}/focus-loss")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> focusLoss(@PathVariable UUID sessionId,
                                          @AuthenticationPrincipal User student) {
        sessionService.recordFocusLoss(sessionId, student);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/restart/{examId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SessionDto> restart(@PathVariable UUID examId,
                                              @AuthenticationPrincipal User student,
                                              HttpServletRequest request) {
        SessionDto result = sessionService.restartByStudent(examId, student, IpUtil.clientIp(request));
        auditLog.log(student.getId(), "EXAM_RESTARTED_BY_STUDENT", "examen " + examId + " sessió " + result.id());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{sessionId}/reset")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ResponseEntity<SessionDto> reset(@PathVariable UUID sessionId,
                                            @AuthenticationPrincipal User professor) {
        SessionDto result = sessionService.resetSession(sessionId, professor);
        auditLog.log(professor.getId(), "SESSION_RESET", "sessió " + sessionId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{sessionId}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SessionDto> submit(@PathVariable UUID sessionId,
                                             @AuthenticationPrincipal User student) {
        SessionDto result = sessionService.submit(sessionId, student);
        auditLog.log(student.getId(), "EXAM_SUBMITTED", sessionId.toString());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('STUDENT')")
    public List<SessionDto> mySubmitted(@AuthenticationPrincipal User student) {
        return sessionService.findMySubmitted(student.getId());
    }

    @GetMapping("/my/historial")
    @PreAuthorize("hasRole('STUDENT')")
    public List<HistorialDto> historial(@AuthenticationPrincipal User student) {
        return sessionService.historial(student);
    }

    @GetMapping("/exam/{examId}")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public List<SessionDto> byExam(@PathVariable UUID examId,
                                   @AuthenticationPrincipal User currentUser) {
        return sessionService.findByExam(examId, currentUser);
    }

    @GetMapping("/exam/{examId}/monitor")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public List<MonitorDto> monitor(@PathVariable UUID examId,
                                    @AuthenticationPrincipal User user) {
        return sessionService.monitor(examId, user);
    }

    @GetMapping("/{sessionId}")
    @PreAuthorize("isAuthenticated()")
    public SessionDto findById(@PathVariable UUID sessionId,
                               @AuthenticationPrincipal User currentUser) {
        return sessionService.findById(sessionId, currentUser);
    }
}
