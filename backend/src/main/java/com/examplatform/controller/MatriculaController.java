package com.examplatform.controller;

import com.examplatform.domain.service.MatriculaService;
import com.examplatform.dto.MatriculaDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/matricules")
@RequiredArgsConstructor
public class MatriculaController {

    private final MatriculaService matriculaService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public List<MatriculaDto> list(@RequestParam(required = false) UUID alumneId,
                                   @RequestParam(required = false) UUID modulId,
                                   @RequestParam(required = false) String curs) {
        if (alumneId != null) return matriculaService.findByAlumne(alumneId);
        if (modulId != null && curs != null) return matriculaService.findByModulAndCurs(modulId, curs);
        return List.of();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public MatriculaDto enroll(@Valid @RequestBody MatriculaDto.CreateRequest req) {
        return matriculaService.enroll(req.alumneId(), req.modulId(), req.curs());
    }

    @PostMapping("/lot")
    @PreAuthorize("hasRole('ADMIN')")
    public MatriculaDto.LotResultat enrollLot(@Valid @RequestBody MatriculaDto.LotRequest req) {
        return matriculaService.enrollLot(req.alumneIds(), req.modulId(), req.curs().strip());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unenroll(@PathVariable UUID id) {
        matriculaService.unenroll(id);
    }
}
