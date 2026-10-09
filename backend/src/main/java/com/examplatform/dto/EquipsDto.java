package com.examplatform.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Estat dels ordinadors d'una aula. */
public record EquipsDto(
        Referencia referencia,
        int preparats,
        int alterats,
        int senseNoticies,
        int altres,
        /** Ordinadors que fa molts dies que no s'encenen (no han enviat cap informe): cal demanar que s'encenguin. */
        int faTemps,
        List<Equip> equips
) {
    /** Estat d'un ordinador. L'ordre és de més a menys greu. */
    public enum Estat { ALTERAT, SENSE_PLATAFORMA, SENSE_NOTICIES, SENSE_REFERENCIA, PREPARAT }

    public record Referencia(LocalDateTime fixadaEl, String origenNom) {}

    public record Equip(
            UUID id,
            String nom,
            String ip,
            LocalDateTime darrerInforme,
            Estat estat,
            /** Fa més dies dels llindar que no envia cap informe (el més probable és que no s'hagi encès). */
            boolean faTemps,
            long diesSenseInformar,
            Boolean arribaPlataforma,
            Boolean arribaIsard,
            String navegador,
            Integer discLliureMb,
            LocalDateTime arrencada,
            Integer usuarisDins,
            /** Avisos que no impedeixen l'examen (Isard no respon, poc disc…). */
            List<String> avisos,
            /** Si l'estat és ALTERAT: què és diferent de la referència ('+' és d'aquest ordinador, '−' és de la referència). */
            List<String> diferencies,
            /** Hi ha una restauració demanada (i no ha caducat) que l'ordinador encara no ha fet. */
            boolean restauracioPendent,
            LocalDateTime restauracioDemanadaEl,
            /** «OK» o el motiu de l'error de l'última restauració informada per l'ordinador. */
            String restauracioResultat,
            LocalDateTime restauracioResultatEl
    ) {}
}
