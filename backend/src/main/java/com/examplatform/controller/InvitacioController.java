package com.examplatform.controller;

import com.examplatform.domain.model.User;
import com.examplatform.domain.service.InvitacioService;
import com.examplatform.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/invitacions")
@RequiredArgsConstructor
public class InvitacioController {

    private final InvitacioService invitacioService;
    private final com.examplatform.config.LoginRateLimiter loginRateLimiter;

    // ── Endpoints autenticats (professor/admin) ───────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitacioDto create(@Valid @RequestBody InvitacioDto.CreateRequest req,
                               @AuthenticationPrincipal User user) {
        return invitacioService.create(req.modulId(), req.curs(), req.maxUses(), req.grupId(), user);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public List<InvitacioDto> list(@RequestParam(required = false) UUID modulId,
                                   @RequestParam(required = false) String curs,
                                   @AuthenticationPrincipal User user) {
        if (modulId != null && curs != null)
            return invitacioService.findByModulAndCurs(modulId, curs, user);
        return invitacioService.findMeves(user);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        invitacioService.deactivate(id, user);
    }

    // ── Endpoints públics (sense autenticació) ────────────────────────────────

    @GetMapping("/publica/{token}")
    public InvitacioPublicaDto getPublica(@PathVariable UUID token) {
        return invitacioService.getPublica(token);
    }

    @PostMapping("/publica/{token}/acceptar")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse acceptar(@PathVariable UUID token,
                                  @Valid @RequestBody AcceptarConvitRequest req,
                                  jakarta.servlet.http.HttpServletRequest request) {
        String ip = com.examplatform.util.IpUtil.clientIp(request);
        loginRateLimiter.assertPermes(ip, req.email());
        try {
            return invitacioService.acceptar(token, req);
        } catch (IllegalArgumentException e) {
            // p. ex. contrasenya incorrecta d'un compte existent
            loginRateLimiter.registraFallada(ip, req.email());
            throw e;
        }
    }

    // ── Endpoint per a usuari ja autenticat que fa clic al link ──────────────

    @PostMapping("/publica/{token}/unir-se")
    @PreAuthorize("hasRole('STUDENT')")
    public InvitacioPublicaDto unirSe(@PathVariable UUID token,
                                      @AuthenticationPrincipal User user) {
        return invitacioService.acceptarAutenticat(token, user);
    }
}
