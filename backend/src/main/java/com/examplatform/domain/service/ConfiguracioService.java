package com.examplatform.domain.service;

import com.examplatform.domain.model.ConfiguracioSistema;
import com.examplatform.dto.ConfiguracioUpdateRequest;
import com.examplatform.infrastructure.persistence.ConfiguracioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class ConfiguracioService {

    private final ConfiguracioRepository configuracioRepository;

    @Transactional(readOnly = true)
    public ConfiguracioSistema get() {
        return configuracioRepository.findById(1)
                .orElseGet(() -> configuracioRepository.save(new ConfiguracioSistema()));
    }

    @Transactional
    public ConfiguracioSistema update(ConfiguracioUpdateRequest req) {
        ConfiguracioSistema c = get();
        if (req.nomCentre()            != null) c.setNomCentre(req.nomCentre().strip());
        if (req.colorMarca()           != null) c.setColorMarca(req.colorMarca().strip());
        if (req.cursActiu()            != null) c.setCursActiu(req.cursActiu().strip());
        if (req.duradaDefecte()        != null) c.setDuradaDefecte(req.duradaDefecte());
        if (req.penalitzacioDefecte()  != null) c.setPenalitzacioDefecte(req.penalitzacioDefecte());
        if (req.focusLossThreshold()   != null) c.setFocusLossThreshold(req.focusLossThreshold());
        if (req.gracePeriodSeconds()   != null) c.setGracePeriodSeconds(req.gracePeriodSeconds());
        if (req.dominisOauth()         != null) c.setDominisOauth(req.dominisOauth().strip());
        if (req.copiesLlindar()        != null) {
            if (req.copiesLlindar() < 50 || req.copiesLlindar() > 100) {
                throw new IllegalArgumentException("El llindar de còpies ha d'estar entre 50 i 100 %");
            }
            c.setCopiesLlindar(req.copiesLlindar());
        }
        if (req.copiesLlindarApunts()  != null) {
            if (req.copiesLlindarApunts() < 50 || req.copiesLlindarApunts() > 100) {
                throw new IllegalArgumentException("El llindar de còpies per a preguntes amb apunts ha d'estar entre 50 i 100 %");
            }
            c.setCopiesLlindarApunts(req.copiesLlindarApunts());
        }
        if (req.pujadaFitxersActiva()  != null) c.setPujadaFitxersActiva(req.pujadaFitxersActiva());
        return configuracioRepository.save(c);
    }

    @Transactional
    public void uploadLogo(MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("El fitxer és buit");
        String mime = file.getContentType();
        if (mime == null || !mime.startsWith("image/")) {
            throw new IllegalArgumentException("Només s'accepten imatges");
        }
        if (file.getSize() > 500_000) {
            throw new IllegalArgumentException("El logo no pot superar 500 KB");
        }
        ConfiguracioSistema c = get();
        c.setLogoBase64(Base64.getEncoder().encodeToString(file.getBytes()));
        c.setLogoMime(mime);
        configuracioRepository.save(c);
    }

    @Transactional
    public void deleteLogo() {
        ConfiguracioSistema c = get();
        c.setLogoBase64(null);
        c.setLogoMime(null);
        configuracioRepository.save(c);
    }
}
