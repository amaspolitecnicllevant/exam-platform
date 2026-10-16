package com.examplatform.domain.service.exportacio;

import com.examplatform.domain.model.Answer;
import com.examplatform.domain.model.Question;
import com.examplatform.domain.model.QuestionType;
import com.examplatform.domain.service.Numeracio;
import com.examplatform.domain.service.Puntuacio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Informe de dificultat d'un examen segons els resultats, amb possibles millores per a la propera vegada i
 * els alumnes que potser no haurien d'haver aprovat. Tot es calcula amb regles fixes (sense IA): els llindars
 * són constants d'aquesta classe i es llisten al final de l'informe perquè es puguin discutir.
 */
public final class InformeDificultat {

    // ── Llindars (es mostren a l'informe, apartat «Criteris») ─────────────────
    static final BigDecimal APROVAT = new BigDecimal("5");
    /** Nota mitjana: a partir d'aquí l'examen és «molt fàcil», «fàcil»… */
    static final BigDecimal MOLT_FACIL = new BigDecimal("8");
    static final BigDecimal FACIL = new BigDecimal("7");
    static final BigDecimal DIFICIL = new BigDecimal("5.5");
    static final BigDecimal MOLT_DIFICIL = new BigDecimal("4");
    /** Rendiment d'una pregunta (% dels punts) a partir del qual és massa fàcil / per sota del qual és massa difícil. */
    static final BigDecimal PREGUNTA_FACIL = new BigDecimal("90");
    static final BigDecimal PREGUNTA_DIFICIL = new BigDecimal("30");
    /** Diferència de rendiment entre el 27 % millor i el 27 % pitjor per sota de la qual la pregunta distingeix poc. */
    static final BigDecimal DISCRIMINACIO_BAIXA = new BigDecimal("0.20");
    /** Alumnes entregats mínims per calcular la discriminació, i per no avisar que les dades són poques. */
    static final int MINIM_DISCRIMINACIO = 8;
    static final int MINIM_FIABLE = 15;
    /** Marge «just aprovat»: de 5 a 5,5. */
    static final BigDecimal MARGE_APROVAT = new BigDecimal("0.5");
    /** Percentatge d'alumnes que deixen una pregunta en blanc a partir del qual s'avisa. */
    static final BigDecimal BLANC_ALT = new BigDecimal("30");
    /** Pes del test (en % dels punts) a partir del qual sense penalització s'avisa. */
    static final BigDecimal PES_TEST_ALT = new BigDecimal("40");
    /** Si la resta de l'examen (sense test) queda per sota d'això, l'aprovat depèn del test. */
    static final BigDecimal RESTA_BAIXA = new BigDecimal("4");

    private static final BigDecimal CENT = new BigDecimal("100");

    private InformeDificultat() {}

    /** Nota sobre 10 d'un alumne entregat. */
    record Nota(DadesExamen.Alumne alumne, BigDecimal valor) {}

    /** Una pregunta amb el seu rendiment i la seva discriminació. */
    record Dades(Question q, BigDecimal rendiment, BigDecimal discriminacio, BigDecimal percentBlanc,
                 BigDecimal percentCorrectes, BigDecimal percentMillorOpcioIncorrecta) {}

    public static String informe(DadesExamen d, LocalDate data) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Informe de dificultat: ").append(unaLinia(d.exam().getTitle())).append("\n\n");
        sb.append("Generat el ").append(data).append(". Aquest informe conté noms d'alumnes: no el comparteixis fora del professorat.\n\n");

        List<DadesExamen.Alumne> entregats = d.entregats();
        if (entregats.isEmpty()) {
            return sb.append("_Cap alumne ha entregat l'examen: encara no hi ha resultats per valorar._\n").toString();
        }
        if (entregats.size() < MINIM_FIABLE) {
            sb.append("> **Poques dades:** només hi ha ").append(entregats.size())
                    .append(" alumnes entregats. Les conclusions són orientatives.\n\n");
        }

        List<Nota> notes = new ArrayList<>();
        for (DadesExamen.Alumne a : entregats) {
            BigDecimal nota = Puntuacio.notaSobreDeu(d.preguntes(), a.respostes());
            if (nota != null) notes.add(new Nota(a, nota));
        }
        if (notes.isEmpty()) {
            return sb.append("_L'examen no té punts: no es poden calcular notes._\n").toString();
        }
        List<Dades> preguntes = analitzaPreguntes(d, entregats, notes);

