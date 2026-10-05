package com.examplatform.domain.service.copies;

import com.examplatform.domain.model.QuestionType;
import com.examplatform.domain.service.copies.Tokenitzador.Token;

import java.util.*;

/**
 * Detecció de respostes semblants entre alumnes (possibles còpies), després de l'examen.
 *
 * <p>Per a cada pregunta comparable, cada resposta es converteix en fragments de {@value #K}
 * tokens consecutius. Es descompten els fragments de l'enunciat, de la resposta model i els que
 * comparteix una bona part de la classe (és normal que coincideixin). La semblança d'un parell és
 * la proporció de fragments de la resposta més curta que també són a l'altra.
 *
 * <p>Per a les preguntes de test només es compta un senyal global: preguntes en què tots dos han
 * triat la mateixa opció incorrecta.
 *
 * <p>És un informe per al professor: una semblança alta no demostra una còpia.
 */
public final class DeteccioCopies {

    /** Mida dels fragments (tokens consecutius). */
    public static final int K = 3;

    /** Un fragment compartit per almenys aquestes respostes (i el 40% de la classe) és "comú". */
    static final int MIN_RESPOSTES_COMU = 5;

    /** Tipus que es comparen resposta a resposta. BASH_CMD i PS_CMD no: les solucions convergeixen. */
    static final Set<QuestionType> COMPARABLES = EnumSet.of(
            QuestionType.TEXT, QuestionType.SHORT, QuestionType.LONG,
            QuestionType.BASH_SCRIPT, QuestionType.PS_SCRIPT, QuestionType.JAVA_PROG, QuestionType.HTML_CSS);

    private static final Set<QuestionType> TEXT = EnumSet.of(QuestionType.TEXT, QuestionType.SHORT, QuestionType.LONG);

    /**
     * @param llindarPercent       semblança mínima perquè una pregunta es marqui
     * @param llindarApuntsPercent el mateix per a les preguntes amb apunts (més alt: s'hi pot copiar dels apunts)
     */
    public record Parametres(int llindarPercent, int llindarApuntsPercent, int minParaulesText,
                             int minTokensCodi, int minErradesTest) {
        public static Parametres perDefecte(int llindarPercent, int llindarApuntsPercent) {
            return new Parametres(llindarPercent, llindarApuntsPercent, 8, 15, 3);
        }

        public static Parametres perDefecte(int llindarPercent) {
            return perDefecte(llindarPercent, 95);
        }

        int llindar(Pregunta p) {
            return p.ambApunts() ? llindarApuntsPercent : llindarPercent;
        }
    }

    public record Resposta(UUID sessioId, String contingut) {}

    public record Pregunta(UUID id, int ordre, QuestionType tipus, String enunciat, String model,
                           String correcta, boolean bonus, boolean ambApunts, List<Resposta> respostes) {

        public Pregunta(UUID id, int ordre, QuestionType tipus, String enunciat, String model,
                        String correcta, boolean bonus, List<Resposta> respostes) {
            this(id, ordre, tipus, enunciat, model, correcta, bonus, false, respostes);
        }
    }

    /** Interval de caràcters [inici, fi) d'una resposta, per ressaltar-lo. */
    public record Interval(int inici, int fi) {}

    public record Coincidencia(UUID preguntaId, int ordre, QuestionType tipus, boolean ambApunts, int semblanca,
                               String respostaA, String respostaB, List<Interval> marquesA, List<Interval> marquesB) {}

    public record Parella(UUID sessioA, UUID sessioB, List<Coincidencia> coincidencies,
                          List<Integer> erradesTestComunes) {
        public int maxSemblanca() {
            return coincidencies.stream().mapToInt(Coincidencia::semblanca).max().orElse(0);
        }

        /** Coincidències en preguntes sense apunts: són les que més pesen. */
        public long coincidenciesSenseApunts() {
            return coincidencies.stream().filter(c -> !c.ambApunts()).count();
        }
    }

