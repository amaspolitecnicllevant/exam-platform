package com.examplatform.infrastructure.parser;

import com.examplatform.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class MarkdownExamParserTest {

    private MarkdownExamParser parser;
    private User professor;

    @BeforeEach
    void setUp() {
        parser = new MarkdownExamParser();
        professor = User.builder().name("Prof").email("prof@test.cat").role(Role.PROFESSOR).build();
    }

    // ── Casos correctes ──────────────────────────────────────────────────────

    @Test
    void parseja_examen_basic_amb_una_pregunta() {
        String md = """
                # Examen de prova
                durada: 60
                ---
                ## 1. [TEXT] [pts:10]
                Explica X.
                :::model
                Resposta model.
                :::
                """;

        Exam exam = parser.parse(md, professor);

        assertThat(exam.getTitle()).isEqualTo("Examen de prova");
        assertThat(exam.getDurada()).isEqualTo(60);
        assertThat(exam.getQuestions()).hasSize(1);

        Question q = exam.getQuestions().get(0);
        assertThat(q.getTipus()).isEqualTo(QuestionType.TEXT);
        assertThat(q.getPunts()).isEqualByComparingTo("10");
        assertThat(q.getOrdre()).isEqualTo(1);
        assertThat(q.getModelResposta()).isEqualTo("Resposta model.");
    }

    @Test
    void parseja_multiples_preguntes_amb_blocs_correccio() {
        String md = """
                # Examen complet
                ---
                ## 1. [SHORT] [pts:3]
                Pregunta curta.
                ## 2. [BASH-CMD] [pts:4]
                Executa una comanda.
                :::output-exact
                hola
                :::
                ## 3. [BASH-SCRIPT] [pts:3]
                Escriu un script.
                :::output-contains
                línia1
                línia2
                :::
                """;

        Exam exam = parser.parse(md, professor);

        assertThat(exam.getQuestions()).hasSize(3);
        assertThat(exam.getQuestions().get(1).getTipus()).isEqualTo(QuestionType.BASH_CMD);
        assertThat(exam.getQuestions().get(1).getOutputExact()).isEqualTo("hola");
        assertThat(exam.getQuestions().get(2).getOutputContains()).contains("línia1");
    }

    @Test
    void parseja_pregunta_amb_output_regex() {
        String md = """
                # Test regex
                ---
                ## 1. [BASH-CMD] [pts:10]
                Enunciat.
                :::output-regex
                ^\\d+$
                :::
                """;

        Exam exam = parser.parse(md, professor);
        assertThat(exam.getQuestions().get(0).getOutputRegex()).isEqualTo("^\\d+$");
    }

    @Test
    void parseja_pregunta_amb_test_script() {
        String md = """
                # Test amb test-script
                ---
                ## 1. [BASH-SCRIPT] [pts:10]
                Escriu una funció.
                :::test
                source $SCRIPT_FILE
                [ "$(myfunc)" = "ok" ] && exit 0 || exit 1
                :::
                """;

        Exam exam = parser.parse(md, professor);
        assertThat(exam.getQuestions().get(0).getTestScript()).contains("myfunc");
    }

    @Test
    void durada_per_defecte_es_90_minuts() {
        String md = """
                # Sense durada
                ---
                ## 1. [TEXT] [pts:10]
                Pregunta.
                """;

        Exam exam = parser.parse(md, professor);
        assertThat(exam.getDurada()).isEqualTo(90);
    }

    @Test
    void suma_punts_10_exacta_amb_decimals() {
        String md = """
                # Decimals
                ---
                ## 1. [SHORT] [pts:3.5]
                P1.
                ## 2. [SHORT] [pts:3.5]
                P2.
                ## 3. [SHORT] [pts:3.0]
                P3.
                """;

        assertThatNoException().isThrownBy(() -> parser.parse(md, professor));
    }

    // ── Errors esperats ───────────────────────────────────────────────────────

    @Test
    void falla_si_no_hi_ha_separador() {
        String md = "# Examen sense separador\ndurada: 60";
        assertThatThrownBy(() -> parser.parse(md, professor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("---");
    }

    @Test
    void falla_si_no_hi_ha_titol() {
        String md = """
                durada: 60
                ---
                ## 1. [TEXT] [pts:10]
                Pregunta.
                """;
        assertThatThrownBy(() -> parser.parse(md, professor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("títol");
    }

    @Test
    void falla_si_suma_punts_no_es_10() {
        String md = """
                # Mal puntuat
                ---
                ## 1. [TEXT] [pts:5]
                Pregunta.
                ## 2. [TEXT] [pts:4]
                Altra.
                """;
        assertThatThrownBy(() -> parser.parse(md, professor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("punts");
    }

    @Test
    void falla_si_output_contains_es_buit() {
        String md = """
                # Contains buit
                ---
                ## 1. [BASH_CMD] [pts:10]
                Llista fitxers.
                :::output-contains

                :::
                """;
        assertThatThrownBy(() -> parser.parse(md, professor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pregunta 1")
                .hasMessageContaining("output-contains");
    }

    @Test
    void falla_si_output_regex_es_invalida() {
        String md = """
                # Regex invàlida
                ---
                ## 1. [BASH_CMD] [pts:10]
                Mostra la IP.
                :::output-regex
                ([0-9
                :::
                """;
        assertThatThrownBy(() -> parser.parse(md, professor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pregunta 1")
                .hasMessageContaining("output-regex");
    }

    @Test
    void accepta_output_regex_valida_i_output_contains_amb_linies() {
        String md = """
                # Criteris vàlids
                ---
                ## 1. [BASH_CMD] [pts:5]
                Mostra la IP.
                :::output-regex
                ^\\d+\\.\\d+
                :::
                ## 2. [BASH_CMD] [pts:5]
                Interfícies.
                :::output-contains
                eth0
                lo
                :::
                """;
        Exam exam = parser.parse(md, professor);

        assertThat(exam.getQuestions().get(0).getOutputRegex()).isEqualTo("^\\d+\\.\\d+");
        assertThat(exam.getQuestions().get(1).getOutputContains()).isEqualTo("eth0\nlo");
    }

    @Test
    void parseja_bloc_clau_en_pregunta_curta() {
        String md = """
                # Claus
                ---
                ## 1. [SHORT] [pts:10]
                Què fa un servidor DHCP?
                :::model
                Assigna adreces IP automàticament.
                :::
                :::clau
                adreça IP, IP | 6
                automàtic, automàticament | 4
                :::
                """;
        Question q = parser.parse(md, professor).getQuestions().get(0);

        assertThat(q.getClaus()).isEqualTo("adreça IP, IP | 6\nautomàtic, automàticament | 4");
        assertThat(q.getModelResposta()).isEqualTo("Assigna adreces IP automàticament.");
    }

    @Test
    void falla_si_clau_en_pregunta_no_de_text() {
        String md = """
                # Claus en codi
                ---
                ## 1. [BASH_CMD] [pts:10]
                Llista.
                :::clau
                ls
                :::
                """;
        assertThatThrownBy(() -> parser.parse(md, professor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pregunta 1")
                .hasMessageContaining("TEXT, SHORT o LONG");
    }

    @Test
    void falla_si_els_pesos_de_clau_no_sumen_els_punts() {
        String md = """
                # Pesos
                ---
                ## 1. [SHORT] [pts:10]
                Pregunta.
                :::clau
                DHCP | 4
                DNS | 4
                :::
                """;
        assertThatThrownBy(() -> parser.parse(md, professor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pregunta 1")
                .hasMessageContaining(":::clau");
    }

    @Test
    void pregunta_sense_clau_te_claus_null() {
        String md = """
                # Sense claus
                ---
                ## 1. [SHORT] [pts:10]
                Pregunta.
                """;
        assertThat(parser.parse(md, professor).getQuestions().get(0).getClaus()).isNull();
    }

    private static final String CHOICE_BODY = """
                Quina és la bona?
                - a) Una
                - b) Altra
                - c) Totes les anteriors
                :::model
                c
                :::
                """;

    @Test
    void choice_per_defecte_barreja_les_opcions() {
        String md = "# Test\n---\n## 1. [CHOICE] [pts:10]\n" + CHOICE_BODY;
        assertThat(parser.parse(md, professor).getQuestions().get(0).isBarrejarOpcions()).isTrue();
    }

    @Test
    void ordre_fix_no_barreja_les_opcions() {
        String md = "# Test\n---\n## 1. [CHOICE] [pts:10] [ordre:fix]\n" + CHOICE_BODY;
        Question q = parser.parse(md, professor).getQuestions().get(0);

        assertThat(q.isBarrejarOpcions()).isFalse();
        assertThat(q.getCorrectChoice()).isEqualTo("c");
    }

    @Test
    void ordre_fix_es_combina_amb_ra_i_dif_en_qualsevol_ordre() {
        String md = "# Test\n---\n## 1. [CHOICE] [pts:10] [dif:alta] [ordre:FIX] [ra:RA2]\n" + CHOICE_BODY;
        Question q = parser.parse(md, professor).getQuestions().get(0);

        assertThat(q.isBarrejarOpcions()).isFalse();
        assertThat(q.getRa()).isEqualTo("RA2");
        assertThat(q.getDificultat()).isEqualTo("alta");
    }

    @Test
    void falla_si_ordre_te_un_valor_desconegut() {
        String md = "# Test\n---\n## 1. [CHOICE] [pts:10] [ordre:aleatori]\n" + CHOICE_BODY;
        assertThatThrownBy(() -> parser.parse(md, professor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fix");
    }

    @Test
    void falla_si_ordre_fix_en_pregunta_no_de_test() {
        String md = "# Test\n---\n## 1. [SHORT] [pts:10] [ordre:fix]\nPregunta.\n";
        assertThatThrownBy(() -> parser.parse(md, professor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CHOICE");
    }

    @Test
    void falla_si_tipus_desconegut() {
        String md = """
                # Tipus erroni
                ---
                ## 1. [INVENTAT] [pts:10]
                Pregunta.
                """;
        assertThatThrownBy(() -> parser.parse(md, professor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVENTAT");
    }

    @Test
    void tipus_amb_guio_es_normalitza_correctament() {
        String md = """
                # Tipus amb guió
                ---
                ## 1. [bash-cmd] [pts:5]
                Comanda.
                ## 2. [ps-cmd] [pts:5]
                Comanda PS.
                """;

        Exam exam = parser.parse(md, professor);
        assertThat(exam.getQuestions().get(0).getTipus()).isEqualTo(QuestionType.BASH_CMD);
        assertThat(exam.getQuestions().get(1).getTipus()).isEqualTo(QuestionType.PS_CMD);
    }

    @Test
    void enunciat_es_el_text_abans_dels_blocs() {
        String md = """
                # Enunciat test
                ---
                ## 1. [SHORT] [pts:10]
                Aquesta és la pregunta.
                Una altra línia.
                :::model
                Resposta.
                :::
                """;

        Question q = parser.parse(md, professor).getQuestions().get(0);
        assertThat(q.getEnunciat()).contains("Aquesta és la pregunta.");
        assertThat(q.getEnunciat()).contains("Una altra línia.");
        assertThat(q.getEnunciat()).doesNotContain("Resposta.");
    }

    // ── Errors de format que abans es perdien en silenci ─────────────────────

    private static String examen(String cos) {
        return "# Test\ndurada: 30\n---\n" + cos;
    }

    private void assertRebutja(String md, String... fragments) {
        var t = assertThatThrownBy(() -> parser.parse(md, professor)).isInstanceOf(IllegalArgumentException.class);
        for (String f : fragments) t.hasMessageContaining(f);
    }

    @Nested
    class Rebutja {

        @Test
        void blocs_encadenats_sense_tancar() {
            assertRebutja(examen("""
                    ## 1. [bash-cmd] [pts:10]
                    Mostra IPs.

                    :::model
                    ip addr show
                    :::output-contains
                    inet
                    :::
                    """), "Línia 9", "Pregunta 1", ":::model", "no està tancat");
        }

        @Test
        void bloc_sense_tancar_al_final() {
            assertRebutja(examen("""
                    ## 1. [short] [pts:10]
                    P
                    :::model
                    resposta
                    """), "Línia 6", ":::model", "no està tancat");
        }

        @Test
        void tancament_sense_bloc_obert() {
            assertRebutja(examen("""
                    ## 1. [short] [pts:10]
                    P
                    :::
                    """), "Línia 6", "no vàlida");
        }

        @Test
        void obertura_amb_text_a_la_mateixa_linia() {
            assertRebutja(examen("""
                    ## 1. [choice] [pts:10]
                    P
                    - a) u
                    - b) d
                    :::model b
                    :::
                    """), "Línia 8", "no vàlida");
        }

        @Test
        void bloc_desconegut_amb_pista() {
            assertRebutja(examen("""
                    ## 1. [choice] [pts:10]
                    P
                    - a) u
                    - b) d
                    :::correct-choice
                    b
                    :::
                    """), "bloc desconegut :::correct-choice", ":::model");
        }

        @Test
        void bloc_repetit() {
            assertRebutja(examen("""
                    ## 1. [short] [pts:10]
                    P
                    :::model
                    a
                    :::
                    :::model
                    b
                    :::
                    """), "està repetit");
        }

        @Test
        void text_del_bloc_amb_triple_dos_punts() {
            assertRebutja(examen("""
                    ## 1. [short] [pts:10]
                    P
                    :::model
                    a ::: b
                    :::
                    """), "no pot contenir ':::'");
        }

        @Test
        void text_despres_dels_blocs() {
            assertRebutja(examen("""
                    ## 1. [short] [pts:10]
                    P
                    :::model
                    a
                    :::
                    Més enunciat oblidat.
                    """), "Línia 9", "text després dels blocs");
        }

        @Test
        void opcio_e() {
            assertRebutja(examen("""
                    ## 1. [choice] [pts:10]
                    Quina?
                    - a) u
                    - b) d
                    - e) cinc
                    :::model
                    b
                    :::
                    """), "Pregunta 1", "e) cinc", "a) a d)");
        }

        @Test
        void opcio_repetida() {
            assertRebutja(examen("""
                    ## 1. [choice] [pts:10]
                    Quina?
                    - a) u
                    - a) d
                    :::model
                    a
                    :::
                    """), "repetida");
        }

        @Test
        void opcio_sense_guio() {
            assertRebutja(examen("""
                    ## 1. [choice] [pts:10]
                    Quina?
                    - a) u
                    b) d
                    :::model
                    a
                    :::
                    """), "ha de començar amb '- '");
        }

        @Test
        void resposta_correcta_fora_de_les_opcions() {
            assertRebutja(examen("""
                    ## 1. [choice] [pts:10]
                    Quina?
                    - a) u
                    - b) d
                    :::model
                    c
                    :::
                    """), "«c» no és cap de les opcions");
        }

        @Test
        void seccio_dins_d_un_enunciat() {
            assertRebutja(examen("""
                    ## 1. [long] [pts:10]
                    Llegeix:
                    ### Context
                    Text del context.
                    """), "Línia 7", "secció «Context»", "negreta");
        }

        @Test
        void text_abans_de_la_primera_pregunta() {
            assertRebutja(examen("""
                    Introducció oblidada.
                    ## 1. [short] [pts:10]
                    P
                    """), "Línia 4", "abans de la primera pregunta");
        }

        @Test
        void dificultat_invalida() {
            assertRebutja(examen("""
                    ## 1. [short] [pts:10] [dif:facil]
                    P
                    """), "dificultat «facil»");
        }

        @Test
        void etiqueta_desconeguda() {
            assertRebutja(examen("""
                    ## 1. [short] [pts:5]
                    P1
                    ## 2. [short] [pts:5] [pes:1]
                    P2
                    """), "Línia 6", "Etiqueta desconeguda [pes");
        }

        @Test
        void punts_amb_coma() {
            assertRebutja(examen("""
                    ## 1. [short] [pts:5,5]
                    P
                    """), "punt decimal");
        }

        @Test
        void capcalera_sense_numero() {
            assertRebutja(examen("""
                    ## [short] [pts:10]
                    P
                    """), "Falta el número");
        }

        @Test
        void test_en_java() {
            assertRebutja(examen("""
                    ## 1. [java-prog] [pts:10]
                    P
                    :::test
                    exit 0
                    :::
                    """), ":::test només", ":::output-*");
        }

        @Test
        void output_en_pregunta_de_text() {
            assertRebutja(examen("""
                    ## 1. [short] [pts:10]
                    P
                    :::output-contains
                    x
                    :::
                    """), "només es pot usar en preguntes de codi");
        }

        @Test
        void output_en_html_css_explica_que_es_corregeix_a_ma() {
            assertRebutja(examen("""
                    ## 1. [html-css] [pts:10]
                    Fes un formulari.
                    :::output-contains
                    Registrar
                    :::
                    """), "HTML_CSS no s'executen");
        }

        @Test
        void dos_criteris_de_sortida() {
            assertRebutja(examen("""
                    ## 1. [bash-cmd] [pts:10]
                    P
                    :::output-exact
                    1
                    :::
                    :::output-regex
                    \\d
                    :::
                    """), "només un criteri");
        }

        @Test
        void test_combinat_amb_criteri_de_sortida() {
            assertRebutja(examen("""
                    ## 1. [bash-script] [pts:10]
                    P
                    :::test
                    exit 0
                    :::
                    :::output-contains
                    x
                    :::
                    """), "no combinis :::test");
        }

        @Test
        void bloc_de_codi_sense_tancar() {
            assertRebutja(examen("""
                    ## 1. [long] [pts:10]
                    Mira:
                    ```
                    codi
                    """), "Línia 6", "```");
        }

        @Test
        void durada_no_numerica() {
            assertRebutja("# T\ndurada: una hora\n---\n## 1. [short] [pts:10]\nP\n", "durada");
        }
    }

    @Nested
    class Accepta {

        @Test
        void fitxer_amb_salts_de_linia_de_windows() {
            String md = examen("""
                    ## 1. [bash-cmd] [pts:10]
                    P
                    :::model
                    ls
                    :::

                    :::output-contains
                    fitxer
                    :::
                    """).replace("\n", "\r\n");
            Question q = parser.parse(md, professor).getQuestions().get(0);

            assertThat(q.getOutputContains()).isEqualTo("fitxer");
            assertThat(q.getModelResposta()).isEqualTo("ls");
        }

        @Test
        void fitxer_amb_bom() {
            Exam e = parser.parse("\uFEFF" + examen("## 1. [short] [pts:10]\nP\n"), professor);
            assertThat(e.getTitle()).isEqualTo("Test");
        }

        @Test
        void comentaris_amb_coixinets_dins_d_un_script_no_son_preguntes_ni_seccions() {
            Exam e = parser.parse(examen("""
                    ## 1. [bash-script] [pts:10]
                    Escriu l'script.
                    :::test
                    #!/bin/bash
                    ## 2. [short] [pts:1] això és un comentari
                    ### també un comentari
                    exit 0
                    :::
                    """), professor);

            assertThat(e.getQuestions()).singleElement().satisfies(q ->
                    assertThat(q.getTestScript()).contains("### també un comentari"));
        }

        @Test
        void separadors_entre_parts_s_ignoren() {
            Exam e = parser.parse(examen("""
                    ### Part 1
                    ## 1. [short] [pts:5]
                    P1
                    :::model
                    a
                    :::

                    ---

                    ### Part 2

                    ***

                    ## 2. [short] [pts:5]
                    P2
                    """), professor);

            assertThat(e.getQuestions()).extracting(Question::getTipus).containsExactly(
                    QuestionType.SECTION, QuestionType.SHORT, QuestionType.SECTION, QuestionType.SHORT);
        }

        @Test
        void separador_dins_d_un_enunciat_es_conserva() {
            Question q = parser.parse(examen("""
                    ## 1. [long] [pts:10]
                    Primera part.

                    ---

                    Segona part.
                    """), professor).getQuestions().get(0);

            assertThat(q.getEnunciat()).contains("Primera part.", "---", "Segona part.");
        }

        @Test
        void blocs_de_codi_a_l_enunciat_es_conserven_encara_que_continguin_marcadors() {
            Question q = parser.parse(examen("""
                    ## 1. [long] [pts:10]
                    Observa:
                    ```
                    ## no és una pregunta
                    :::no és un bloc
                    ```
                    Explica-ho.
                    """), professor).getQuestions().get(0);

            assertThat(q.getEnunciat()).contains("## no és una pregunta", ":::no és un bloc", "Explica-ho.");
        }

        @Test
        void numero_del_fitxer_als_errors_encara_que_hi_hagi_seccions() {
            // amb seccions, l'ordre intern (4) no coincideix amb el número del fitxer (3)
            assertRebutja(examen("""
                    ### A
                    ## 1. [short] [pts:5]
                    P
                    ### B
                    ## 3. [short] [pts:5] [dif:dificil]
                    P
                    """), "Pregunta 3");
        }
    }

    // ── Preguntes amb apunts ──────────────────────────────────────────────────

    @Test
    void apunts_a_la_seccio_marca_totes_les_seves_preguntes() {
        Exam e = parser.parse(examen("""
                ### Part 1 — Teoria
                ## 1. [short] [pts:5]
                P1
                ### Part 2 — Pràctica [apunts]
                ## 2. [bash-script] [pts:3]
                P2
                ## 3. [java-prog] [pts:2]
                P3
                """), professor);

        assertThat(e.getQuestions()).extracting(Question::getEnunciat, Question::isAmbApunts).containsExactly(
                tuple("Part 1 — Teoria", false), tuple("P1", false),
                tuple("Part 2 — Pràctica", false), tuple("P2", true), tuple("P3", true));
    }

    @Test
    void apunts_en_una_sola_pregunta() {
        Exam e = parser.parse(examen("""
                ## 1. [short] [pts:5]
                P1
                ## 2. [long] [pts:5] [dif:alta] [apunts] [ra:RA2]
                P2
                """), professor);

        assertThat(e.getQuestions()).extracting(Question::isAmbApunts).containsExactly(false, true);
        assertThat(e.getQuestions().get(1).getRa()).isEqualTo("RA2");
    }

    @Test
    void sense_apunts_per_defecte() {
        assertThat(parser.parse(examen("## 1. [short] [pts:10]\nP\n"), professor)
                .getQuestions().get(0).isAmbApunts()).isFalse();
    }
}
