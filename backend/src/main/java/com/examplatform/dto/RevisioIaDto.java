package com.examplatform.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Previsualització (i resultat) de la importació d'una revisió feta per una IA. */
public record RevisioIaDto(
        /** Les notes ja són visibles per als alumnes: aplicar canvis les modifica davant d'ells. */
        boolean notesPublicades,
        int canvis,
        int iguals,
        int ignorades,
        int errors,
        List<Fila> files,
        /** Nota final sobre 10 de cada alumne afectat, abans i si s'apliquessin tots els canvis. */
        List<Alumne> alumnes,
        List<String> avisos
) {
    public enum Estat { CANVI, IGUAL, IGNORADA, ERROR }

    /** Origen de la nota que té ara la resposta. */
    public enum Origen { REVISADA, PROPOSTA, CAP }

    public record Fila(
            int linia,
            UUID answerId,
            String alumne,
            String codi,
            Integer pregunta,
            String enunciat,
            BigDecimal puntsMax,
            BigDecimal notaActual,
            Origen origenActual,
            BigDecimal notaNova,
            String justificacio,
            Estat estat,
            String motiu,
            /** Substitueix una nota que el professor ja havia revisat (no una proposta automàtica). */
            boolean sobreescriuRevisada
    ) {}

    public record Alumne(String nom, String codi, BigDecimal notaAbans, BigDecimal notaDespres) {}

    /** Una fila que el professor accepta; {@code notaActual} és la que va veure a la previsualització. */
    public record Acceptada(UUID answerId, BigDecimal notaActual) {}

    public record PrevisualitzaRequest(String text) {}

    public record AplicaRequest(String text, List<Acceptada> acceptades) {}

    public record Aplicacio(int aplicades, int saltades, List<String> motius) {}
}