    private DeteccioCopies() {}

    public static List<Parella> analitza(List<Pregunta> preguntes, Parametres par) {
        // clau del parell (ids ordenats) → dades acumulades
        Map<List<UUID>, List<Coincidencia>> coincidencies = new HashMap<>();
        Map<List<UUID>, List<Integer>> errades = new HashMap<>();

        for (Pregunta p : preguntes) {
            if (COMPARABLES.contains(p.tipus())) {
                comparaRespostes(p, par, coincidencies);
            } else if (p.tipus() == QuestionType.CHOICE && !p.bonus() && p.correcta() != null) {
                erradesComunes(p, errades);
            }
        }

        Set<List<UUID>> claus = new HashSet<>(coincidencies.keySet());
        errades.forEach((k, v) -> { if (v.size() >= par.minErradesTest()) claus.add(k); });

        List<Parella> out = new ArrayList<>();
        for (List<UUID> k : claus) {
            List<Coincidencia> c = coincidencies.getOrDefault(k, List.of()).stream()
                    .sorted(Comparator.comparingInt(Coincidencia::ordre)).toList();
            List<Integer> e = errades.getOrDefault(k, List.of()).stream().sorted().toList();
            out.add(new Parella(k.get(0), k.get(1), c, e));
        }
        out.sort(Comparator.comparingLong(Parella::coincidenciesSenseApunts).reversed()
                .thenComparing(Comparator.comparingInt((Parella p) -> p.coincidencies().size()).reversed())
                .thenComparing(Comparator.comparingInt(Parella::maxSemblanca).reversed())
                .thenComparing(Comparator.comparingInt((Parella p) -> p.erradesTestComunes().size()).reversed()));
        return out;
    }

    // ── Preguntes de text i de codi ─────────────────────────────────────────

    private record Analitzada(Resposta resposta, List<Token> tokens, Map<String, List<Integer>> fragments) {}

    private static void comparaRespostes(Pregunta p, Parametres par, Map<List<UUID>, List<Coincidencia>> acc) {
        int minim = TEXT.contains(p.tipus()) ? par.minParaulesText() : par.minTokensCodi();
        List<Analitzada> analitzades = new ArrayList<>();
        for (Resposta r : p.respostes()) {
            List<Token> tokens = Tokenitzador.tokens(r.contingut(), p.tipus());
            if (tokens.size() < minim) continue;    // massa curta: s'assemblaria per força
            analitzades.add(new Analitzada(r, tokens, fragments(tokens)));
        }
        if (analitzades.size() < 2) return;

        Set<String> comuns = fragmentsComuns(p, analitzades);

        for (int i = 0; i < analitzades.size(); i++) {
            for (int j = i + 1; j < analitzades.size(); j++) {
                Analitzada a = analitzades.get(i), b = analitzades.get(j);
                Set<String> fa = new HashSet<>(a.fragments().keySet());
                Set<String> fb = new HashSet<>(b.fragments().keySet());
                fa.removeAll(comuns);
                fb.removeAll(comuns);
                if (fa.isEmpty() || fb.isEmpty()) continue;
                Set<String> comp = new HashSet<>(fa);
                comp.retainAll(fb);
                int semblanca = (int) Math.floor(100.0 * comp.size() / Math.min(fa.size(), fb.size()));
                if (semblanca < par.llindar(p)) continue;

                List<UUID> clau = clau(a.resposta().sessioId(), b.resposta().sessioId());
                boolean aPrimer = clau.get(0).equals(a.resposta().sessioId());
                Analitzada x = aPrimer ? a : b, y = aPrimer ? b : a;
                acc.computeIfAbsent(clau, k -> new ArrayList<>()).add(new Coincidencia(
                        p.id(), p.ordre(), p.tipus(), p.ambApunts(), semblanca,
                        x.resposta().contingut(), y.resposta().contingut(),
                        marques(x, comp), marques(y, comp)));
            }
        }
    }

