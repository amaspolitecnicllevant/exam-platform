package com.examplatform.domain.service;

import com.examplatform.domain.model.AuditLog;
import com.examplatform.domain.model.Role;
import com.examplatform.domain.model.User;
import com.examplatform.dto.AlertaDto;
import com.examplatform.infrastructure.persistence.AuditLogRepository;
import com.examplatform.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Alertes de seguretat per als administradors, calculades a partir de l'auditoria. No envien res (no hi ha correu): es mostren
 * a l'aplicació, i desapareixen soles quan els esdeveniments surten de la finestra de temps de cada regla.
 *
 * <ul>
 *   <li>Atac a un compte: molts inicis de sessió fallits al mateix compte en una hora (més greu si és de personal).</li>
 *   <li>Atac des d'una adreça: molts fallits des d'una mateixa adreça (més greu si prova molts comptes diferents).</li>
 *   <li>Comptes bloquejats pel límit d'intents.</li>
 *   <li>Un administrador ha entrat des d'una adreça nova.</li>
 *   <li>Informes d'ordinadors rebutjats (testimoni incorrecte o adreça fora d'una aula) i restauracions fallides.</li>
 *   <li>Moltes contrasenyes reinicialitzades o sessions tancades en poc temps (compte d'administrador potser compromès).</li>
 *   <li>Ordinadors amb la configuració alterada.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AlertesSeguretatService {

    static final int FALLITS_PER_COMPTE = 5;
    static final int FALLITS_PER_ADRECA = 10;
    static final int COMPTES_PER_ADRECA_GREU = 5;
    static final int CANVIS_DE_CONTRASENYA = 3;

    private static final List<String> ACCIONS = List.of("LOGIN_BLOQUEJAT", "LOGIN_NOVA_IP", "EQUIP_TESTIMONI_INVALID",
            "EQUIP_IP_FORA_D_AULA", "EQUIP_RESTAURACIO_FALLIDA", "PASSWORD_RESET", "SESSIONS_REVOCADES");

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final EquipsService equipsService;

    @Transactional(readOnly = true)
    public List<AlertaDto> alertes(LocalDateTime ara) {
        List<AlertaDto> res = new ArrayList<>();

        // ── Inicis de sessió fallits (última hora)
        List<AuditLog> fallits = auditLogRepository.findByActionInAndCreatedAtAfterOrderByCreatedAtDesc(
                List.of("LOGIN_FALLIT"), ara.minusHours(1));
        fallitsPerCompte(fallits, res);
        fallitsPerAdreca(fallits, res);

        // ── La resta d'esdeveniments (fins a 7 dies; cada regla mira la seva finestra)
        Map<String, List<AuditLog>> perAccio = auditLogRepository
                .findByActionInAndCreatedAtAfterOrderByCreatedAtDesc(ACCIONS, ara.minusDays(7)).stream()
                .collect(Collectors.groupingBy(AuditLog::getAction));

        List<AuditLog> bloquejos = dins(perAccio.get("LOGIN_BLOQUEJAT"), ara.minusHours(24));
        if (!bloquejos.isEmpty()) {
            res.add(alerta("MITJANA", "COMPTES_BLOQUEJATS",
                    bloquejos.size() + (bloquejos.size() == 1 ? " compte bloquejat" : " bloquejos") + " per massa intents (24 h)",
                    "Comptes: " + resum(bloquejos, AuditLog::getResource) + ". Adreces: " + resum(bloquejos, AuditLog::getIpAddress) + ".",
                    bloquejos));
        }

        List<AuditLog> novaIp = dins(perAccio.get("LOGIN_NOVA_IP"), ara.minusDays(7));
        if (!novaIp.isEmpty()) {
            String qui = novaIp.stream().map(e -> nomDe(e.getUserId())).distinct().limit(3).collect(Collectors.joining(", "));
            res.add(alerta("MITJANA", "ADMIN_NOVA_ADRECA", "Un administrador ha entrat des d'una adreça nova (7 dies)",
                    qui + " ha entrat des de " + resum(novaIp, AuditLog::getResource) + ". Si no sou vosaltres, tanqueu les seves sessions i canvieu-li la contrasenya.",
                    novaIp));
        }

        List<AuditLog> testimoni = dins(perAccio.get("EQUIP_TESTIMONI_INVALID"), ara.minusHours(24));
        if (!testimoni.isEmpty()) {
            res.add(alerta("MITJANA", "INFORMES_TESTIMONI", "Informes d'ordinadors amb un testimoni incorrecte (24 h)",
                    "Algú ha enviat informes sense el testimoni correcte, des de " + resum(testimoni, AuditLog::getIpAddress)
                            + ". Pot ser un ordinador mal configurat o algú que ho prova.", testimoni));
        }
        List<AuditLog> foraAula = dins(perAccio.get("EQUIP_IP_FORA_D_AULA"), ara.minusHours(24));
        if (!foraAula.isEmpty()) {
            res.add(alerta("INFO", "INFORMES_FORA_AULA", "Informes d'ordinadors des d'una adreça que no és de cap aula (24 h)",
                    "Adreces: " + resum(foraAula, AuditLog::getIpAddress) + ". Si és un ordinador de l'aula, comprova el CIDR de l'aula.", foraAula));
        }
        List<AuditLog> restauracions = dins(perAccio.get("EQUIP_RESTAURACIO_FALLIDA"), ara.minusHours(24));
        if (!restauracions.isEmpty()) {
            res.add(alerta("MITJANA", "RESTAURACIONS_FALLIDES", "Restauracions d'ordinadors que han fallat (24 h)",
                    resum(restauracions, AuditLog::getResource), restauracions));
        }

        List<AuditLog> canvis = new ArrayList<>(dins(perAccio.get("PASSWORD_RESET"), ara.minusHours(1)));
        canvis.addAll(dins(perAccio.get("SESSIONS_REVOCADES"), ara.minusHours(1)));
        if (canvis.size() >= CANVIS_DE_CONTRASENYA) {
            String qui = canvis.stream().map(e -> nomDe(e.getUserId())).distinct().limit(3).collect(Collectors.joining(", "));
            res.add(alerta("MITJANA", "MOLTS_CANVIS_DE_CONTRASENYA", canvis.size() + " contrasenyes reinicialitzades o sessions tancades en una hora",
                    "Fet per: " + qui + ". Si no ho esperàveu, un compte d'administrador podria estar compromès.", canvis));
        }

        // ── Ordinadors alterats
        List<String> alterats = equipsService.ordinadorsAlterats(ara);
        if (!alterats.isEmpty()) {
            res.add(new AlertaDto("ALTA", "ORDINADORS_ALTERATS",
                    alterats.size() + (alterats.size() == 1 ? " ordinador té" : " ordinadors tenen") + " la configuració d'examen alterada",
                    String.join(", ", alterats.stream().limit(8).toList()) + (alterats.size() > 8 ? "…" : "") + ". Mira Aules → Ordinadors.",
                    alterats.size(), ara));
        }

        res.sort(Comparator.comparingInt((AlertaDto a) -> pes(a.gravetat()))
                .thenComparing(AlertaDto::ultima, Comparator.nullsLast(Comparator.reverseOrder())));
        return res;
    }

    private void fallitsPerCompte(List<AuditLog> fallits, List<AlertaDto> res) {
        Map<String, List<AuditLog>> perCompte = fallits.stream().filter(e -> e.getResource() != null && !e.getResource().isBlank())
                .collect(Collectors.groupingBy(AuditLog::getResource));
        perCompte.forEach((compte, events) -> {
            if (events.size() < FALLITS_PER_COMPTE) return;
            boolean personal = userRepository.findByEmail(compte).map(u -> u.getRole() != Role.STUDENT).orElse(false);
            long adreces = events.stream().map(AuditLog::getIpAddress).filter(Objects::nonNull).distinct().count();
            res.add(alerta(personal ? "ALTA" : "MITJANA", "ATAC_COMPTE",
                    events.size() + " inicis de sessió fallits al compte " + compte + " (1 h)",
                    (personal ? "És un compte del personal. " : "") + "Des de " + adreces + (adreces == 1 ? " adreça: " : " adreces: ")
                            + resum(events, AuditLog::getIpAddress) + ".", events));
        });
    }

    private void fallitsPerAdreca(List<AuditLog> fallits, List<AlertaDto> res) {
        Map<String, List<AuditLog>> perAdreca = fallits.stream().filter(e -> e.getIpAddress() != null)
                .collect(Collectors.groupingBy(AuditLog::getIpAddress));
        perAdreca.forEach((ip, events) -> {
            if (events.size() < FALLITS_PER_ADRECA) return;
            long comptes = events.stream().map(AuditLog::getResource).distinct().count();
            res.add(alerta(comptes >= COMPTES_PER_ADRECA_GREU ? "ALTA" : "MITJANA", "ATAC_ADRECA",
                    events.size() + " inicis de sessió fallits des de " + ip + " (1 h)",
                    "Ha provat " + comptes + (comptes == 1 ? " compte" : " comptes diferents")
                            + (comptes >= COMPTES_PER_ADRECA_GREU ? ": sembla que en prova molts." : "."), events));
        });
    }

    // ── Ajudes ────────────────────────────────────────────────────────────────

    private static AlertaDto alerta(String gravetat, String codi, String titol, String detall, List<AuditLog> events) {
        LocalDateTime ultima = events.stream().map(AuditLog::getCreatedAt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        return new AlertaDto(gravetat, codi, titol, detall, events.size(), ultima);
    }

    private static List<AuditLog> dins(List<AuditLog> events, LocalDateTime desde) {
        if (events == null) return List.of();
        return events.stream().filter(e -> e.getCreatedAt() != null && e.getCreatedAt().isAfter(desde)).toList();
    }

    /** Valors diferents (els 4 primers), per a un detall curt. */
    private static String resum(List<AuditLog> events, java.util.function.Function<AuditLog, String> camp) {
        List<String> valors = events.stream().map(camp).filter(v -> v != null && !v.isBlank()).distinct().toList();
        if (valors.isEmpty()) return "—";
        return String.join(", ", valors.stream().limit(4).toList()) + (valors.size() > 4 ? " i " + (valors.size() - 4) + " més" : "");
    }

    private String nomDe(UUID userId) {
        if (userId == null) return "algú";
        return userRepository.findById(userId).map(User::getName).orElse("un usuari esborrat");
    }

    private static int pes(String gravetat) {
        return switch (gravetat) { case "ALTA" -> 0; case "MITJANA" -> 1; default -> 2; };
    }
}
