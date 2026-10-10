package com.examplatform.controller;

import com.examplatform.domain.service.AlertesSeguretatService;
import com.examplatform.dto.AlertaDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/** Alertes de seguretat actives, només per als administradors. */
@RestController
@RequestMapping("/api/admin/alertes")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AlertesSeguretatController {

    private final AlertesSeguretatService servei;

    @GetMapping
    public List<AlertaDto> alertes() {
        return servei.alertes(LocalDateTime.now());
    }
}