    /**
     * Fragments que no indiquen còpia: els de l'enunciat i la resposta model, i els que apareixen
     * en moltes respostes (almenys {@value #MIN_RESPOSTES_COMU} i almenys el 40% de la classe). El mínim
     * alt evita que un grup petit que es copia entre ells (fins a 4) es descompti com a "comú".
     */
    private static Set<String> fragmentsComuns(Pregunta p, List<Analitzada> analitzades) {
        Set<String> comuns = new HashSet<>();
        comuns.addAll(fragments(Tokenitzador.tokens(p.model(), p.tipus())).keySet());
        comuns.addAll(fragments(Tokenitzador.tokens(p.enunciat(), p.tipus())).keySet());
        if (!TEXT.contains(p.tipus())) {
            // l'enunciat és text: també en forma de text, per als fragments literals que s'hi repeteixen
            comuns.addAll(fragments(Tokenitzador.tokens(p.enunciat(), QuestionType.TEXT)).keySet());
        }
        Map<String, Integer> freq = new HashMap<>();
        for (Analitzada a : analitzades) {
            for (String f : a.fragments().keySet()) freq.merge(f, 1, Integer::sum);
        }
        int llindarFreq = Math.max(MIN_RESPOSTES_COMU, (int) Math.ceil(0.4 * analitzades.size()));
        freq.forEach((f, n) -> { if (n >= llindarFreq) comuns.add(f); });
        return comuns;
    }

    /** Fragment → posicions (índex del primer token) on comença. */
    private static Map<String, List<Integer>> fragments(List<Token> tokens) {
        Map<String, List<Integer>> out = new HashMap<>();
        for (int i = 0; i + K <= tokens.size(); i++) {
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < K; k++) sb.append(tokens.get(i + k).valor()).append('\u0001');
            out.computeIfAbsent(sb.toString(), x -> new ArrayList<>()).add(i);
        }
        return out;
    }

    /** Intervals de text original coberts pels fragments compartits (els consecutius s'uneixen). */
    private static List<Interval> marques(Analitzada a, Set<String> compartits) {
        boolean[] marcat = new boolean[a.tokens().size()];
        for (String f : compartits) {
            for (int inici : a.fragments().getOrDefault(f, List.of())) {
                for (int k = 0; k < K; k++) marcat[inici + k] = true;
            }
        }
        List<Interval> out = new ArrayList<>();
        int i = 0;
        while (i < marcat.length) {
            if (!marcat[i]) { i++; continue; }
            int j = i;
            while (j + 1 < marcat.length && marcat[j + 1]) j++;
            out.add(new Interval(a.tokens().get(i).inici(), a.tokens().get(j).fi()));
            i = j + 1;
        }
        return out;
    }

    // ── Preguntes de test ───────────────────────────────────────────────────

    private static void erradesComunes(Pregunta p, Map<List<UUID>, List<Integer>> acc) {
        String correcta = p.correcta().strip().toLowerCase(Locale.ROOT);
        Map<String, List<UUID>> perOpcio = new HashMap<>();
        for (Resposta r : p.respostes()) {
            if (r.contingut() == null || r.contingut().isBlank()) continue;
            String lletra = r.contingut().strip().toLowerCase(Locale.ROOT);
            if (!lletra.equals(correcta)) perOpcio.computeIfAbsent(lletra, k -> new ArrayList<>()).add(r.sessioId());
        }
        for (List<UUID> mateixaErrada : perOpcio.values()) {
            for (int i = 0; i < mateixaErrada.size(); i++) {
                for (int j = i + 1; j < mateixaErrada.size(); j++) {
                    acc.computeIfAbsent(clau(mateixaErrada.get(i), mateixaErrada.get(j)), k -> new ArrayList<>())
                            .add(p.ordre());
                }
            }
        }
    }

    private static List<UUID> clau(UUID a, UUID b) {
        return a.compareTo(b) <= 0 ? List.of(a, b) : List.of(b, a);
    }
}
