package com.examplatform.controller;

import com.examplatform.domain.model.User;
import com.examplatform.domain.service.FitxerRespostaService;
import com.examplatform.dto.AnswerDto;
import com.examplatform.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Lliurament de fitxers de l'alumne a les preguntes de tipus fitxer. */
@RestController
@RequestMapping("/api/sessions/{sessionId}/questions/{questionId}/file")
@RequiredArgsConstructor
public class RespostaFitxerController {

    private final FitxerRespostaService fitxerService;

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasRole('STUDENT')")
    public AnswerDto puja(@PathVariable UUID sessionId, @PathVariable UUID questionId,
                          @RequestParam("file") MultipartFile file,
                          @AuthenticationPrincipal User student,
                          HttpServletRequest request) throws IOException {
        return fitxerService.puja(sessionId, questionId, file, student, IpUtil.clientIp(request));
    }

    @DeleteMapping
    @PreAuthorize("hasRole('STUDENT')")
    public AnswerDto esborra(@PathVariable UUID sessionId, @PathVariable UUID questionId,
                             @AuthenticationPrincipal User student,
                             HttpServletRequest request) {
        return fitxerService.esborra(sessionId, questionId, student, IpUtil.clientIp(request));
    }

    /** Descàrrega: l'alumne propietari o qui gestiona l'examen. Sempre com a adjunt, mai interpretat pel navegador. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<org.springframework.core.io.Resource> descarrega(
            @PathVariable UUID sessionId, @PathVariable UUID questionId,
            @AuthenticationPrincipal User usuari) {
        var d = fitxerService.descarrega(sessionId, questionId, usuari);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(d.nom(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(d.recurs());
    }
}
