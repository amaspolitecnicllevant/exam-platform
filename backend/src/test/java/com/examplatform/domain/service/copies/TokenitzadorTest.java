package com.examplatform.domain.service.copies;

import com.examplatform.domain.model.QuestionType;
import com.examplatform.domain.service.copies.Tokenitzador.Token;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TokenitzadorTest {

    private static List<String> valors(String text, QuestionType tipus) {
        return Tokenitzador.tokens(text, tipus).stream().map(Token::valor).toList();
    }

    @Test
    void text_sense_majuscules_accents_ni_puntuacio() {
        assertThat(valors("El DHCP assigna l'adreça IP, automàticament!", QuestionType.SHORT))
                .containsExactly("el", "dhcp", "assigna", "l", "adreca", "ip", "automaticament");
    }

    @Test
    void les_posicions_apunten_al_text_original() {
        String text = "Hola  Món";
        List<Token> t = Tokenitzador.tokens(text, QuestionType.TEXT);
        assertThat(text.substring(t.get(1).inici(), t.get(1).fi())).isEqualTo("Món");
    }

    @Test
    void java_renombra_identificadors_i_ignora_comentaris() {
        String a = "int suma = 0; // acumulador\nfor (int i = 1; i <= 100; i++) suma += i;";
        String b = "int total = 0; /* diferent */ for (int k = 1; k <= 100; k++) total += k;";
        assertThat(valors(a, QuestionType.JAVA_PROG)).isEqualTo(valors(b, QuestionType.JAVA_PROG));
        assertThat(valors(a, QuestionType.JAVA_PROG)).contains("int", "for", "ID").doesNotContain("suma", "acumulador");
    }

    @Test
    void java_conserva_paraules_reservades_i_api_habitual() {
        assertThat(valors("System.out.println(x);", QuestionType.JAVA_PROG))
                .containsExactly("System", ".", "out", ".", "println", "(", "ID", ")", ";");
    }

    @Test
    void bash_renombra_variables_pero_no_comandes() {
        String a = "#!/bin/bash\n# compta\nn=$(ls \"$1\" | wc -l)\necho \"Fitxers: $n\"";
        String b = "#!/bin/bash\nnum=$(ls \"$1\" | wc -l)\necho \"Fitxers: $num\"";
        assertThat(valors(a, QuestionType.BASH_SCRIPT)).containsSubsequence("VAR", "=", "$", "(", "ls");
        assertThat(valors(a, QuestionType.BASH_SCRIPT)).doesNotContain("n", "num", "compta");
        assertThat(valors(a, QuestionType.BASH_SCRIPT)).contains("wc", "echo");
        // les variables dins de les cadenes també es renombren: tots dos scripts són equivalents
        assertThat(valors(a, QuestionType.BASH_SCRIPT)).isEqualTo(valors(b, QuestionType.BASH_SCRIPT));
        assertThat(valors(a, QuestionType.BASH_SCRIPT)).contains("\"fitxers: var\"");
    }

    @Test
    void html_substitueix_valors_d_atributs() {
        assertThat(valors("<div class=\"caixa-1\">Hola</div>", QuestionType.HTML_CSS))
                .isEqualTo(valors("<div class='x'>Hola</div>", QuestionType.HTML_CSS));
    }

    @Test
    void resposta_buida_o_tipus_no_comparable_no_dona_tokens() {
        assertThat(Tokenitzador.tokens("   ", QuestionType.LONG)).isEmpty();
        assertThat(Tokenitzador.tokens(null, QuestionType.LONG)).isEmpty();
        assertThat(Tokenitzador.tokens("b", QuestionType.CHOICE)).isEmpty();
    }
}
