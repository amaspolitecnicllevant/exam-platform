package com.examplatform.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Resultats d'un mòdul agrupats per unitat de treball i per competència, amb la mitjana de tots els alumnes que han
 * entregat. Els punts són els reals (sobre el màxim); {@code sobreDeu} és el mateix reescalat a 10.
 */
public record ResultatsModulDto(
        UUID modulId,
        /** Exàmens del mòdul que algú ha entregat i que tenen alguna unitat assignada o competència. */
        int examens,
        /** Respostes encara sense nota: les mitjanes són provisionals (compten 0). */
        int respostesPendents,
        List<Unitat> unitats,
        List<Competencia> competencies
) {
    /** Punts reals mitjans sobre un màxim, i l'equivalent sobre 10. */
    public record Nota(BigDecimal mitjanaPunts, BigDecimal maxim, BigDecimal sobreDeu) {}

    public record Examen(UUID examId, String titol, int entregats, Nota nota) {}

    /** Una competència dins d'una unitat: la seva nota agrupant els exàmens de la unitat. */
    public record CompetenciaDeUnitat(String codi, Nota nota) {}

    public record Unitat(String codi, String nom,
                         /** Nota de la unitat agrupant tots els seus exàmens; null si cap no s'ha entregat. */
                         Nota nota,
                         List<Examen> examens,
                         List<CompetenciaDeUnitat> competencies) {}

    /** Un examen d'una unitat, per a una competència: rendiment i pes dins la nota d'aquella competència a la unitat. */
    public record ExamenDeCompetencia(UUID examId, String titol, int entregats, Nota nota,
                                      /** % dels punts de la competència que han tret en aquest examen (0–100). */
                                      BigDecimal percentRendiment,
                                      /** % que representen els punts d'aquest examen sobre els de la competència a la unitat (0–100). */
                                      BigDecimal percentPes) {}

    public record UnitatDeCompetencia(String codi, String nom, List<ExamenDeCompetencia> examens) {}

    public record Competencia(String codi, String descripcio,
                              /** Nota de la competència agrupant tots els exàmens del mòdul que la porten. */
                              Nota nota,
                              /** Desglossament per unitat i, dins de cada una, per examen; codi null = exàmens sense unitat. */
                              List<UnitatDeCompetencia> perUnitat) {}
}
