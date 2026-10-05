package com.examplatform.domain.service.copies;

import com.examplatform.domain.model.QuestionType;
import com.examplatform.domain.service.copies.DeteccioCopies.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeteccioCopiesTest {

    private static final Parametres PAR = Parametres.perDefecte(80);
    private final List<UUID> alumnes = new ArrayList<>();

    private UUID alumne(int i) {
        while (alumnes.size() <= i) alumnes.add(UUID.randomUUID());
        return alumnes.get(i);
    }

    private Pregunta pregunta(QuestionType tipus, String model, String... respostes) {
        List<Resposta> rs = new ArrayList<>();
        for (int i = 0; i < respostes.length; i++) rs.add(new Resposta(alumne(i), respostes[i]));
        return new Pregunta(UUID.randomUUID(), 1, tipus, "Enunciat de la pregunta.", model, null, false, rs);
    }

    private static final String TEXT_A = "El servidor DHCP s'encarrega de donar a cada ordinador de la xarxa "
            + "una adreça IP lliure i també li indica la porta d'enllaç i els servidors DNS que ha de fer servir";
    private static final String TEXT_B = "Quan un equip arrenca envia una petició de difusió i el servidor respon "
            + "amb una configuració completa perquè pugui comunicar-se sense configurar-lo a mà";

    @Test
    void textos_identics_es_detecten_al_100_per_cent() {
        List<Parella> r = DeteccioCopies.analitza(List.of(pregunta(QuestionType.LONG, null, TEXT_A, TEXT_A, TEXT_B)), PAR);

        assertThat(r).singleElement().satisfies(p -> {
            assertThat(p.coincidencies()).singleElement().satisfies(c -> assertThat(c.semblanca()).isEqualTo(100));
            assertThat(List.of(p.sessioA(), p.sessioB())).containsExactlyInAnyOrder(alumne(0), alumne(1));
        });
    }

    @Test
    void textos_diferents_sobre_el_mateix_tema_no_es_detecten() {
        assertThat(DeteccioCopies.analitza(List.of(pregunta(QuestionType.LONG, null, TEXT_A, TEXT_B)), PAR)).isEmpty();
    }

    @Test
    void una_copia_amb_alguna_paraula_canviada_es_detecta() {
        String retocat = TEXT_A.replace("lliure", "disponible").replace("ordinador", "equip");

        List<Parella> r = DeteccioCopies.analitza(List.of(pregunta(QuestionType.LONG, null, TEXT_A, retocat)), PAR);

        assertThat(r).singleElement().satisfies(p ->
                assertThat(p.coincidencies().get(0).semblanca()).isBetween(80, 99));
    }

    @Test
    void dues_respostes_iguals_a_la_resposta_model_no_es_detecten() {
        assertThat(DeteccioCopies.analitza(List.of(pregunta(QuestionType.LONG, TEXT_A, TEXT_A, TEXT_A)), PAR)).isEmpty();
    }

    @Test
    void respostes_curtes_no_es_comparen() {
        String curta = "Assigna adreces IP automàticament";   // 4 paraules
        assertThat(DeteccioCopies.analitza(List.of(pregunta(QuestionType.SHORT, null, curta, curta)), PAR)).isEmpty();
    }

    @Test
    void les_comandes_d_una_linia_no_es_comparen() {
        String cmd = "find /home -type f -name \"*.txt\" -mtime -7 -exec ls -l {} \\; | sort -k5 -n | tail -n 3";
        assertThat(DeteccioCopies.analitza(List.of(pregunta(QuestionType.BASH_CMD, null, cmd, cmd)), PAR)).isEmpty();
    }

    @Test
    void codi_java_copiat_amb_noms_i_comentaris_canviats_es_detecta() {
        String a = """
                public class Main {
                    public static void main(String[] args) {
                        int suma = 0;
                        for (int i = 2; i <= 100; i += 2) { if (i % 3 != 0) suma += i * i; }
                        System.out.println("Resultat: " + suma);
                    }
                }""";
        String b = """
                public class Main {
                  // el meu programa
                  public static void main(String[] args) {
                    int total = 0;
                    for (int k = 2; k <= 100; k += 2) { if (k % 3 != 0) total += k * k; }
                    System.out.println("Resultat: " + total);
                  }
                }""";
        String diferent = """
                public class Main {
                    public static void main(String[] args) {
                        long acumulat = java.util.stream.IntStream.rangeClosed(1, 50)
                            .map(n -> n * 2).filter(n -> n % 3 != 0).mapToLong(n -> (long) n * n).sum();
                        System.out.println(acumulat);
                    }
                }""";

        List<Parella> r = DeteccioCopies.analitza(List.of(pregunta(QuestionType.JAVA_PROG, null, a, b, diferent)), PAR);

        assertThat(r).singleElement().satisfies(p -> {
            assertThat(List.of(p.sessioA(), p.sessioB())).containsExactlyInAnyOrder(alumne(0), alumne(1));
            assertThat(p.maxSemblanca()).isGreaterThanOrEqualTo(90);
        });
    }

    @Test
    void script_bash_copiat_amb_variables_renombrades_es_detecta() {
        String a = "#!/bin/bash\ndir=$1\nif [ ! -d \"$dir\" ]; then echo \"No existeix\"; exit 1; fi\n"
                + "n=$(find \"$dir\" -type f | wc -l)\nmida=$(du -sk \"$dir\" | cut -f1)\necho \"Fitxers: $n Mida: $mida\"";
        String b = "#!/bin/bash\n# script de l'examen\ncarpeta=$1\nif [ ! -d \"$carpeta\" ]; then echo \"No existeix\"; exit 1; fi\n"
                + "x=$(find \"$carpeta\" -type f | wc -l)\ny=$(du -sk \"$carpeta\" | cut -f1)\necho \"Fitxers: $x Mida: $y\"";

        assertThat(DeteccioCopies.analitza(List.of(pregunta(QuestionType.BASH_SCRIPT, null, a, b)), PAR)).hasSize(1);
    }

    @Test
    void el_que_escriu_mitja_classe_es_descompta() {
        String comu = "Primer de tot cal instal·lar el paquet del servidor amb el gestor de paquets i després ";
        String[] respostes = {
                comu + "editar el fitxer de configuració per definir el rang d'adreces que es repartiran",
                comu + "reiniciar el servei perquè agafi els canvis i comprovar-ho amb un client de proves",
                comu + "obrir el port al tallafocs perquè els clients hi puguin arribar des de la xarxa local",
                comu + "revisar els registres del sistema per veure si hi ha hagut cap error en arrencar",
                comu + "configurar una reserva fixa per a la impressora perquè sempre tingui la mateixa adreça",
                comu + "configurar una reserva fixa per a la impressora perquè sempre tingui la mateixa adreça",
        };

        List<Parella> r = DeteccioCopies.analitza(List.of(pregunta(QuestionType.LONG, null, respostes)), PAR);

        // només el parell que coincideix també en la part pròpia
        assertThat(r).singleElement().satisfies(p ->
                assertThat(List.of(p.sessioA(), p.sessioB())).containsExactlyInAnyOrder(alumne(4), alumne(5)));
    }

    @Test
    void un_grup_de_tres_que_es_copia_es_detecta_encara_que_la_classe_sigui_petita() {
        List<Parella> r = DeteccioCopies.analitza(List.of(pregunta(QuestionType.LONG, null, TEXT_A, TEXT_A, TEXT_A, TEXT_B)), PAR);
        assertThat(r).hasSize(3);
    }

    @Test
    void les_marques_corresponen_al_text_copiat() {
        String propi = "Això ho he escrit jo sol i no té res a veure amb cap altra resposta de la classe. ";
        List<Parella> r = DeteccioCopies.analitza(List.of(pregunta(QuestionType.LONG, null, propi + TEXT_A, TEXT_A)), PAR);

        Coincidencia c = r.get(0).coincidencies().get(0);
        String resposta = c.respostaA().equals(TEXT_A) ? c.respostaB() : c.respostaA();
        List<Interval> marques = c.respostaA().equals(TEXT_A) ? c.marquesB() : c.marquesA();
        assertThat(marques).singleElement().satisfies(m ->
                assertThat(resposta.substring(m.inici(), m.fi())).isEqualTo(TEXT_A));
    }

    @Test
    void el_llindar_es_configurable() {
        String retocat = TEXT_A.replace("lliure", "disponible").replace("ordinador", "equip");
        Pregunta p = pregunta(QuestionType.LONG, null, TEXT_A, retocat);

        assertThat(DeteccioCopies.analitza(List.of(p), Parametres.perDefecte(80))).hasSize(1);
        assertThat(DeteccioCopies.analitza(List.of(p), Parametres.perDefecte(100))).isEmpty();
    }

    // ── Test: errades coincidents ──

    private Pregunta test(int ordre, String correcta, boolean bonus, String... respostes) {
        List<Resposta> rs = new ArrayList<>();
        for (int i = 0; i < respostes.length; i++) rs.add(new Resposta(alumne(i), respostes[i]));
        return new Pregunta(UUID.randomUUID(), ordre, QuestionType.CHOICE, "?", null, correcta, bonus, rs);
    }

    @Test
    void tres_errades_amb_la_mateixa_opcio_es_marquen() {
        List<Pregunta> ps = List.of(
                test(1, "a", false, "c", "c", "a"),
                test(2, "b", false, "d", "d", "b"),
                test(3, "c", false, "a", "a", "c"),
                test(4, "d", false, "d", "d", "d"));   // encerts comuns: no compten

        List<Parella> r = DeteccioCopies.analitza(ps, PAR);

        assertThat(r).singleElement().satisfies(p -> {
            assertThat(p.erradesTestComunes()).containsExactly(1, 2, 3);
            assertThat(p.coincidencies()).isEmpty();
        });
    }

    @Test
    void dues_errades_coincidents_no_es_marquen() {
        List<Pregunta> ps = List.of(test(1, "a", false, "c", "c"), test(2, "b", false, "d", "d"));
        assertThat(DeteccioCopies.analitza(ps, PAR)).isEmpty();
    }

    @Test
    void errades_amb_opcions_diferents_no_coincideixen() {
        List<Pregunta> ps = List.of(test(1, "a", false, "c", "d"), test(2, "b", false, "d", "c"), test(3, "c", false, "a", "b"));
        assertThat(DeteccioCopies.analitza(ps, PAR)).isEmpty();
    }

    @Test
    void les_preguntes_amb_bonus_no_compten_com_a_errades() {
        List<Pregunta> ps = List.of(
                test(1, "a", true, "c", "c"),
                test(2, "b", false, "d", "d"),
                test(3, "c", false, "a", "a"));
        assertThat(DeteccioCopies.analitza(ps, PAR)).isEmpty();
    }

    @Test
    void els_parells_amb_mes_preguntes_sospitoses_van_primer() {
        Pregunta p1 = pregunta(QuestionType.LONG, null, TEXT_A, TEXT_A, TEXT_B, TEXT_B);
        Pregunta p2 = new Pregunta(UUID.randomUUID(), 2, QuestionType.LONG, "Enunciat", null, null, false,
                List.of(new Resposta(alumne(2), TEXT_A + " i res més"), new Resposta(alumne(3), TEXT_A + " i res més")));

        List<Parella> r = DeteccioCopies.analitza(List.of(p1, p2), PAR);

        assertThat(r).hasSize(2);
        assertThat(List.of(r.get(0).sessioA(), r.get(0).sessioB())).containsExactlyInAnyOrder(alumne(2), alumne(3));
        assertThat(r.get(0).coincidencies()).hasSize(2);
    }

    // ── Preguntes amb apunts ──

    private Pregunta ambApunts(int ordre, String... respostes) {
        List<Resposta> rs = new ArrayList<>();
        for (int i = 0; i < respostes.length; i++) rs.add(new Resposta(alumne(i), respostes[i]));
        return new Pregunta(UUID.randomUUID(), ordre, QuestionType.LONG, "Enunciat", null, null, false, true, rs);
    }

    @Test
    void les_preguntes_amb_apunts_fan_servir_el_seu_llindar() {
        String retocat = TEXT_A.replace("lliure", "disponible").replace("ordinador", "equip");   // ~ 85 %

        assertThat(DeteccioCopies.analitza(List.of(ambApunts(1, TEXT_A, retocat)), Parametres.perDefecte(80, 95))).isEmpty();
        assertThat(DeteccioCopies.analitza(List.of(ambApunts(1, TEXT_A, retocat)), Parametres.perDefecte(80, 80))).hasSize(1);
    }

    @Test
    void una_copia_literal_en_una_pregunta_amb_apunts_es_marca_i_s_etiqueta() {
        List<Parella> r = DeteccioCopies.analitza(List.of(ambApunts(1, TEXT_A, TEXT_A)), Parametres.perDefecte(80, 95));

        assertThat(r).singleElement().satisfies(p -> {
            assertThat(p.coincidencies()).singleElement().satisfies(c -> assertThat(c.ambApunts()).isTrue());
            assertThat(p.coincidenciesSenseApunts()).isZero();
        });
    }

    @Test
    void les_coincidencies_sense_apunts_pesen_mes() {
        // parell 0-1: dues coincidències, però totes dues en preguntes amb apunts
        // parell 2-3: una sola coincidència, en una pregunta sense apunts → va primer
        Pregunta a1 = ambApunts(1, TEXT_A, TEXT_A, TEXT_B, "Una resposta diferent de totes les altres de la llista, prou llarga per comparar-la");
        Pregunta a2 = ambApunts(2, TEXT_B, TEXT_B, TEXT_A, "Una altra resposta diferent de totes les altres de la llista, també prou llarga");
        Pregunta teoria = new Pregunta(UUID.randomUUID(), 3, QuestionType.LONG, "Enunciat", null, null, false, List.of(
                new Resposta(alumne(0), TEXT_B), new Resposta(alumne(1), "Resposta pròpia i original escrita per l'alumne sense copiar ningú de la classe"),
                new Resposta(alumne(2), TEXT_A), new Resposta(alumne(3), TEXT_A)));

        List<Parella> r = DeteccioCopies.analitza(List.of(a1, a2, teoria), Parametres.perDefecte(80, 95));

        assertThat(r).hasSizeGreaterThanOrEqualTo(2);
        assertThat(List.of(r.get(0).sessioA(), r.get(0).sessioB())).containsExactlyInAnyOrder(alumne(2), alumne(3));
    }
}
