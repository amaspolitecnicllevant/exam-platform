package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.AnswerDto;
import com.examplatform.infrastructure.persistence.AnswerRepository;
import com.examplatform.infrastructure.storage.FitxersRespostaStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FitxerRespostaServiceTest {

    private static final long MAXIM = 1000;
    private static final byte[] ZIP = {'P', 'K', 3, 4, 'd', 'a', 'd', 'e', 's'};

    @Mock SessionService sessionService;
    @Mock AnswerRepository answerRepository;
    @Mock ConfiguracioService configuracioService;
    @Mock ExamService examService;
    @TempDir Path tmp;

    FitxersRespostaStorage storage;
    FitxerRespostaService service;
    ConfiguracioSistema config;

    User alumne;
    Exam exam;
    ExamSession sessio;
    Question pregunta;
    Answer resposta;

    @BeforeEach
    void setUp() {
        storage = new FitxersRespostaStorage(tmp.toString());
        service = new FitxerRespostaService(sessionService, answerRepository, configuracioService,
                storage, examService, MAXIM);
        config = new ConfiguracioSistema();
        lenient().when(configuracioService.get()).thenReturn(config);

        alumne = User.builder().id(UUID.randomUUID()).name("Alumne").email("a@x.cat").role(Role.STUDENT).build();
        exam = Exam.builder().id(UUID.randomUUID()).title("Pràctica").build();
        sessio = ExamSession.builder().id(UUID.randomUUID()).exam(exam).student(alumne).build();
        pregunta = Question.builder().id(UUID.randomUUID()).exam(exam).tipus(QuestionType.FILE_UPLOAD)
                .formatsPermesos("docx,pkt").build();
        resposta = Answer.builder().id(UUID.randomUUID()).session(sessio).question(pregunta).build();

        lenient().when(sessionService.respostaPerEscriure(eq(sessio.getId()), eq(pregunta.getId()), eq(alumne), anyString()))
                .thenReturn(resposta);
        lenient().when(answerRepository.save(any(Answer.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private MockMultipartFile fitxer(String nom, byte[] contingut) {
        return new MockMultipartFile("file", nom, "application/octet-stream", contingut);
    }

    private AnswerDto puja(MockMultipartFile f) throws Exception {
        return service.puja(sessio.getId(), pregunta.getId(), f, alumne, "10.0.0.5");
    }

    private long fitxersAlDisc() throws Exception {
        Path dir = tmp.resolve("answers");
        if (!Files.exists(dir)) return 0;
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).count();
        }
    }

    // ── puja: camí correcte ───────────────────────────────────────────────────

    @Test
    void puja_un_docx_valid_el_desa_i_marca_la_pregunta_com_contestada() throws Exception {
        AnswerDto dto = puja(fitxer("Informe final.docx", ZIP));

        assertThat(dto.fitxerNom()).isEqualTo("Informe final.docx");
        assertThat(dto.fitxerMida()).isEqualTo(ZIP.length);
        assertThat(resposta.getContingut()).isEqualTo("Informe final.docx");
        assertThat(resposta.getFitxerSha256()).hasSize(64);
        assertThat(resposta.getFitxerPujatEl()).isNotNull();
        assertThat(Files.readAllBytes(Path.of(resposta.getFitxerRuta()))).isEqualTo(ZIP);
        verify(answerRepository).save(resposta);
    }

    @Test
    void puja_un_fitxer_de_packet_tracer_sense_signatura() throws Exception {
        AnswerDto dto = puja(fitxer("xarxa.PKT", new byte[]{1, 2, 3, 4, 5}));

        assertThat(dto.fitxerNom()).isEqualTo("xarxa.PKT");
        assertThat(fitxersAlDisc()).isEqualTo(1);
    }

    @Test
    void el_nom_del_disc_no_depen_del_que_envia_l_alumne() throws Exception {
        puja(fitxer("../../etc/passwd.docx", ZIP));

        Path ruta = Path.of(resposta.getFitxerRuta());
        assertThat(ruta.startsWith(tmp.resolve("answers"))).isTrue();
        assertThat(ruta.getFileName().toString()).doesNotContain("passwd").doesNotContain("..");
        assertThat(resposta.getFitxerNom()).isEqualTo("passwd.docx");
    }

    @Test
    void sense_llista_de_formats_admet_qualsevol_dels_permesos() throws Exception {
        pregunta.setFormatsPermesos(null);

        puja(fitxer("full.pdf", "%PDF-1.7 contingut".getBytes(StandardCharsets.ISO_8859_1)));

        assertThat(resposta.getFitxerNom()).isEqualTo("full.pdf");
    }

    @Test
    void pujar_un_fitxer_nou_substitueix_l_anterior_i_n_esborra_el_del_disc() throws Exception {
        puja(fitxer("v1.docx", ZIP));
        Path primer = Path.of(resposta.getFitxerRuta());

        puja(fitxer("v2.docx", ZIP));

        assertThat(resposta.getFitxerNom()).isEqualTo("v2.docx");
        assertThat(Files.exists(primer)).isFalse();
        assertThat(fitxersAlDisc()).isEqualTo(1);
    }

    // ── puja: rebutjos ────────────────────────────────────────────────────────

    @Test
    void rebutja_un_format_que_la_pregunta_no_admet() throws Exception {
        assertThatThrownBy(() -> puja(fitxer("full.xlsx", ZIP)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".docx").hasMessageContaining(".pkt");

        assertThat(fitxersAlDisc()).isZero();
        verify(answerRepository, never()).save(any());
    }

    @Test
    void rebutja_un_executable_encara_que_se_li_canvii_el_nom() throws Exception {
        assertThatThrownBy(() -> puja(fitxer("virus.exe", new byte[]{'M', 'Z', 0, 0})))
                .isInstanceOf(IllegalArgumentException.class);
        // Un executable amb nom .docx: l'extensió passa però el contingut no és un ZIP
        assertThatThrownBy(() -> puja(fitxer("virus.docx", new byte[]{'M', 'Z', 0, 0, 1, 2})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no correspon a un fitxer .docx");

        assertThat(fitxersAlDisc()).isZero();
        assertThat(resposta.teFitxer()).isFalse();
    }

    @Test
    void rebutja_un_fitxer_sense_extensio() throws Exception {
        assertThatThrownBy(() -> puja(fitxer("informe", ZIP))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rebutja_un_fitxer_buit() throws Exception {
        assertThatThrownBy(() -> puja(fitxer("buit.docx", new byte[0])))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("buit");
    }

    @Test
    void rebutja_un_fitxer_massa_gran_sense_deixar_res_al_disc() throws Exception {
        byte[] gran = new byte[(int) MAXIM + 1];
        System.arraycopy(ZIP, 0, gran, 0, ZIP.length);

        assertThatThrownBy(() -> puja(fitxer("gran.docx", gran)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mida màxima");

        assertThat(fitxersAlDisc()).isZero();
        verify(answerRepository, never()).save(any());
    }

    @Test
    void un_rebuig_no_toca_el_fitxer_anterior() throws Exception {
        puja(fitxer("bo.docx", ZIP));
        Path bo = Path.of(resposta.getFitxerRuta());

        assertThatThrownBy(() -> puja(fitxer("dolent.docx", new byte[]{1, 2, 3, 4, 5}))).isInstanceOf(IllegalArgumentException.class);

        assertThat(Files.exists(bo)).isTrue();
        assertThat(resposta.getFitxerNom()).isEqualTo("bo.docx");
        assertThat(fitxersAlDisc()).isEqualTo(1);
    }

    @Test
    void rebutja_pujar_a_una_pregunta_que_no_es_de_fitxer() throws Exception {
        pregunta.setTipus(QuestionType.SHORT);

        assertThatThrownBy(() -> puja(fitxer("a.docx", ZIP)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no és de lliurament");
        assertThat(fitxersAlDisc()).isZero();
    }

    @Test
    void amb_la_pujada_desactivada_no_es_pot_pujar_res_ni_es_consulta_la_sessio() throws Exception {
        config.setPujadaFitxersActiva(false);

        assertThatThrownBy(() -> puja(fitxer("a.docx", ZIP)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("desactivada");

        verifyNoInteractions(sessionService);
        assertThat(fitxersAlDisc()).isZero();
    }

    @Test
    void les_regles_de_la_sessio_les_decideix_SessionService_i_es_propaguen() throws Exception {
        when(sessionService.respostaPerEscriure(any(), any(), any(), anyString()))
                .thenThrow(new IllegalStateException("La sessió ja ha estat enviada"));

        assertThatThrownBy(() -> puja(fitxer("a.docx", ZIP)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("enviada");

        assertThat(fitxersAlDisc()).isZero();
        verify(answerRepository, never()).save(any());
    }

    // ── esborra ───────────────────────────────────────────────────────────────

    @Test
    void esborra_treu_el_fitxer_del_disc_i_buida_la_resposta() throws Exception {
        puja(fitxer("a.docx", ZIP));
        Path ruta = Path.of(resposta.getFitxerRuta());

        AnswerDto dto = service.esborra(sessio.getId(), pregunta.getId(), alumne, "10.0.0.5");

        assertThat(dto.fitxerNom()).isNull();
        assertThat(Files.exists(ruta)).isFalse();
        assertThat(resposta.teFitxer()).isFalse();
        assertThat(resposta.getContingut()).isNull();
    }

    @Test
    void esborra_sense_fitxer_dona_no_trobat() {
        assertThatThrownBy(() -> service.esborra(sessio.getId(), pregunta.getId(), alumne, "10.0.0.5"))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── descarrega ────────────────────────────────────────────────────────────

    private void ambFitxerPujat() throws Exception {
        puja(fitxer("Treball.docx", ZIP));
        when(answerRepository.findBySessionIdAndQuestionId(sessio.getId(), pregunta.getId()))
                .thenReturn(Optional.of(resposta));
    }

    @Test
    void l_alumne_propietari_pot_descarregar_el_seu_fitxer() throws Exception {
        ambFitxerPujat();

        var d = service.descarrega(sessio.getId(), pregunta.getId(), alumne);

        assertThat(d.nom()).isEqualTo("Treball.docx");
        assertThat(d.recurs().contentLength()).isEqualTo(ZIP.length);
    }

    @Test
    void un_altre_alumne_no_pot_descarregar_el_fitxer() throws Exception {
        ambFitxerPujat();
        User altre = User.builder().id(UUID.randomUUID()).role(Role.STUDENT).build();

        assertThatThrownBy(() -> service.descarrega(sessio.getId(), pregunta.getId(), altre))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void qui_gestiona_l_examen_pot_descarregar_el_fitxer() throws Exception {
        ambFitxerPujat();
        User prof = User.builder().id(UUID.randomUUID()).role(Role.PROFESSOR).build();
        when(examService.potGestionar(exam, prof)).thenReturn(true);

        assertThat(service.descarrega(sessio.getId(), pregunta.getId(), prof).nom()).isEqualTo("Treball.docx");
    }

    @Test
    void un_professor_que_no_gestiona_l_examen_no_pot_descarregar_el_fitxer() throws Exception {
        ambFitxerPujat();
        User prof = User.builder().id(UUID.randomUUID()).role(Role.PROFESSOR).build();
        when(examService.potGestionar(exam, prof)).thenReturn(false);

        assertThatThrownBy(() -> service.descarrega(sessio.getId(), pregunta.getId(), prof))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void descarregar_sense_resposta_o_sense_fitxer_dona_no_trobat() {
        when(answerRepository.findBySessionIdAndQuestionId(any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.descarrega(sessio.getId(), pregunta.getId(), alumne))
                .isInstanceOf(NoSuchElementException.class);

        when(answerRepository.findBySessionIdAndQuestionId(any(), any())).thenReturn(Optional.of(resposta));
        assertThatThrownBy(() -> service.descarrega(sessio.getId(), pregunta.getId(), alumne))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void si_el_fitxer_ja_no_es_al_disc_s_avisa_clarament() throws Exception {
        ambFitxerPujat();
        Files.delete(Path.of(resposta.getFitxerRuta()));

        assertThatThrownBy(() -> service.descarrega(sessio.getId(), pregunta.getId(), alumne))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ja no està disponible");
    }

    // ── nom net ───────────────────────────────────────────────────────────────

    @Test
    void nomNet_treu_directoris_i_caracters_de_control() {
        assertThat(FitxerRespostaService.nomNet("C:\\Users\\Pere\\Documents\\treball.docx")).isEqualTo("treball.docx");
        assertThat(FitxerRespostaService.nomNet("/tmp/../etc/x.docx")).isEqualTo("x.docx");
        assertThat(FitxerRespostaService.nomNet("a\u0000b\nc.docx")).isEqualTo("abc.docx");
        assertThat(FitxerRespostaService.nomNet(null)).isEqualTo("fitxer");
        assertThat(FitxerRespostaService.nomNet("   ")).isEqualTo("fitxer");
    }

    @Test
    void nomNet_retalla_noms_llarguissims_conservant_l_extensio() {
        String nom = FitxerRespostaService.nomNet("a".repeat(500) + ".xlsx");

        assertThat(nom.length()).isLessThanOrEqualTo(200);
        assertThat(nom).endsWith(".xlsx");
    }
}
