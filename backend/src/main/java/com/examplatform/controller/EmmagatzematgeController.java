package com.examplatform.controller;

import com.examplatform.domain.service.emmagatzematge.Agrupacio;
import com.examplatform.domain.service.emmagatzematge.EmmagatzematgeService;
import com.examplatform.dto.EmmagatzematgeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Auditoria de l'espai que ocupen els exàmens (només administradors). */
@RestController
@RequestMapping("/api/admin/emmagatzematge")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class EmmagatzematgeController {

    private final EmmagatzematgeService service;

    /** @param agrupa professor (per defecte), departament, cicle, modul o examen */
    @GetMapping
    public EmmagatzematgeDto auditoria(@RequestParam(defaultValue = "professor") String agrupa) {
        return service.auditoria(Agrupacio.de(agrupa));
    }
}
