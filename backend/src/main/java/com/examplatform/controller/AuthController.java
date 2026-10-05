package com.examplatform.controller;

import com.examplatform.config.JwtUtil;
import com.examplatform.domain.model.User;
import com.examplatform.dto.LoginRequest;
import com.examplatform.dto.LoginResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtUtil jwtUtil;
    private final com.examplatform.config.LoginRateLimiter rateLimiter;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                               jakarta.servlet.http.HttpServletRequest request) {
        String ip = com.examplatform.util.IpUtil.clientIp(request);
        rateLimiter.assertPermes(ip, req.email());
        Authentication auth;
        try {
            auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        } catch (org.springframework.security.core.AuthenticationException e) {
            rateLimiter.registraFallada(ip, req.email());
            throw e;
        }

        User user = (User) auth.getPrincipal();
        String token = jwtUtil.generateToken(user);
        return ResponseEntity.ok(
                new LoginResponse(token, user.getId(), user.getName(), user.getEmail(), user.getRole()));
    }

    /**
     * Renova el token d'un usuari que encara el té vàlid: el frontend ho fa quan en queda poc,
     * perquè no caduqui a mig examen. Sense activitat, el token caduca igualment.
     */
    @PostMapping("/refresh")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public LoginResponse refresh(@org.springframework.security.core.annotation.AuthenticationPrincipal User user) {
        return new LoginResponse(jwtUtil.generateToken(user), user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
