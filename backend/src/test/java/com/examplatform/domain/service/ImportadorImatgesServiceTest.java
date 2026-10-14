package com.examplatform.domain.service;

import com.examplatform.domain.model.Exam;
import com.examplatform.domain.model.Question;
import com.examplatform.domain.model.QuestionFile;
import com.examplatform.domain.model.QuestionType;
import com.examplatform.domain.port.DescarregadorImatges;
import com.examplatform.infrastructure.persistence.QuestionFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportadorImatgesServiceTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 1, 2, 3};
    static final String URL = "https://exemple.cat/img/xarxa.png";

    DescarregadorImatges descarregador = mock(DescarregadorImatges.class);
    QuestionFileRepository fileRepository = mock(QuestionFileRepository.class);
    ImportadorImatgesService service;
    @TempDir Path disc;

    @BeforeEach
    void prepara() {
        service = new ImportadorImatgesService(descarregador, fileRepository);
        ReflectionTestUtils.setField(service, "filesHostPath", disc.toString());
        ReflectionTestUtils.setField(service, "imatgeMaxBytes", 5L * 1024 * 1024);
        ReflectionTestUtils.setField(service, "maximImatges", 60);
        when(fileRepository.save(any(QuestionFile.class))).thenAnswer(inv -> {
            QuestionFile f = inv.getArgument(0);
            f.setId(UUID.randomUUID());
            return f;
        });
        when(descarregador.descarrega(any(), anyLong())).thenReturn(PNG);
    }

    private Exam examen(String... enunciats) {
        Exam e = new Exam();
        e.setQuestions(new ArrayList<>());
        int i = 1;
        for (String text : enunciats) {
            e.getQuestions().add(Question.builder().id(UUID.randomUUID()).exam(e).ordre(i++)
                    .tipus(QuestionType.SHORT).enunciat(text).build());
        }
        return e;
    }

    private long fitxersAlDisc() throws Exception {
        try (Stream<Path> s = Files.walk(disc)) {
            return s.filter(Files::isRegularFile).count();
        }
    }

    @Test
    void baixa_la_imatge_la_desa_i_reescriu_la_referencia() throws Exception {
        Exam e = examen("Mira la xarxa: ![Topologia](" + URL + ") i respon.");

        service.incorpora(e);

        ArgumentCaptor<QuestionFile> captor = ArgumentCaptor.forClass(QuestionFile.class);
        verify(fileRepository).save(captor.capture());
        QuestionFile qf = captor.getValue();
        assertThat(qf.getFilename()).isEqualTo("xarxa.png");
        assertThat(qf.getContentType()).isEqualTo("image/png");
        assertThat(qf.getFileSize()).isEqualTo(PNG.length);
        assertThat(Files.readAllBytes(Path.of(qf.getStoredPath()))).isEqualTo(PNG);
        assertThat(e.getQuestions().get(0).getEnunciat())
                .isEqualTo("Mira la xarxa: ![Topologia](fitxer:" + qf.getId() + ") i respon.");
    }

    @Test
    void la_mateixa_imatge_dues_vegades_a_la_mateixa_pregunta_es_baixa_i_es_desa_un_cop() throws Exception {
        Exam e = examen("![a](" + URL + ") i després ![b](" + URL + ")");

        service.incorpora(e);

        verify(descarregador, times(1)).descarrega(eq(URL), anyLong());
        verify(fileRepository, times(1)).save(any());
        String enunciat = e.getQuestions().get(0).getEnunciat();
        assertThat(enunciat).doesNotContain("http").contains("![a](fitxer:").contains("![b](fitxer:");
    }

    @Test
    void la_mateixa_url_a_dues_preguntes_es_baixa_un_cop_pero_cada_pregunta_te_el_seu_fitxer() throws Exception {
        Exam e = examen("![](" + URL + ")", "![](" + URL + ")");

        service.incorpora(e);

        verify(descarregador, times(1)).descarrega(eq(URL), anyLong());
        verify(fileRepository, times(2)).save(any());
        assertThat(fitxersAlDisc()).isEqualTo(2);
    }

    @Test
    void no_toca_les_imatges_dins_de_codi() {
        String text = "Exemple: `![x](https://a.cat/x.png)` i\n```\n![y](https://a.cat/y.png)\n```";
        Exam e = examen(text);

        service.incorpora(e);

        assertThat(e.getQuestions().get(0).getEnunciat()).isEqualTo(text);
        verify(descarregador, never()).descarrega(any(), anyLong());
    }

    @Test
    void deixa_igual_les_referencies_que_ja_son_fitxers() {
        String text = "![](fitxer:" + UUID.randomUUID() + ")";
        Exam e = examen(text);

        service.incorpora(e);

        assertThat(e.getQuestions().get(0).getEnunciat()).isEqualTo(text);
        verify(descarregador, never()).descarrega(any(), anyLong());
    }

    @Test
    void admet_imatges_en_base64() throws Exception {
        String data = "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG);
        Exam e = examen("![incrustada](" + data + ")");

        service.incorpora(e);

        verify(descarregador, never()).descarrega(any(), anyLong());
        assertThat(e.getQuestions().get(0).getEnunciat()).startsWith("![incrustada](fitxer:");
        assertThat(fitxersAlDisc()).isEqualTo(1);
    }

    @Test
    void rebutja_una_ruta_local_amb_el_numero_de_pregunta() {
        Exam e = examen("sense imatge", "![](img/xarxa.png)");

        assertThatThrownBy(() -> service.incorpora(e))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pregunta 2")
                .hasMessageContaining("img/xarxa.png")
                .hasMessageContaining("editor");
    }

    @Test
    void rebutja_el_que_no_es_una_imatge_encara_que_acabi_en_png() {
        when(descarregador.descarrega(any(), anyLong())).thenReturn("<html>no sóc una imatge</html>".getBytes());
        Exam e = examen("![](" + URL + ")");

        assertThatThrownBy(() -> service.incorpora(e))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no és una imatge");
    }

    @Test
    void si_la_baixada_falla_el_missatge_porta_la_url_i_el_motiu() {
        when(descarregador.descarrega(any(), anyLong())).thenThrow(new IllegalArgumentException("el servidor ha respost 404"));
        Exam e = examen("![](" + URL + ")");

        assertThatThrownBy(() -> service.incorpora(e))
                .hasMessageContaining(URL)
                .hasMessageContaining("404");
    }

    @Test
    void si_una_imatge_falla_s_esborren_les_que_ja_s_havien_escrit() throws Exception {
        String altra = "https://exemple.cat/falla.png";
        when(descarregador.descarrega(eq(altra), anyLong())).thenThrow(new IllegalArgumentException("el servidor ha respost 500"));
        Exam e = examen("![](" + URL + ")", "![](" + altra + ")");

        assertThatThrownBy(() -> service.incorpora(e)).isInstanceOf(IllegalArgumentException.class);

        assertThat(fitxersAlDisc()).isZero();
    }

    @Test
    void limita_el_nombre_d_imatges_per_importacio() {
        ReflectionTestUtils.setField(service, "maximImatges", 2);
        Exam e = examen("![](https://a.cat/1.png) ![](https://a.cat/2.png) ![](https://a.cat/3.png)");

        assertThatThrownBy(() -> service.incorpora(e))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("més de 2 imatges");
    }

    @Test
    void reconeix_cada_format_per_la_signatura() {
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] gif = {'G', 'I', 'F', '8', '9', 'a', 0, 0, 0, 0, 0, 0};
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

        assertThat(ImportadorImatgesService.extensioPerSignatura(PNG)).isEqualTo("png");
        assertThat(ImportadorImatgesService.extensioPerSignatura(jpg)).isEqualTo("jpg");
        assertThat(ImportadorImatgesService.extensioPerSignatura(gif)).isEqualTo("gif");
        assertThat(ImportadorImatgesService.extensioPerSignatura(webp)).isEqualTo("webp");
        assertThat(ImportadorImatgesService.extensioPerSignatura(new byte[20])).isNull();
        assertThat(ImportadorImatgesService.extensioPerSignatura(null)).isNull();
    }
}
