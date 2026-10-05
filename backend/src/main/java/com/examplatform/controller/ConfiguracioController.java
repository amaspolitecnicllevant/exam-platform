package com.examplatform.controller;

import com.examplatform.domain.model.ConfiguracioSistema;
import com.examplatform.domain.service.ConfiguracioService;
import com.examplatform.dto.ConfiguracioDto;
import com.examplatform.dto.ConfiguracioUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;

@RestController
@RequestMapping("/api/configuracio")
@RequiredArgsConstructor
public class ConfiguracioController {

    private final ConfiguracioService configuracioService;
    private final com.examplatform.domain.service.CopiesSeguretatService copiesSeguretatService;
    /** Només existeix si hi ha Google configurat (GoogleOAuth2Config). */
    private final org.springframework.beans.factory.ObjectProvider<
            org.springframework.security.oauth2.client.registration.ClientRegistrationRepository> oauthClients;

    private boolean googleActiu() {
        return oauthClients.getIfAvailable() != null;
    }

    @GetMapping
    public ConfiguracioDto get() {
        return ConfiguracioDto.from(configuracioService.get(), googleActiu());
    }

    @GetMapping("/logo")
    public ResponseEntity<byte[]> getLogo() {
        ConfiguracioSistema c = configuracioService.get();
        if (c.getLogoBase64() == null) return ResponseEntity.notFound().build();
        byte[] bytes = Base64.getDecoder().decode(c.getLogoBase64());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(c.getLogoMime()))
                .body(bytes);
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ConfiguracioDto update(@RequestBody ConfiguracioUpdateRequest req) {
        return ConfiguracioDto.from(configuracioService.update(req), googleActiu());
    }

    @GetMapping("/copies-seguretat")
    @PreAuthorize("hasRole('ADMIN')")
    public com.examplatform.dto.CopiesSeguretatDto copiesSeguretat() {
        return copiesSeguretatService.estat();
    }

    @PostMapping("/logo")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> uploadLogo(@RequestParam("file") MultipartFile file) throws IOException {
        configuracioService.uploadLogo(file);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/logo")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteLogo() {
        configuracioService.deleteLogo();
        return ResponseEntity.noContent().build();
    }
}
