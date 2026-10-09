package com.examplatform.controller;

import com.examplatform.domain.model.User;
import com.examplatform.domain.service.RevisioIaService;
import com.examplatform.dto.RevisioIaDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Importació de la revisió d'un examen feta per una IA (a partir de l'exportació «respostes»).
 * Qui importa ha de poder gestionar l'examen (el servei ho comprova).
 */
@RestController
@RequestMapping("/api/exams/{examId}/revisio-ia")
@PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
@RequiredArgsConstructor
public class RevisioIaController {

    private final RevisioIaService servei;

    /** Mostra què canviaria. No desa res. */
    @PostMapping("/previsualitza")
    public RevisioIaDto previsualitza(@PathVariable UUID examId,
                                      @RequestBody RevisioIaDto.PrevisualitzaRequest req,
                                      @AuthenticationPrincipal User user) {
        return servei.previsualitza(examId, req.text(), user);
    }

    /** Desa només les files acceptades. */
    @PostMapping("/aplica")
    public RevisioIaDto.Aplicacio aplica(@PathVariable UUID examId,
                                         @RequestBody RevisioIaDto.AplicaRequest req,
                                         @AuthenticationPrincipal User user) {
        return servei.aplica(examId, req.text(), req.acceptades(), user);
    }
}
