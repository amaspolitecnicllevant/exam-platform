package com.examplatform.domain.service.exportacio;

import com.examplatform.domain.model.Answer;
import com.examplatform.domain.model.Question;
import com.examplatform.domain.model.QuestionType;
import com.examplatform.domain.service.Numeracio;
import com.examplatform.domain.service.Puntuacio;

import java.math.BigDecimal;
import java.util.*;

/**
 * Informe de qualificació de l'examen per importar a l'aplicació de notes: una secció per alumne que explica d'on surt la
 * nota, pregunta a pregunta. Segueix la sintaxi dels informes de qualificació de pràctiques (títol, «Resum de la classe»,
 * una secció {@code ##} per alumne amb «Resum», «Part teòrica» i «Què pots millorar», separades per {@code ---}, notes
 * amb coma decimal i sense zeros sobrants). Un examen només té la part teòrica, que compta el 100 % de la nota final.
 */
public final class InformeQualificacio {

    /** La columna «Resposta esperada» només s'omple si la nota és inferior a aquest % dels punts de la pregunta. */
    static final BigDecimal LLINDAR_RESPOSTA_ESPERADA = new BigDecimal("0.90");
    /** «Què pots millorar» recull les preguntes amb menys d'aquest % dels punts. */
    static final BigDecimal LLINDAR_MILLORAR = new BigDecimal("0.60");

    private static final int LLARGADA_TEMA = 60;
    private static final int LLARGADA_TEXT = 220;

    private InformeQualificacio() {}

    public static String informe(DadesExamen d) {
        StringBuilder sb = new StringBuilder();
        List<Question> preguntes = d.preguntes();
        BigDecimal maxim = Puntuacio.maxim(preguntes);

        sb.append("# Informes de qualificació — ").append(unaLinia(d.exam().getTitle())).append("\n\n");
        if (d.exam().getModul() != null) {
            sb.append("**Mòdul:** ").append(unaLinia(d.exam().getModul().getNom())).append(" (")
                    .append(unaLinia(d.exam().getModul().getCodi())).append(")\n\n");
        }
        sb.append("Nota final = 100% teoria. Teoria: ").append(preguntes.size())
                .append(preguntes.size() == 1 ? " pregunta" : " preguntes").append(", ").append(CsvSegur.curt(maxim))
                .append(" punts en total.\n\n");

        sb.append("## Resum de la classe\n\n| Alumne | Teoria | Final |\n|---|---:|---:|\n");
        for (DadesExamen.Alumne a : d.alumnes()) {
            String nota = a.entregat() ? nota(a, d) : "No entregada";
            sb.append("| ").append(cela(a.nom())).append(" | ").append(nota).append(" | ")
                    .append(a.entregat() ? nota : "—").append(" |\n");
        }

        for (DadesExamen.Alumne a : d.alumnes()) {
            sb.append("\n---\n\n## ").append(unaLinia(a.nom())).append("\n\n### Resum\n\n");
            sb.append("| Part | Nota (sobre 10) | Pes |\n|---|---:|---:|\n");
            if (!a.entregat()) {
                sb.append("| Part teòrica | No entregada | 100% |\n| **Nota final** | **—** | |\n");
                continue;
            }
            String nota = nota(a, d);
            sb.append("| Part teòrica | ").append(nota).append(" | 100% |\n");
            sb.append("| **Nota final** | **").append(nota).append("** | |\n\n");
            sb.append("*Càlcul: 100% × ").append(nota).append(" = ").append(nota).append("*\n\n");

            sb.append("### Part teòrica\n\n").append(explicacio(d, maxim)).append("\n\n");
            sb.append("| Pregunta | Nota | Per què | Resposta esperada |\n|---|---:|---|---|\n");
            for (Question q : preguntes) {
                Answer r = a.respostes().get(q.getId());
                BigDecimal punts = Puntuacio.punts(q, r);
                sb.append("| ").append(cela(numero(q) + ". " + tema(q))).append(" | ").append(notaPregunta(punts, q))
                        .append(" | ").append(cela(motiu(q, r, punts))).append(" | ")
                        .append(cela(respostaEsperada(q, punts))).append(" |\n");
            }

            sb.append("\n### Què pots millorar\n\n");
            List<String> millorar = new ArrayList<>();
            for (Question q : preguntes) {
                BigDecimal punts = Puntuacio.punts(q, a.respostes().get(q.getId()));
                if (!q.isAnulada() && q.getPunts().signum() > 0
                        && (punts == null ? BigDecimal.ZERO : punts).compareTo(q.getPunts().multiply(LLINDAR_MILLORAR)) < 0) {
                    String esperada = esperada(q);
                    millorar.add("- **Teoria " + numero(q) + ". " + tema(q) + "** (" + notaPregunta(punts, q).replace(" / ", "/") + ")"
                            + (esperada.isEmpty() ? "" : ": " + esperada));
                }
            }
            if (millorar.isEmpty()) {
                sb.append("- Cap pregunta per sota del 60 % dels punts.\n");
            } else {
                millorar.forEach(l -> sb.append(l.replaceAll("[\\r\\n]+", " ")).append("\n"));
            }
        }
        return sb.toString();
    }

    // ── Peces ─────────────────────────────────────────────────────────────────

