package com.examplatform.controller;

import com.examplatform.domain.model.User;
import com.examplatform.domain.service.ResumNotesService;
import com.examplatform.dto.ResumNotesDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Aprovats i mitjanes de tots els exàmens del professor, agrupats per UT. */
@RestController
@RequestMapping("/api/resum-notes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
public class ResumNotesController {

    private final ResumNotesService resumNotes;

    @GetMapping
    public ResumNotesDto resum(@AuthenticationPrincipal User user) {
        return resumNotes.resum(user);
    }
}
