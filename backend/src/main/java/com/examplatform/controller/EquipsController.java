package com.examplatform.controller;

import com.examplatform.config.InformeEquipsRateLimiter;
import com.examplatform.domain.model.User;
import com.examplatform.domain.service.EquipsService;
import com.examplatform.dto.EquipsDto;
import com.examplatform.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Estat dels ordinadors de les aules. L'informe l'envia cada ordinador (sense sessió: només amb el testimoni
 * compartit, i només des de la xarxa d'una aula); la consulta, per a professors i administradors; fixar la referència i esborrar, només per als administradors.
 */
@RestController
@RequiredArgsConstructor
public class EquipsController {

    private final EquipsService servei;
    private final InformeEquipsRateLimiter limitador;

    /** Informe d'un ordinador (formulari, perquè el script de l'ordinador no hagi d'escapar JSON). */
    @PostMapping(path = "/api/equips/informe", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                 produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> informe(@RequestHeader(name = "X-Equip-Token", required = false) String token,
                                        @RequestParam String nom,
                                        @RequestParam(required = false) String integritat,
                                        @RequestParam(required = false) Boolean arribaPlataforma,
                                        @RequestParam(required = false) Boolean arribaIsard,
                                        @RequestParam(required = false) String navegador,
                                        @RequestParam(required = false) Integer discLliureMb,
                                        @RequestParam(required = false) Long uptimeSegons,
                                        @RequestParam(required = false) Integer usuarisDins,
                                        @RequestParam(required = false) String restauracio,
                                        HttpServletRequest request) {
        String ip = IpUtil.clientIp(request);
        limitador.consumeix(ip);
        EquipsService.Resposta r = servei.registraInforme(token, ip, new EquipsService.Informe(nom, integritat,
                arribaPlataforma, arribaIsard, navegador, discLliureMb, uptimeSegons, usuarisDins, restauracio),
                LocalDateTime.now());
        // Text pla i curt perquè el script de l'ordinador el pugui llegir: RESTAURA o OK. Mai codi.
        return ResponseEntity.ok(r.restaura() ? "RESTAURA" : "OK");
    }

    /** Els professors també la veuen: abans d'un examen han de saber quins ordinadors fa temps que no s'encenen. */
    @GetMapping("/api/aules/{aulaId}/equips")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public EquipsDto equips(@PathVariable UUID aulaId) {
        return servei.equipsDeLAula(aulaId, LocalDateTime.now());
    }

    @PostMapping("/api/equips/{equipId}/referencia")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> fixaReferencia(@PathVariable UUID equipId, @AuthenticationPrincipal User admin) {
        servei.fixaReferencia(equipId, admin, LocalDateTime.now());
        return ResponseEntity.noContent().build();
    }

    /** Demana restaurar un ordinador. No envia codi: només el marca perquè l'ordinador executi la seva còpia local de l'script. */
    @PostMapping("/api/equips/{equipId}/restaura")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> demanaRestauracio(@PathVariable UUID equipId, @AuthenticationPrincipal User admin) {
        servei.demanaRestauracio(equipId, admin, LocalDateTime.now());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/equips/{equipId}/restaura")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> cancelaRestauracio(@PathVariable UUID equipId, @AuthenticationPrincipal User admin) {
        servei.cancelaRestauracio(equipId, admin);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/equips/{equipId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> esborra(@PathVariable UUID equipId, @AuthenticationPrincipal User admin) {
        servei.esborra(equipId, admin);
        return ResponseEntity.noContent().build();
    }
}