    /** Frase que explica d'on surt la nota de teoria. */
    private static String explicacio(DadesExamen d, BigDecimal maxim) {
        StringBuilder s = new StringBuilder("La nota és la suma dels punts de cada pregunta entre el total de l'examen (")
                .append(CsvSegur.curt(maxim)).append(" punts), passada a 10.");
        BigDecimal pen = d.exam().getPenalitzacioChoice();
        if (d.preguntes().stream().anyMatch(q -> q.getTipus() == QuestionType.CHOICE)) {
            s.append(pen != null && pen.signum() > 0
                    ? " Les respostes errades del test resten " + CsvSegur.curt(pen.multiply(new BigDecimal("100"))) + " % dels punts de la pregunta; les en blanc no resten."
                    : " Les respostes errades del test no resten; les en blanc tampoc.");
        }
        if (d.preguntes().stream().anyMatch(Question::isAnulada)) {
            s.append(" Les preguntes anul·lades donen els punts a tothom.");
        }
        return s.toString();
    }

    private static String nota(DadesExamen.Alumne a, DadesExamen d) {
        BigDecimal n = Puntuacio.notaSobreDeu(d.preguntes(), a.respostes());
        return n == null ? "—" : CsvSegur.curt(n);
    }

    private static String notaPregunta(BigDecimal punts, Question q) {
        return (punts == null ? "0" : CsvSegur.curt(punts)) + " / " + CsvSegur.curt(q.getPunts());
    }

    private static int numero(Question q) {
        return Numeracio.numero(q);
    }

    /** Tema curt de la pregunta: el principi de l'enunciat, en una línia. */
    private static String tema(Question q) {
        String t = unaLinia(q.getEnunciat()).replaceAll("[`*_#>]", "");
        return t.length() <= LLARGADA_TEMA ? t : t.substring(0, LLARGADA_TEMA - 1).stripTrailing() + "…";
    }

    /** Per què té aquesta nota: el comentari del professor, la justificació de la revisió o els motius de la proposta. */
    static String motiu(Question q, Answer r, BigDecimal punts) {
        if (q.isAnulada()) return "Pregunta anul·lada: tothom té els punts.";
        boolean sensResposta = r == null || (r.getContingut() == null || r.getContingut().isBlank())
                && !(q.getTipus() == QuestionType.FILE_UPLOAD && r != null && r.teFitxer());
        if (sensResposta) return "No contestada.";
        if (punts == null) return "Pendent de revisar: de moment compta 0.";
        List<String> parts = new ArrayList<>();
        if (r.getComentari() != null && !r.getComentari().isBlank()) parts.add(r.getComentari().strip());
        else if (r.getRevisioIaJustificacio() != null && !r.getRevisioIaJustificacio().isBlank()) parts.add(r.getRevisioIaJustificacio().strip());
        else if (r.getAutoFeedback() != null && !r.getAutoFeedback().isBlank()) parts.add(r.getAutoFeedback().strip().replaceAll("\\s*\\n\\s*", "; "));
        if (parts.isEmpty()) {
            if (q.getTipus() == QuestionType.CHOICE) {
                String triada = r.getContingut().strip().toLowerCase(Locale.ROOT);
                String correcta = q.getCorrectChoice() == null ? "" : q.getCorrectChoice().strip().toLowerCase(Locale.ROOT);
                parts.add(triada.equals(correcta) ? "Resposta correcta."
                        : "Ha triat " + triada + "; la correcta és " + correcta + ".");
            } else if (punts.compareTo(q.getPunts()) >= 0) {
                parts.add("Resposta completa.");
            } else {
                parts.add("Nota posada pel professor.");
            }
        }
        return abreuja(String.join(" ", parts), LLARGADA_TEXT);
    }

    /** Resposta esperada de la columna: només si la nota és inferior al 90 % de la pregunta; si no, «—». */
    static String respostaEsperada(Question q, BigDecimal punts) {
        if (q.isAnulada() || q.getPunts().signum() == 0) return "—";
        BigDecimal p = punts == null ? BigDecimal.ZERO : punts;
        if (p.compareTo(q.getPunts().multiply(LLINDAR_RESPOSTA_ESPERADA)) >= 0) return "—";
        String e = esperada(q);
        return e.isEmpty() ? "—" : e;
    }

    /** La resposta de referència de la pregunta, en una línia. */
    static String esperada(Question q) {
        if (q.getTipus() == QuestionType.CHOICE) {
            String correcta = q.getCorrectChoice() == null ? "" : q.getCorrectChoice().strip().toLowerCase(Locale.ROOT);
            if (q.getChoices() == null || correcta.isEmpty()) return correcta;
            return Arrays.stream(q.getChoices().split("\n")).map(String::strip)
                    .filter(c -> c.toLowerCase(Locale.ROOT).startsWith(correcta))
                    .findFirst().map(c -> abreuja(c, LLARGADA_TEXT)).orElse(correcta);
        }
        if (q.getModelResposta() == null || q.getModelResposta().isBlank()) return "";
        return abreuja(q.getModelResposta(), LLARGADA_TEXT);
    }

    // ── Ajudes ────────────────────────────────────────────────────────────────

    private static String unaLinia(String t) {
        return t == null ? "" : t.replaceAll("[\\r\\n]+", " ").strip();
    }

    private static String abreuja(String t, int max) {
        String l = unaLinia(t).replaceAll("\\s{2,}", " ");
        return l.length() <= max ? l : l.substring(0, max - 1).stripTrailing() + "…";
    }

    /** Text dins d'una cel·la de taula Markdown. */
    private static String cela(String t) {
        return unaLinia(t).replace("|", "\\|");
    }
}