        resum(sb, d, notes, preguntes);
        perTipus(sb, d, entregats);
        List<String> millores = new ArrayList<>();
        preguntesPerRevisar(sb, preguntes, entregats.size(), millores);
        competencies(sb, d, entregats, millores);
        List<Aprovat> dubtosos = aprovatsDubtosos(d, notes);
        alumnesDubtosos(sb, dubtosos, notes.size());
        millores(sb, d, notes, preguntes, dubtosos, millores);
        criteris(sb);
        return sb.toString();
    }

    // ── Resum global ──────────────────────────────────────────────────────────

    static String nivell(BigDecimal mitjana) {
        if (mitjana.compareTo(MOLT_FACIL) >= 0) return "Molt fàcil";
        if (mitjana.compareTo(FACIL) >= 0) return "Fàcil";
        if (mitjana.compareTo(DIFICIL) >= 0) return "Equilibrat";
        if (mitjana.compareTo(MOLT_DIFICIL) >= 0) return "Difícil";
        return "Molt difícil";
    }

    private static void resum(StringBuilder sb, DadesExamen d, List<Nota> notes, List<Dades> preguntes) {
        List<BigDecimal> valors = notes.stream().map(Nota::valor).toList();
        BigDecimal mitjana = mitjana(valors);
        long aprovats = valors.stream().filter(n -> n.compareTo(APROVAT) >= 0).count();
        BigDecimal pctAprovats = percent(aprovats, valors.size());
        long excel = valors.stream().filter(n -> n.compareTo(new BigDecimal("9")) >= 0).count();
        long molt = valors.stream().filter(n -> n.compareTo(new BigDecimal("3")) < 0).count();

        sb.append("## Valoració global\n\n");
        sb.append("**Dificultat de l'examen: ").append(nivell(mitjana)).append("** (nota mitjana ")
                .append(CsvSegur.num(mitjana)).append(", ").append(pct(pctAprovats)).append(" d'aprovats).\n\n");
        sb.append("| | |\n|:--|--:|\n");
        sb.append("| Alumnes entregats | ").append(notes.size()).append(" |\n");
        sb.append("| Nota mitjana (sobre 10) | ").append(CsvSegur.num(mitjana)).append(" |\n");
        sb.append("| Mediana | ").append(CsvSegur.num(mediana(valors))).append(" |\n");
        sb.append("| Aprovats (≥ 5) | ").append(aprovats).append(" (").append(pct(pctAprovats)).append(") |\n");
        sb.append("| Notes de 9 o més | ").append(excel).append(" (").append(pct(percent(excel, valors.size()))).append(") |\n");
        sb.append("| Notes inferiors a 3 | ").append(molt).append(" (").append(pct(percent(molt, valors.size()))).append(") |\n\n");

        long facils = preguntes.stream().filter(p -> !p.q().isAnulada() && p.rendiment() != null
                && p.rendiment().compareTo(PREGUNTA_FACIL) >= 0).count();
        long dificils = preguntes.stream().filter(p -> !p.q().isAnulada() && p.rendiment() != null
                && p.rendiment().compareTo(PREGUNTA_DIFICIL) < 0).count();
        long valorades = preguntes.stream().filter(p -> !p.q().isAnulada() && p.rendiment() != null).count();
        if (valorades > 0) {
            sb.append("Preguntes valorades: ").append(valorades).append(" · massa fàcils (rendiment ≥ ")
                    .append(PREGUNTA_FACIL.toPlainString()).append(" %): ").append(facils).append(" · massa difícils (< ")
                    .append(PREGUNTA_DIFICIL.toPlainString()).append(" %): ").append(dificils).append(".\n\n");
        }
    }

    // ── Per tipus de pregunta ─────────────────────────────────────────────────

    private static void perTipus(StringBuilder sb, DadesExamen d, List<DadesExamen.Alumne> entregats) {
        Map<QuestionType, List<Question>> perTipus = new TreeMap<>();
        for (Question q : d.preguntes()) {
            if (!q.isAnulada()) perTipus.computeIfAbsent(q.getTipus(), k -> new ArrayList<>()).add(q);
        }
        if (perTipus.size() < 2) return;
        sb.append("## Dificultat per tipus de pregunta\n\n| Tipus | Preguntes | Punts | Rendiment mitjà |\n|:--|--:|--:|--:|\n");
        perTipus.forEach((tipus, qs) -> {
            BigDecimal punts = Puntuacio.maxim(qs);
            BigDecimal obtinguts = BigDecimal.ZERO;
            for (DadesExamen.Alumne a : entregats) obtinguts = obtinguts.add(Puntuacio.total(qs, a.respostes()));
            BigDecimal rendiment = punts.signum() == 0 ? null
                    : obtinguts.multiply(CENT).divide(punts.multiply(BigDecimal.valueOf(entregats.size())), 1, RoundingMode.HALF_UP);
            sb.append("| ").append(ExportacioMarkdown.etiquetaTipus(tipus)).append(" | ").append(qs.size()).append(" | ")
                    .append(CsvSegur.curt(punts)).append(" | ").append(rendiment == null ? "—" : pct(rendiment)).append(" |\n");
        });
        sb.append("\n");
    }

    // ── Preguntes ─────────────────────────────────────────────────────────────

    static List<Dades> analitzaPreguntes(DadesExamen d, List<DadesExamen.Alumne> entregats, List<Nota> notes) {
        // Discriminació: rendiment del 27 % amb millor nota menys el del 27 % amb pitjor nota
        List<DadesExamen.Alumne> ordenats = notes.stream()
                .sorted(Comparator.comparing(Nota::valor).reversed())
                .map(Nota::alumne).toList();
        int k = ordenats.size() < MINIM_DISCRIMINACIO ? 0 : Math.max(2, (int) Math.round(ordenats.size() * 0.27));
        List<DadesExamen.Alumne> dalt = k == 0 ? List.of() : ordenats.subList(0, k);
        List<DadesExamen.Alumne> baix = k == 0 ? List.of() : ordenats.subList(ordenats.size() - k, ordenats.size());

        List<Dades> res = new ArrayList<>();
        for (Question q : d.preguntes()) {
            List<BigDecimal> punts = new ArrayList<>();
            int blanc = 0, correctes = 0;
            Map<String, Integer> opcions = new LinkedHashMap<>();
            for (DadesExamen.Alumne a : entregats) {
                Answer r = a.respostes().get(q.getId());
                boolean contestada = r != null && r.getContingut() != null && !r.getContingut().isBlank();
                if (!contestada && q.getTipus() != QuestionType.FILE_UPLOAD) blanc++;
                if (q.getTipus() == QuestionType.FILE_UPLOAD && (r == null || !r.teFitxer())) blanc++;
                BigDecimal p = Puntuacio.punts(q, r);
                if (p != null) punts.add(p);
                if (q.getTipus() == QuestionType.CHOICE && contestada) {
                    String lletra = r.getContingut().strip().toLowerCase(Locale.ROOT);
                    opcions.merge(lletra, 1, Integer::sum);
                    if (lletra.equals(q.getCorrectChoice() == null ? "" : q.getCorrectChoice().strip().toLowerCase(Locale.ROOT))) correctes++;
                }
            }
            BigDecimal rendiment = rendiment(q, punts);
            BigDecimal disc = null;
            if (k > 0 && !q.isAnulada() && q.getPunts().signum() > 0) {
                BigDecimal rDalt = rendimentGrup(q, dalt), rBaix = rendimentGrup(q, baix);
                if (rDalt != null && rBaix != null) disc = rDalt.subtract(rBaix).divide(CENT, 2, RoundingMode.HALF_UP);
            }
            BigDecimal pctCorrectes = q.getTipus() == QuestionType.CHOICE ? percent(correctes, entregats.size()) : null;
            BigDecimal millorIncorrecta = null;
            if (q.getTipus() == QuestionType.CHOICE) {
                String correcta = q.getCorrectChoice() == null ? "" : q.getCorrectChoice().strip().toLowerCase(Locale.ROOT);
                millorIncorrecta = opcions.entrySet().stream().filter(e -> !e.getKey().equals(correcta))
                        .map(e -> percent(e.getValue(), entregats.size())).max(Comparator.naturalOrder()).orElse(null);
            }
            res.add(new Dades(q, rendiment, disc, percent(blanc, entregats.size()), pctCorrectes, millorIncorrecta));
        }
        return res;
    }

    private static BigDecimal rendiment(Question q, List<BigDecimal> punts) {
        if (punts.isEmpty() || q.getPunts().signum() == 0) return null;
        return mitjana(punts).multiply(CENT).divide(q.getPunts(), 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal rendimentGrup(Question q, List<DadesExamen.Alumne> grup) {
        List<BigDecimal> punts = new ArrayList<>();
        for (DadesExamen.Alumne a : grup) {
            BigDecimal p = Puntuacio.punts(q, a.respostes().get(q.getId()));
            if (p != null) punts.add(p);
        }
        return rendiment(q, punts);
    }

    private static void preguntesPerRevisar(StringBuilder sb, List<Dades> preguntes, int entregats, List<String> millores) {
        sb.append("## Preguntes per revisar\n\n");
        List<String[]> files = new ArrayList<>();
        List<String> massaFacils = new ArrayList<>(), massaDificils = new ArrayList<>(), pocDiscriminants = new ArrayList<>(),
                clauSospitosa = new ArrayList<>(), enBlanc = new ArrayList<>();
        for (Dades p : preguntes) {
            if (p.q().isAnulada()) continue;
            String num = "P" + Numeracio.numero(p.q());
            List<String> motius = new ArrayList<>();
            if (p.rendiment() != null && p.rendiment().compareTo(PREGUNTA_FACIL) >= 0) { motius.add("massa fàcil"); massaFacils.add(num); }
            if (p.rendiment() != null && p.rendiment().compareTo(PREGUNTA_DIFICIL) < 0) { motius.add("massa difícil"); massaDificils.add(num); }
            if (p.discriminacio() != null && p.discriminacio().compareTo(BigDecimal.ZERO) < 0) {
                motius.add("la responen millor els alumnes amb pitjor nota (revisa l'enunciat o la clau)"); pocDiscriminants.add(num);
            } else if (p.discriminacio() != null && p.discriminacio().compareTo(DISCRIMINACIO_BAIXA) < 0
                    && !(p.rendiment() != null && p.rendiment().compareTo(PREGUNTA_FACIL) >= 0)) {
                motius.add("distingeix poc entre alumnes bons i dolents"); pocDiscriminants.add(num);
            }
            if (p.q().getTipus() == QuestionType.CHOICE && p.percentMillorOpcioIncorrecta() != null && p.percentCorrectes() != null
                    && p.percentMillorOpcioIncorrecta().compareTo(p.percentCorrectes()) > 0) {
                motius.add("una opció incorrecta s'ha triat més que la correcta (la clau pot ser errònia)"); clauSospitosa.add(num);
            }
            if (p.percentBlanc() != null && p.percentBlanc().compareTo(BLANC_ALT) >= 0) {
                motius.add(pct(p.percentBlanc()) + " en blanc"); enBlanc.add(num);
            }
            if (!motius.isEmpty()) {
                files.add(new String[]{num, cela(abreuja(p.q().getEnunciat(), 70)), p.rendiment() == null ? "—" : pct(p.rendiment()),
                        p.discriminacio() == null ? "—" : CsvSegur.num(p.discriminacio()), String.join("; ", motius)});
            }
        }
        if (files.isEmpty()) {
            sb.append("Cap pregunta destaca per ser massa fàcil, massa difícil o per distingir poc.\n\n");
        } else {
            sb.append("| P | Enunciat | Rendiment | Discriminació | Motiu |\n|:--|:--|--:|--:|:--|\n");
            files.forEach(f -> sb.append("| ").append(String.join(" | ", f)).append(" |\n"));
            sb.append("\n_Discriminació_: diferència de rendiment entre el 27 % d'alumnes amb millor nota i el 27 % amb pitjor (de −1 a 1; "
                    + "com més alta, millor distingeix). Només es calcula amb ").append(MINIM_DISCRIMINACIO)
                    .append(" o més alumnes entregats.\n\n");
        }
        if (!clauSospitosa.isEmpty()) millores.add("Revisa la clau de correcció de " + String.join(", ", clauSospitosa)
                + ": els alumnes han triat més una opció incorrecta que la correcta.");
        if (!massaFacils.isEmpty()) millores.add("Fes més exigents " + String.join(", ", massaFacils)
                + " (rendiment molt alt): o canvia'n el nivell, o redueix-ne el pes si només comproven memòria.");
        if (!massaDificils.isEmpty()) millores.add("Revisa " + String.join(", ", massaDificils)
                + " (rendiment molt baix): comprova que l'enunciat és clar i que es treballa a classe a aquest nivell.");
        if (!pocDiscriminants.isEmpty()) millores.add("Les preguntes " + String.join(", ", pocDiscriminants)
                + " distingeixen poc els alumnes que dominen la matèria dels que no: substitueix-les per altres que ho facin millor.");
        if (!enBlanc.isEmpty()) millores.add("Moltes respostes en blanc a " + String.join(", ", enBlanc)
                + ": pot ser un tema no treballat, un enunciat poc clar o falta de temps; si són les últimes, revisa la durada de l'examen.");
    }

    // ── Competències ──────────────────────────────────────────────────────────

    private static void competencies(StringBuilder sb, DadesExamen d, List<DadesExamen.Alumne> entregats, List<String> millores) {
        List<String> cos = ExportacioNotes.competencies(d).stream()
                .filter(c -> !c.equals(ExportacioNotes.SENSE_COMPETENCIA)).toList();
        if (cos.isEmpty()) return;
        sb.append("## Competències\n\n| Competència | Preguntes | Nota mitjana (sobre 10) | Aproven |\n|:--|--:|--:|--:|\n");
        List<String> baixes = new ArrayList<>(), sensePreguntes = new ArrayList<>();
        for (String co : cos) {
            long qs = d.preguntes().stream().filter(q -> co.equals(q.getCompetencia() == null ? null : q.getCompetencia().strip())).count();
            List<BigDecimal> notes = entregats.stream().map(a -> ExportacioNotes.notaCompetencia(d, a, co))
                    .filter(Objects::nonNull).toList();
            BigDecimal mitjana = notes.isEmpty() ? null : mitjana(notes);
            BigDecimal aproven = notes.isEmpty() ? null : percent(notes.stream().filter(n -> n.compareTo(APROVAT) >= 0).count(), notes.size());
            sb.append("| ").append(cela(co)).append(" | ").append(qs).append(" | ").append(mitjana == null ? "—" : CsvSegur.num(mitjana))
                    .append(" | ").append(aproven == null ? "—" : pct(aproven)).append(" |\n");
            if (qs == 0) sensePreguntes.add(co);
            else if (mitjana != null && mitjana.compareTo(APROVAT) < 0) baixes.add(co);
        }
        sb.append("\n");
        if (!baixes.isEmpty()) millores.add("La mitjana de " + String.join(", ", baixes)
                + " no arriba al 5: reforça aquestes competències a classe o revisa si les preguntes que les avaluen són raonables.");
        if (!sensePreguntes.isEmpty()) millores.add("L'examen declara " + String.join(", ", sensePreguntes)
                + " però cap pregunta no l'avalua: afegeix-hi preguntes o treu-la de les competències de l'examen.");
    }

    // ── Alumnes que potser no haurien d'aprovar ───────────────────────────────

    record Aprovat(DadesExamen.Alumne alumne, BigDecimal nota, List<String> motius) {}

    static List<Aprovat> aprovatsDubtosos(DadesExamen d, List<Nota> notes) {
        List<Question> senseBonus = d.preguntes().stream().filter(q -> !q.isAnulada()).toList();
        List<Question> senseTest = d.preguntes().stream()
                .filter(q -> q.getTipus() != QuestionType.CHOICE && !q.isAnulada()).toList();
        boolean testIPenalitza = d.exam().getPenalitzacioChoice() != null && d.exam().getPenalitzacioChoice().signum() > 0;
        long bonus = d.preguntes().stream().filter(Question::isAnulada).count();
        List<String> cos = ExportacioNotes.competencies(d).stream()
                .filter(c -> !c.equals(ExportacioNotes.SENSE_COMPETENCIA)).toList();

        List<Aprovat> res = new ArrayList<>();
        for (Nota n0 : notes) {
            DadesExamen.Alumne a = n0.alumne();
            BigDecimal nota = n0.valor();
            if (nota.compareTo(APROVAT) < 0) continue;
            List<String> motius = new ArrayList<>();
            if (nota.compareTo(APROVAT.add(MARGE_APROVAT)) < 0) {
                motius.add("just aprovat (entre 5 i " + CsvSegur.num(APROVAT.add(MARGE_APROVAT)) + ")");
            }
            if (bonus > 0 && !senseBonus.isEmpty()) {
                BigDecimal sense = Puntuacio.notaSobreDeu(senseBonus, a.respostes());
                if (sense != null && sense.compareTo(APROVAT) < 0) {
                    motius.add("sense les " + bonus + " preguntes de bonus la nota seria " + CsvSegur.num(sense));
                }
            }
            if (!testIPenalitza && !senseTest.isEmpty() && senseTest.size() < senseBonus.size()) {
                BigDecimal resta = Puntuacio.notaSobreDeu(senseTest, a.respostes());
                if (resta != null && resta.compareTo(RESTA_BAIXA) < 0) {
                    motius.add("aprova gràcies al test (sense penalització): la resta de l'examen només té " + CsvSegur.num(resta));
                }
            }
            List<String> suspeses = new ArrayList<>();
            for (String co : cos) {
                BigDecimal n = ExportacioNotes.notaCompetencia(d, a, co);
                if (n != null && n.compareTo(APROVAT) < 0) suspeses.add(co + " (" + CsvSegur.num(n) + ")");
            }
            if (!suspeses.isEmpty()) motius.add("competències suspeses: " + String.join(", ", suspeses));
            int pendents = ExportacioNotes.pendents(d, a);
            if (pendents > 0) motius.add("té " + pendents + " respostes sense qualificar: la nota pot canviar");
            if (!motius.isEmpty()) res.add(new Aprovat(a, nota, motius));
        }
        res.sort(Comparator.comparing(Aprovat::nota).thenComparing(x -> x.alumne().nom(), String.CASE_INSENSITIVE_ORDER));
        return res;
    }

    private static void alumnesDubtosos(StringBuilder sb, List<Aprovat> dubtosos, int total) {
        sb.append("## Alumnes aprovats que potser s'han de revisar\n\n");
        if (dubtosos.isEmpty()) {
            sb.append("Cap aprovat té indicis clars de no haver arribat al nivell: cap és just aprovat, ni depèn del bonus o del test, "
                    + "ni té competències suspeses.\n\n");
            return;
        }
        sb.append(dubtosos.size()).append(" de ").append(total).append(" alumnes tenen algun indici (no vol dir que hagin de suspendre; "
                + "és una llista per revisar):\n\n| Alumne | Nota | Indicis |\n|:--|--:|:--|\n");
        for (Aprovat a : dubtosos) {
            sb.append("| ").append(cela(a.alumne().nom())).append(" | ").append(CsvSegur.num(a.nota()))
                    .append(" | ").append(cela(String.join("; ", a.motius()))).append(" |\n");
        }
        sb.append("\n");
    }

    // ── Millores ──────────────────────────────────────────────────────────────

    private static void millores(StringBuilder sb, DadesExamen d, List<Nota> notes, List<Dades> preguntes,
                                 List<Aprovat> dubtosos, List<String> deLesPreguntes) {
        List<String> m = new ArrayList<>();
        BigDecimal mitjana = mitjana(notes.stream().map(Nota::valor).toList());
        BigDecimal pctAprovats = percent(notes.stream().filter(n -> n.valor().compareTo(APROVAT) >= 0).count(), notes.size());
        if (mitjana.compareTo(MOLT_FACIL) >= 0) {
            m.add("L'examen ha estat molt fàcil (mitjana " + CsvSegur.num(mitjana) + "): no separa bé els alumnes. Afegeix preguntes d'un nivell més alt "
                    + "o d'aplicació, i redueix el pes de les preguntes de memòria.");
        } else if (mitjana.compareTo(FACIL) >= 0) {
            m.add("L'examen ha estat fàcil (mitjana " + CsvSegur.num(mitjana) + "): considera afegir-hi alguna pregunta més exigent.");
        } else if (mitjana.compareTo(MOLT_DIFICIL) < 0) {
            m.add("L'examen ha estat molt difícil (mitjana " + CsvSegur.num(mitjana) + "): revisa que el contingut s'hagi treballat prou "
                    + "i que el nombre de preguntes càpiga en el temps.");
        } else if (mitjana.compareTo(APROVAT) < 0) {
            m.add("La mitjana no arriba al 5 (" + CsvSegur.num(mitjana) + "): valora si és un problema de l'examen o de la preparació.");
        }
        BigDecimal pesTest = pesTest(d);
        boolean penalitza = d.exam().getPenalitzacioChoice() != null && d.exam().getPenalitzacioChoice().signum() > 0;
        if (!penalitza && pesTest.compareTo(PES_TEST_ALT) >= 0) {
            m.add("El test suma el " + pct(pesTest) + " de la nota i les errades no penalitzen: l'atzar aporta punts. "
                    + "Activa la penalització (p. ex. 1/3), redueix el pes del test o afegeix-hi més opcions.");
        }
        if (pctAprovats.compareTo(new BigDecimal("95")) >= 0 && notes.size() >= MINIM_FIABLE) {
            m.add("Aproven el " + pct(pctAprovats) + ": mira la llista d'aprovats a revisar i considera pujar l'exigència de l'aprovat.");
        }
        if (!dubtosos.isEmpty()) {
            m.add("Revisa els " + dubtosos.size() + " aprovats amb indicis (apartat anterior) abans de tancar les notes.");
        }
        m.addAll(deLesPreguntes);
        long anulades = d.preguntes().stream().filter(Question::isAnulada).count();
        if (anulades > 0) {
            m.add("Hi ha " + anulades + " preguntes de bonus (anul·lades): regalen punts a tothom; pensa a corregir-les abans de l'examen perquè no el facin més fàcil.");
        }

        sb.append("## Possibles millores per a la propera vegada\n\n");
        if (m.isEmpty()) sb.append("L'examen sembla ben calibrat: no hi ha cap senyal clar de millora.\n\n");
        else m.forEach(x -> sb.append("- ").append(x).append("\n"));
        sb.append("\n");
    }

    private static BigDecimal pesTest(DadesExamen d) {
        BigDecimal total = Puntuacio.maxim(d.preguntes());
        if (total.signum() == 0) return BigDecimal.ZERO;
        BigDecimal test = d.preguntes().stream().filter(q -> q.getTipus() == QuestionType.CHOICE)
                .map(Question::getPunts).reduce(BigDecimal.ZERO, BigDecimal::add);
        return test.multiply(CENT).divide(total, 1, RoundingMode.HALF_UP);
    }

    private static void criteris(StringBuilder sb) {
        sb.append("## Criteris d'aquest informe\n\n");
        sb.append("Càlcul automàtic amb regles fixes, sense IA. Són orientatius.\n\n");
        sb.append("- Dificultat global segons la nota mitjana: molt fàcil ≥ ").append(MOLT_FACIL.toPlainString()).append(", fàcil ≥ ")
                .append(FACIL.toPlainString()).append(", equilibrat ≥ ").append(DIFICIL.toPlainString().replace('.', ',')).append(", difícil ≥ ")
                .append(MOLT_DIFICIL.toPlainString()).append(", molt difícil per sota.\n");
        sb.append("- Pregunta massa fàcil: rendiment ≥ ").append(PREGUNTA_FACIL.toPlainString()).append(" %; massa difícil: < ")
                .append(PREGUNTA_DIFICIL.toPlainString()).append(" %. Les de bonus no hi compten.\n");
        sb.append("- Poca discriminació: menys de ").append(DISCRIMINACIO_BAIXA.toPlainString().replace('.', ',')).append(" (27 % millor contra 27 % pitjor).\n");
        sb.append("- Aprovat amb indicis: just aprovat (de 5 a ").append(CsvSegur.num(APROVAT.add(MARGE_APROVAT)))
                .append("), nota < 5 sense el bonus, resta de l'examen < ").append(RESTA_BAIXA.toPlainString())
                .append(" amb test sense penalització, competència suspesa o respostes sense qualificar.\n");
    }

    // ── Ajudes ────────────────────────────────────────────────────────────────

    private static BigDecimal mitjana(List<BigDecimal> v) {
        return v.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(v.size()), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal mediana(List<BigDecimal> v) {
        List<BigDecimal> o = v.stream().sorted().toList();
        int n = o.size();
        return n % 2 == 1 ? o.get(n / 2)
                : o.get(n / 2 - 1).add(o.get(n / 2)).divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(long part, long total) {
        return total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(part).multiply(CENT).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    private static String pct(BigDecimal n) {
        return CsvSegur.curt(n) + " %";
    }

    private static String unaLinia(String t) {
        return t == null ? "" : t.replaceAll("[\\r\\n]+", " ").strip();
    }

    private static String abreuja(String t, int max) {
        String l = unaLinia(t);
        return l.length() <= max ? l : l.substring(0, max - 1).stripTrailing() + "…";
    }

    /** Text dins d'una cel·la de taula Markdown. */
    private static String cela(String t) {
        return unaLinia(t).replace("|", "\\|");
    }
}
