package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.domain.port.ScriptExecutor;
import com.examplatform.domain.port.ScriptExecutor.ExecutionResult;
import com.examplatform.dto.AnswerDto;
import com.examplatform.dto.ExecutionResultDto;
import com.examplatform.infrastructure.persistence.AnswerRepository;
import com.examplatform.infrastructure.persistence.ExecutionRepository;
import com.examplatform.infrastructure.persistence.QuestionFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests unitaris de {@link CorrectionService}: execució de respostes i auto-correcció
 * (test-script, output-exact, output-contains, output-regex, sense criteri) i puntuació manual.
 */
@ExtendWith(MockitoExtension.class)
class CorrectionServiceTest {

    private static final String TIMEOUT_OUTPUT =
            "resultat parcial\n[Timeout: execució cancel·lada després de 10s]";

    @Mock AnswerRepository answerRepo;
    @Mock ExecutionRepository executionRepo;
    @Mock ScriptExecutor executor;
    @Mock QuestionFileRepository questionFileRepo;
    @Mock ExamService examService;

    CorrectionService service;

    private static final User PROFESSOR = User.builder().id(UUID.randomUUID()).role(Role.PROFESSOR).build();

    @BeforeEach
    void setUp() {
        service = new CorrectionService(answerRepo, executionRepo, executor, questionFileRepo, examService,
                new org.springframework.transaction.support.TransactionTemplate(
                        mock(org.springframework.transaction.PlatformTransactionManager.class)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Question question(QuestionType type, String punts) {
        return Question.builder()
                .id(UUID.randomUUID())
                .tipus(type)
                .punts(new BigDecimal(punts))
                .build();
    }

    private Answer answer(Question q, String contingut) {
        ExamSession session = ExamSession.builder()
                .id(UUID.randomUUID())
                .student(User.builder().id(UUID.randomUUID()).build())
                .build();
        Answer a = Answer.builder()
                .id(UUID.randomUUID())
                .question(q)
                .session(session)
                .contingut(contingut)
                .build();
        when(answerRepo.findById(a.getId())).thenReturn(Optional.of(a));
        return a;
    }

    /** Resposta executable amb els mocks de persistència preparats. */
    private Answer executableAnswer(Question q, String contingut) {
        Answer a = answer(q, contingut);
        when(questionFileRepo.findByQuestionId(q.getId())).thenReturn(List.of());
        when(answerRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(executionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return a;
    }

    private void executorReturns(String output, int exitCode) {
        when(executor.execute(any(), anyString(), anyList()))
                .thenReturn(new ExecutionResult(output, exitCode, 42));
    }

    private void executorWithTestReturns(String output, int exitCode) {
        when(executor.executeWithTest(any(), anyString(), anyString(), anyList()))
                .thenReturn(new ExecutionResult(output, exitCode, 42));
    }

    private static void assertScore(Answer a, String expected) {
        assertThat(a.getAutoScore()).isEqualByComparingTo(expected);
    }

    // ── execute: validacions i flux ──────────────────────────────────────────

    @Nested
    class Execute {

        @Test
        void resposta_inexistent_llanca_NoSuchElement() {
            UUID id = UUID.randomUUID();
            when(answerRepo.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.execute(id))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining(id.toString());
        }

        @ParameterizedTest
        @EnumSource(value = QuestionType.class, names = {"TEXT", "SHORT", "LONG", "CHOICE", "HTML_CSS", "SECTION"})
        void pregunta_no_executable_llanca_IllegalArgument_i_no_executa(QuestionType type) {
            Answer a = answer(question(type, "1"), "qualsevol cosa");

            assertThatThrownBy(() -> service.execute(a.getId()))
                    .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(executor, executionRepo);
            verify(answerRepo, never()).save(any());
        }

        @Test
        void resposta_null_no_executa_ni_persisteix() {
            Answer a = answer(question(QuestionType.BASH_CMD, "5"), null);

            ExecutionResultDto result = service.execute(a.getId());

            assertThat(result.output()).isEqualTo("(sense resposta)");
            assertThat(result.exitCode()).isEqualTo(-1);
            assertThat(result.succeeded()).isFalse();
            verifyNoInteractions(executor, executionRepo);
            verify(answerRepo, never()).save(any());
            assertThat(a.getAutoScore()).isNull();
        }

        @Test
        void resposta_en_blanc_no_executa() {
            Answer a = answer(question(QuestionType.BASH_CMD, "5"), "  \n\t ");

            ExecutionResultDto result = service.execute(a.getId());

            assertThat(result.exitCode()).isEqualTo(-1);
            verifyNoInteractions(executor);
        }

        @Test
        void persisteix_execucio_i_actualitza_la_resposta() {
            Question q = question(QuestionType.BASH_CMD, "2");
            Answer a = executableAnswer(q, "echo hola");
            when(executor.execute(any(), anyString(), anyList()))
                    .thenReturn(new ExecutionResult("hola", 0, 123));

            ExecutionResultDto result = service.execute(a.getId());

            ArgumentCaptor<Execution> exec = ArgumentCaptor.forClass(Execution.class);
            verify(executionRepo).save(exec.capture());
            assertThat(exec.getValue().getAnswer()).isSameAs(a);
            assertThat(exec.getValue().getOutput()).isEqualTo("hola");
            assertThat(exec.getValue().getExitCode()).isZero();
            assertThat(exec.getValue().getDurationMs()).isEqualTo(123L);

            verify(answerRepo).save(a);
            assertThat(a.getExecutionOutput()).isEqualTo("hola");
            assertThat(result).isEqualTo(new ExecutionResultDto("hola", 0, 123, true));
        }

        @Test
        void passa_els_fitxers_de_dades_de_la_pregunta_a_l_executor() {
            Question q = question(QuestionType.BASH_CMD, "1");
            Answer a = answer(q, "cat dades.csv");
            when(answerRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(executionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(questionFileRepo.findByQuestionId(q.getId())).thenReturn(List.of(
                    QuestionFile.builder().storedPath("/opt/exam-files/a/dades.csv").build(),
                    QuestionFile.builder().storedPath("/opt/exam-files/a/extra.txt").build()));
            executorReturns("ok", 0);

            service.execute(a.getId());

            verify(executor).execute(QuestionType.BASH_CMD, "cat dades.csv", List.of(
                    Path.of("/opt/exam-files/a/dades.csv"),
                    Path.of("/opt/exam-files/a/extra.txt")));
        }

        @ParameterizedTest
        @EnumSource(value = QuestionType.class, names = {"BASH_SCRIPT", "PS_SCRIPT"})
        void script_amb_test_script_usa_executeWithTest(QuestionType type) {
            Question q = question(type, "1");
            q.setTestScript("test -f out.txt");
            Answer a = executableAnswer(q, "touch out.txt");
            executorWithTestReturns("", 0);

            service.execute(a.getId());

            verify(executor).executeWithTest(type, "touch out.txt", "test -f out.txt", List.of());
            verify(executor, never()).execute(any(), anyString(), anyList());
        }

        @ParameterizedTest
        @EnumSource(value = QuestionType.class, names = {"BASH_CMD", "PS_CMD", "JAVA_PROG"})
        void test_script_en_tipus_no_script_s_ignora_per_executar(QuestionType type) {
            Question q = question(type, "1");
            q.setTestScript("exit 0");
            Answer a = executableAnswer(q, "codi");
            executorReturns("", 0);

            service.execute(a.getId());

            verify(executor).execute(eq(type), eq("codi"), anyList());
            verify(executor, never()).executeWithTest(any(), anyString(), anyString(), anyList());
        }

        @Test
        void script_sense_test_script_usa_execute_normal() {
            Question q = question(QuestionType.BASH_SCRIPT, "1");
            Answer a = executableAnswer(q, "echo ok");
            executorReturns("ok", 0);

            service.execute(a.getId());

            verify(executor).execute(eq(QuestionType.BASH_SCRIPT), eq("echo ok"), anyList());
            verify(executor, never()).executeWithTest(any(), anyString(), anyString(), anyList());
        }

        @Test
        void re_executar_sobreescriu_la_nota_automatica_anterior() {
            Question q = question(QuestionType.BASH_CMD, "3");
            q.setOutputExact("ok");
            Answer a = executableAnswer(q, "echo ok");
            a.setAutoScore(new BigDecimal("3"));
            executorReturns("ko", 0);

            service.execute(a.getId());

            assertScore(a, "0");
        }
    }

    // ── auto-correcció: test-script ──────────────────────────────────────────

    @Nested
    class TestScript {

        @Test
        void exit0_dona_punts_maxims() {
            Question q = question(QuestionType.BASH_SCRIPT, "6");
            q.setTestScript("exit 0");
            Answer a = executableAnswer(q, "#!/bin/bash\necho ok");
            executorWithTestReturns("ok", 0);

            service.execute(a.getId());

            assertScore(a, "6");
        }

        @Test
        void exit_no_zero_dona_zero() {
            Question q = question(QuestionType.BASH_SCRIPT, "6");
            q.setTestScript("exit 1");
            Answer a = executableAnswer(q, "#!/bin/bash\necho malament");
            executorWithTestReturns("", 1);

            service.execute(a.getId());

            assertScore(a, "0");
        }

        @Test
        void timeout_dona_zero() {
            Question q = question(QuestionType.BASH_SCRIPT, "6");
            q.setTestScript("./check.sh");
            Answer a = executableAnswer(q, "while true; do :; done");
            executorWithTestReturns(TIMEOUT_OUTPUT, -1);

            service.execute(a.getId());

            assertScore(a, "0");
        }

        @Test
        void error_llancant_contenidor_dona_zero() {
            Question q = question(QuestionType.BASH_SCRIPT, "6");
            q.setTestScript("./check.sh");
            Answer a = executableAnswer(q, "echo ok");
            executorWithTestReturns("Error llançant contenidor: docker no trobat", -1);

            service.execute(a.getId());

            assertScore(a, "0");
        }

        @Test
        void te_prioritat_sobre_els_criteris_de_sortida() {
            Question q = question(QuestionType.BASH_SCRIPT, "6");
            q.setTestScript("exit 1");
            q.setOutputExact("ok");
            Answer a = executableAnswer(q, "echo ok");
            // la sortida coincideix, però el test falla → mana el test
            executorWithTestReturns("ok", 1);

            service.execute(a.getId());

            assertScore(a, "0");
        }

        @Test
        void en_tipus_no_script_puntua_per_exit_code() {
            Question q = question(QuestionType.JAVA_PROG, "4");
            q.setTestScript("exit 0");
            q.setOutputExact("no s'avalua");
            Answer a = executableAnswer(q, "class Main {}");
            executorReturns("sortida qualsevol", 0);

            service.execute(a.getId());

            assertScore(a, "4");
        }
    }

    // ── auto-correcció: output-exact ─────────────────────────────────────────

    @Nested
    class OutputExact {

        @Test
        void coincidencia_ignora_majuscules() {
            Question q = question(QuestionType.BASH_CMD, "3");
            q.setOutputExact("Hola Món");
            Answer a = executableAnswer(q, "echo 'hola món'");
            executorReturns("hola món", 0);

            service.execute(a.getId());

            assertScore(a, "3");
        }

        @Test
        void ignora_espais_i_salts_de_linia_als_extrems() {
            Question q = question(QuestionType.BASH_CMD, "3");
            q.setOutputExact("  resultat \n");
            Answer a = executableAnswer(q, "echo resultat");
            executorReturns("\n resultat\n\n", 0);

            service.execute(a.getId());

            assertScore(a, "3");
        }

        @Test
        void sortida_diferent_dona_zero() {
            Question q = question(QuestionType.BASH_CMD, "3");
            q.setOutputExact("esperada");
            Answer a = executableAnswer(q, "echo 'altra cosa'");
            executorReturns("altra cosa", 0);

            service.execute(a.getId());

            assertScore(a, "0");
        }

        @Test
        void sortida_que_nomes_conte_l_esperada_dona_zero() {
            Question q = question(QuestionType.BASH_CMD, "3");
            q.setOutputExact("42");
            Answer a = executableAnswer(q, "echo 'valor: 42'");
            executorReturns("valor: 42", 0);

            service.execute(a.getId());

            assertScore(a, "0");
        }

        @Test
        void espais_interns_han_de_coincidir() {
            Question q = question(QuestionType.BASH_CMD, "3");
            q.setOutputExact("a b");
            Answer a = executableAnswer(q, "echo 'a  b'");
            executorReturns("a  b", 0);

            service.execute(a.getId());

            assertScore(a, "0");
        }

        @Test
        void sortida_null_es_tracta_com_a_buida() {
            Question q = question(QuestionType.BASH_CMD, "3");
            q.setOutputExact("algo");
            Answer a = executableAnswer(q, "true");
            executorReturns(null, 0);

            service.execute(a.getId());

            assertScore(a, "0");
        }

        @Test
        void sortida_de_timeout_no_coincideix() {
            Question q = question(QuestionType.BASH_CMD, "3");
            q.setOutputExact("resultat parcial");
            Answer a = executableAnswer(q, "sleep 999");
            executorReturns(TIMEOUT_OUTPUT, -1);

            service.execute(a.getId());

            assertScore(a, "0");
        }

        @Test
        void sortida_correcta_amb_exit_no_zero_dona_zero() {
            Question q = question(QuestionType.BASH_CMD, "3");
            q.setOutputExact("ok");
            Answer a = executableAnswer(q, "echo ok; exit 2");
            executorReturns("ok", 2);

            service.execute(a.getId());

            assertScore(a, "0");
        }

        @Test
        void sortida_buida_esperada_i_exit0_dona_punts() {
            Question q = question(QuestionType.BASH_CMD, "3");
            q.setOutputExact("");
            Answer a = executableAnswer(q, "true");
            executorReturns("", 0);

            service.execute(a.getId());

            assertScore(a, "3");
        }

        @Test
        void te_prioritat_sobre_contains_i_regex() {
            Question q = question(QuestionType.BASH_CMD, "3");
            q.setOutputExact("exacte");
            q.setOutputContains("xacte");
            q.setOutputRegex("exac");
            Answer a = executableAnswer(q, "echo 'exacte i més'");
            executorReturns("exacte i més", 0);

            service.execute(a.getId());

            assertScore(a, "0");
        }
    }

    // ── auto-correcció: output-contains ──────────────────────────────────────

    @Nested
    class OutputContains {

        @Test
        void totes_les_linies_presents_dona_punts() {
            Question q = question(QuestionType.BASH_CMD, "4");
            q.setOutputContains("eth0\ninet");
            Answer a = executableAnswer(q, "ip addr");
            executorReturns("2: eth0: <UP>\n    inet 192.168.1.1", 0);

            service.execute(a.getId());

            assertScore(a, "4");
        }

        @Test
        void falta_una_linia_resta_la_seva_part_i_ho_explica() {
            Question q = question(QuestionType.BASH_CMD, "4");
            q.setOutputContains("eth0\nwlan0");
            Answer a = executableAnswer(q, "ip addr");
            executorReturns("2: eth0: <UP>", 0);

            service.execute(a.getId());

            assertScore(a, "2");
            assertThat(a.getAutoFeedback()).isEqualTo("−2: a la sortida hi falta «wlan0»");
        }

        @Test
        void nota_proporcional_arrodonida_a_dos_decimals() {
            Question q = question(QuestionType.BASH_CMD, "1");
            q.setOutputContains("a\nb\nc");
            Answer a = executableAnswer(q, "echo a b");
            executorReturns("a b", 0);

            service.execute(a.getId());

            assertScore(a, "0.67");
            assertThat(a.getAutoFeedback()).isEqualTo("−0,33: a la sortida hi falta «c»");
        }

        @Test
        void cap_linia_present_dona_zero_amb_un_motiu_per_linia() {
            Question q = question(QuestionType.BASH_CMD, "4");
            q.setOutputContains("eth0\nwlan0");
            Answer a = executableAnswer(q, "ip addr");
            executorReturns("lo", 0);

            service.execute(a.getId());

            assertScore(a, "0");
            assertThat(a.getAutoFeedback()).isEqualTo(
                    "−2: a la sortida hi falta «eth0»\n−2: a la sortida hi falta «wlan0»");
        }

        @Test
        void ignora_majuscules_i_espais_de_les_linies_esperades() {
            Question q = question(QuestionType.BASH_CMD, "4");
            q.setOutputContains("  ETH0  \n\tInet\t");
            Answer a = executableAnswer(q, "ip addr");
            executorReturns("eth0 up\ninet 10.0.0.1", 0);

            service.execute(a.getId());

            assertScore(a, "4");
        }

        @Test
        void ignora_linies_en_blanc_del_criteri() {
            Question q = question(QuestionType.BASH_CMD, "4");
            q.setOutputContains("\nalfa\n\n   \nbeta\n");
            Answer a = executableAnswer(q, "cat fitxer");
            executorReturns("alfa beta", 0);

            service.execute(a.getId());

            assertScore(a, "4");
        }

        @Test
        void l_ordre_de_les_linies_no_importa() {
            Question q = question(QuestionType.BASH_CMD, "4");
            q.setOutputContains("segon\nprimer");
            Answer a = executableAnswer(q, "echo primer segon");
            executorReturns("primer\nsegon", 0);

            service.execute(a.getId());

            assertScore(a, "4");
        }

        @Test
        void sortida_null_dona_zero() {
            Question q = question(QuestionType.BASH_CMD, "4");
            q.setOutputContains("algo");
            Answer a = executableAnswer(q, "true");
            executorReturns(null, 0);

            service.execute(a.getId());

            assertScore(a, "0");
        }

        @Test
        void criteri_nomes_amb_linies_en_blanc_s_ignora_i_falla_si_exit_no_zero() {
            Question q = question(QuestionType.BASH_CMD, "4");
            q.setOutputContains("\n   \n");
            Answer a = executableAnswer(q, "false");
            executorReturns("", 1);

            service.execute(a.getId());

            assertScore(a, "0");
        }

        @Test
        void criteri_nomes_amb_linies_en_blanc_cau_al_seguent_criteri() {
            Question q = question(QuestionType.BASH_CMD, "4");
            q.setOutputContains("  ");
            q.setOutputRegex("^\\d+$");
            Answer a = executableAnswer(q, "echo 42");
            executorReturns("42", 0);

            service.execute(a.getId());

            assertScore(a, "4");
        }

        @Test
        void sortida_parcial_d_un_timeout_que_conte_el_text_dona_zero() {
            Question q = question(QuestionType.BASH_CMD, "4");
            q.setOutputContains("resultat parcial");
            Answer a = executableAnswer(q, "echo 'resultat parcial'; sleep 999");
            executorReturns(TIMEOUT_OUTPUT, -1);

            service.execute(a.getId());

            assertScore(a, "0");
        }

        @Test
        void sortida_correcta_amb_exit_no_zero_dona_zero() {
            Question q = question(QuestionType.BASH_CMD, "4");
            q.setOutputContains("eth0");
            Answer a = executableAnswer(q, "ip addr; exit 1");
            executorReturns("eth0", 1);

            service.execute(a.getId());

            assertScore(a, "0");
        }

        @Test
        void te_prioritat_sobre_regex() {
            Question q = question(QuestionType.BASH_CMD, "4");
            q.setOutputContains("absent");
            q.setOutputRegex(".*");
            Answer a = executableAnswer(q, "echo res");
            executorReturns("res", 0);

            service.execute(a.getId());

            assertScore(a, "0");
        }
    }

    // ── auto-correcció: output-regex ─────────────────────────────────────────

    @Nested
    class OutputRegex {

        @Test
        void match_dona_punts() {
            Question q = question(QuestionType.BASH_CMD, "2");
            q.setOutputRegex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");
            Answer a = executableAnswer(q, "hostname -I");
            executorReturns("192.168.1.50", 0);

            service.execute(a.getId());

            assertScore(a, "2");
        }

        @Test
        void no_match_dona_zero() {
            Question q = question(QuestionType.BASH_CMD, "2");
            q.setOutputRegex("^\\d+$");
            Answer a = executableAnswer(q, "echo abc");
            executorReturns("abc", 0);

            service.execute(a.getId());

            assertScore(a, "0");
        }

        @Test
        void n_hi_ha_prou_amb_un_match_parcial() {
            Question q = question(QuestionType.BASH_CMD, "2");
            q.setOutputRegex("\\d+ fitxers");
            Answer a = executableAnswer(q, "ls | wc -l");
            executorReturns("Hi ha 12 fitxers al directori", 0);

            service.execute(a.getId());

            assertScore(a, "2");
        }

        @Test
        void mode_multilinia_permet_ancorar_linies_intermedies() {
            Question q = question(QuestionType.BASH_CMD, "2");
            q.setOutputRegex("^usuari: \\w+$");
            Answer a = executableAnswer(q, "cat /etc/info");
            executorReturns("capçalera\nusuari: toma\npeu", 0);

            service.execute(a.getId());

            assertScore(a, "2");
        }

        @Test
        void distingeix_majuscules() {
            Question q = question(QuestionType.BASH_CMD, "2");
            q.setOutputRegex("^OK$");
            Answer a = executableAnswer(q, "echo ok");
            executorReturns("ok", 0);

            service.execute(a.getId());

            assertScore(a, "0");
        }

        @Test
        void regex_invalida_dona_zero_sense_excepcio() {
            Question q = question(QuestionType.BASH_CMD, "2");
            q.setOutputRegex("([a-z");
            Answer a = executableAnswer(q, "echo abc");
            executorReturns("abc", 0);

            ExecutionResultDto result = service.execute(a.getId());

            assertScore(a, "0");
            assertThat(result.succeeded()).isTrue();
        }

        @Test
        void sortida_null_es_tracta_com_a_buida() {
            Question q = question(QuestionType.BASH_CMD, "2");
            // \A\z = entrada buida (en MULTILINE, "^$" no coincideix amb una cadena buida a Java)
            q.setOutputRegex("\\A\\z");
            Answer a = executableAnswer(q, "true");
            executorReturns(null, 0);

            service.execute(a.getId());

            assertScore(a, "2");
        }

        @Test
        void match_amb_exit_no_zero_dona_zero() {
            Question q = question(QuestionType.BASH_CMD, "2");
            q.setOutputRegex("\\d+");
            Answer a = executableAnswer(q, "echo 7; exit 3");
            executorReturns("7", 3);

            service.execute(a.getId());

            assertScore(a, "0");
        }

        @Test
        void match_sobre_sortida_de_timeout_dona_zero() {
            Question q = question(QuestionType.BASH_CMD, "2");
            q.setOutputRegex("resultat");
            Answer a = executableAnswer(q, "sleep 999");
            executorReturns(TIMEOUT_OUTPUT, -1);

            service.execute(a.getId());

            assertScore(a, "0");
        }

        @Test
        void regex_catastrofica_es_talla_pel_termini_i_dona_zero() {
            // (.*a){12}b sobre "aaa…a" provoca backtracking catastròfic al motor de Java
            Question q = question(QuestionType.BASH_CMD, "5");
            q.setOutputRegex("(.*a){12}b");
            Answer a = executableAnswer(q, "echo aaa");
            executorReturns("a".repeat(60), 0);

            long start = System.nanoTime();
            service.execute(a.getId());
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertScore(a, "0");
            assertThat(elapsedMs).isBetween(1_500L, 4_000L);
        }

        @Test
        void regex_catastrofica_no_deixa_cap_fil_executant_la_cerca() {
            Question q = question(QuestionType.BASH_CMD, "5");
            q.setOutputRegex("(.*a){12}b");
            Answer a = executableAnswer(q, "echo aaa");
            executorReturns("a".repeat(60), 0);

            service.execute(a.getId());

            assertThat(Thread.getAllStackTraces().values())
                    .noneMatch(stack -> java.util.Arrays.stream(stack)
                            .anyMatch(f -> f.getClassName().startsWith("java.util.regex.")));
        }
    }

    // ── auto-correcció: sense criteri ────────────────────────────────────────

    @Nested
    class SenseCriteri {

        @Test
        void exit0_dona_punts_maxims() {
            Question q = question(QuestionType.BASH_CMD, "5");
            Answer a = executableAnswer(q, "ls");
            executorReturns("fitxer.txt", 0);

            service.execute(a.getId());

            assertScore(a, "5");
        }

        @Test
        void exit_no_zero_dona_zero() {
            Question q = question(QuestionType.BASH_CMD, "5");
            Answer a = executableAnswer(q, "ls /inexistent");
            executorReturns("error", 1);

            service.execute(a.getId());

            assertScore(a, "0");
        }

        @Test
        void timeout_dona_zero() {
            Question q = question(QuestionType.JAVA_PROG, "5");
            Answer a = executableAnswer(q, "class Main { public static void main(String[] a) { while(true); } }");
            executorReturns(TIMEOUT_OUTPUT, -1);

            service.execute(a.getId());

            assertScore(a, "0");
        }

        @Test
        void punts_decimals_es_conserven() {
            Question q = question(QuestionType.PS_CMD, "0.75");
            Answer a = executableAnswer(q, "Get-Date");
            executorReturns("dilluns", 0);

            service.execute(a.getId());

            assertScore(a, "0.75");
        }
    }

    // ── motius de la proposta ────────────────────────────────────────────────

    @Nested
    class Motius {

        @Test
        void test_script_superat() {
            Question q = question(QuestionType.BASH_SCRIPT, "2");
            q.setTestScript("exit 0");
            Answer a = executableAnswer(q, "echo ok");
            executorWithTestReturns("ok", 0);

            service.execute(a.getId());

            assertThat(a.getAutoFeedback()).isEqualTo("✓ Supera el test de comprovació");
        }

        @Test
        void test_script_fallat_indica_exit_code() {
            Question q = question(QuestionType.BASH_SCRIPT, "2");
            q.setTestScript("exit 3");
            Answer a = executableAnswer(q, "echo ok");
            executorWithTestReturns("", 3);

            service.execute(a.getId());

            assertThat(a.getAutoFeedback()).isEqualTo("−2: no supera el test de comprovació (exit 3)");
        }

        @Test
        void test_script_amb_timeout() {
            Question q = question(QuestionType.BASH_SCRIPT, "2");
            q.setTestScript("./check.sh");
            Answer a = executableAnswer(q, "while :; do :; done");
            executorWithTestReturns(TIMEOUT_OUTPUT, -1);

            service.execute(a.getId());

            assertThat(a.getAutoFeedback()).isEqualTo("−2: temps d'execució esgotat");
        }

        @Test
        void criteri_de_sortida_amb_error_d_execucio() {
            Question q = question(QuestionType.BASH_CMD, "1.5");
            q.setOutputExact("ok");
            Answer a = executableAnswer(q, "echo ok; exit 2");
            executorReturns("ok", 2);

            service.execute(a.getId());

            assertThat(a.getAutoFeedback()).isEqualTo("−1,5: l'execució acaba amb error (exit 2)");
        }

        @Test
        void criteri_de_sortida_amb_timeout() {
            Question q = question(QuestionType.BASH_CMD, "1");
            q.setOutputContains("resultat parcial");
            Answer a = executableAnswer(q, "sleep 999");
            executorReturns(TIMEOUT_OUTPUT, -1);

            service.execute(a.getId());

            assertThat(a.getAutoFeedback()).isEqualTo("−1: temps d'execució esgotat");
        }

        @Test
        void output_exact_encert_i_error() {
            Question q = question(QuestionType.BASH_CMD, "1");
            q.setOutputExact("42");
            Answer a = executableAnswer(q, "echo 41");
            executorReturns("41", 0);

            service.execute(a.getId());

            assertThat(a.getAutoFeedback()).isEqualTo("−1: la sortida no coincideix amb l'esperada");
        }

        @Test
        void output_regex_error() {
            Question q = question(QuestionType.BASH_CMD, "1");
            q.setOutputRegex("^\\d+$");
            Answer a = executableAnswer(q, "echo abc");
            executorReturns("abc", 0);

            service.execute(a.getId());

            assertThat(a.getAutoFeedback()).isEqualTo("−1: la sortida no compleix el patró esperat");
        }

        @Test
        void sense_criteri_correcte_i_amb_error() {
            Question q = question(QuestionType.JAVA_PROG, "3");
            Answer a = executableAnswer(q, "class Main {}");
            executorReturns("error: compilació", 1);

            service.execute(a.getId());

            assertThat(a.getAutoFeedback()).isEqualTo("−3: l'execució acaba amb error (exit 1)");
        }
    }

    // ── proposta en entregar (sense execució) ────────────────────────────────

    @Nested
    class ProposaSenseExecucio {

        private ExamSession session;

        @BeforeEach
        void setUpSession() {
            session = ExamSession.builder().id(UUID.randomUUID()).build();
        }

        private Answer resposta(Question q, String contingut) {
            return Answer.builder().id(UUID.randomUUID()).session(session).question(q).contingut(contingut).build();
        }

        @Test
        void pregunta_curta_amb_claus_rep_proposta_i_motius() {
            Question q = question(QuestionType.SHORT, "2");
            q.setClaus("DHCP | 1.5\nDNS | 0.5");
            Answer a = resposta(q, "El servidor DHCP");
            when(answerRepo.findBySessionId(session.getId())).thenReturn(List.of(a));

            service.proposaSenseExecucio(session);

            assertScore(a, "1.5");
            assertThat(a.getAutoFeedback()).isEqualTo("−0,5: no esmenta «DNS»");
            verify(answerRepo).save(a);
        }

        @ParameterizedTest
        @EnumSource(value = QuestionType.class, names = {"TEXT", "LONG"})
        void tambe_per_a_preguntes_de_text_llargues(QuestionType tipus) {
            Question q = question(tipus, "1");
            q.setClaus("TCP");
            Answer a = resposta(q, "Usa TCP");
            when(answerRepo.findBySessionId(session.getId())).thenReturn(List.of(a));

            service.proposaSenseExecucio(session);

            assertScore(a, "1");
        }

        @Test
        void pregunta_de_text_sense_claus_queda_sense_proposta() {
            Question q = question(QuestionType.SHORT, "2");
            Answer a = resposta(q, "Qualsevol cosa");
            when(answerRepo.findBySessionId(session.getId())).thenReturn(List.of(a));

            service.proposaSenseExecucio(session);

            assertThat(a.getAutoScore()).isNull();
            verify(answerRepo, never()).save(any());
        }

        @Test
        void claus_invalides_no_trenquen_l_entrega() {
            Question q = question(QuestionType.SHORT, "2");
            q.setClaus("DHCP | 5");  // no sumen els punts (dades antigues)
            Answer a = resposta(q, "DHCP");
            when(answerRepo.findBySessionId(session.getId())).thenReturn(List.of(a));

            service.proposaSenseExecucio(session);

            assertThat(a.getAutoScore()).isNull();
        }

        @Test
        void pregunta_de_codi_sense_resposta_rep_zero() {
            Question q = question(QuestionType.BASH_CMD, "2");
            Answer a = resposta(q, "  ");
            when(answerRepo.findBySessionId(session.getId())).thenReturn(List.of(a));

            service.proposaSenseExecucio(session);

            assertScore(a, "0");
            assertThat(a.getAutoFeedback()).isEqualTo("Sense resposta");
            verifyNoInteractions(executor);
        }

        @Test
        void pregunta_de_codi_amb_resposta_no_s_executa_aqui() {
            Question q = question(QuestionType.BASH_CMD, "2");
            Answer a = resposta(q, "ls");
            when(answerRepo.findBySessionId(session.getId())).thenReturn(List.of(a));

            service.proposaSenseExecucio(session);

            assertThat(a.getAutoScore()).isNull();
            verifyNoInteractions(executor);
        }

        @Test
        void pregunta_anul_lada_s_ignora() {
            Question q = question(QuestionType.SHORT, "2");
            q.setClaus("DHCP");
            q.setAnulada(true);
            Answer a = resposta(q, "res");
            when(answerRepo.findBySessionId(session.getId())).thenReturn(List.of(a));

            service.proposaSenseExecucio(session);

            assertThat(a.getAutoScore()).isNull();
        }

        @ParameterizedTest
        @EnumSource(value = QuestionType.class, names = {"CHOICE", "HTML_CSS"})
        void altres_tipus_no_reben_proposta(QuestionType tipus) {
            Question q = question(tipus, "1");
            Answer a = resposta(q, "a");
            when(answerRepo.findBySessionId(session.getId())).thenReturn(List.of(a));

            service.proposaSenseExecucio(session);

            assertThat(a.getAutoScore()).isNull();
        }
    }

    // ── acceptar propostes ───────────────────────────────────────────────────

    @Nested
    class AcceptarPropostes {

        private final User professor = User.builder().id(UUID.randomUUID()).role(Role.PROFESSOR).build();
        private Exam exam;
        private ExamSession session;

        @BeforeEach
        void setUpExam() {
            exam = Exam.builder().id(UUID.randomUUID()).createdBy(professor).build();
            session = ExamSession.builder().id(UUID.randomUUID()).exam(exam).build();
        }

        private Answer ambProposta(ExamSession s, String punts, String proposta) {
            Answer a = Answer.builder().id(UUID.randomUUID()).session(s)
                    .question(question(QuestionType.SHORT, punts)).build();
            a.setAutoScore(proposta == null ? null : new BigDecimal(proposta));
            return a;
        }

        @Test
        void acceptar_una_proposta_la_converteix_en_nota_revisada() {
            Answer a = ambProposta(session, "2", "1.5");
            when(answerRepo.findById(a.getId())).thenReturn(Optional.of(a));
            when(answerRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AnswerDto dto = service.acceptaProposta(a.getId(), professor);

            assertThat(a.getManualScore()).isEqualByComparingTo("1.5");
            assertThat(a.getCorrectedAt()).isNotNull();
            assertThat(dto.manualScore()).isEqualByComparingTo("1.5");
            verify(examService).assertOwnership(exam, professor);
        }

        @Test
        void acceptar_sense_proposta_llanca_IllegalState() {
            Answer a = ambProposta(session, "2", null);
            when(answerRepo.findById(a.getId())).thenReturn(Optional.of(a));

            assertThatThrownBy(() -> service.acceptaProposta(a.getId(), professor))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(a.getManualScore()).isNull();
        }

        @Test
        void acceptar_d_un_examen_aliè_llanca_AccessDenied_i_no_desa() {
            Answer a = ambProposta(session, "2", "1");
            when(answerRepo.findById(a.getId())).thenReturn(Optional.of(a));
            doThrow(new org.springframework.security.access.AccessDeniedException("no"))
                    .when(examService).assertOwnership(exam, professor);

            assertThatThrownBy(() -> service.acceptaProposta(a.getId(), professor))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            assertThat(a.getManualScore()).isNull();
            verify(answerRepo, never()).save(any());
        }

        @Test
        void acceptar_limita_la_nota_entre_zero_i_el_maxim() {
            Answer negativa = ambProposta(session, "2", "-0.5");
            when(answerRepo.findById(negativa.getId())).thenReturn(Optional.of(negativa));
            when(answerRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.acceptaProposta(negativa.getId(), professor);

            assertThat(negativa.getManualScore()).isEqualByComparingTo("0");
        }

        @Test
        void acceptar_totes_nomes_accepta_les_que_tenen_proposta() {
            Answer amb1 = ambProposta(session, "2", "1");
            Answer amb2 = ambProposta(session, "2", "2");
            Answer sense = ambProposta(session, "2", null);
            when(examService.getEntity(exam.getId())).thenReturn(exam);
            when(answerRepo.findPendentsRevisio(exam.getId(), CorrectionService.PENDENTS_TIPUS_EXCLOSOS))
                    .thenReturn(List.of(amb1, amb2, sense));

            int acceptades = service.acceptaPropostes(exam.getId(), null, professor);

            assertThat(acceptades).isEqualTo(2);
            assertThat(amb1.getManualScore()).isEqualByComparingTo("1");
            assertThat(amb2.getManualScore()).isEqualByComparingTo("2");
            assertThat(sense.getManualScore()).isNull();
            verify(examService).assertOwnership(exam, professor);
        }

        @Test
        void acceptar_totes_d_una_sessio_no_toca_les_altres() {
            ExamSession altra = ExamSession.builder().id(UUID.randomUUID()).exam(exam).build();
            Answer meva = ambProposta(session, "2", "1");
            Answer aliena = ambProposta(altra, "2", "1");
            when(examService.getEntity(exam.getId())).thenReturn(exam);
            when(answerRepo.findPendentsRevisio(exam.getId(), CorrectionService.PENDENTS_TIPUS_EXCLOSOS))
                    .thenReturn(List.of(meva, aliena));

            int acceptades = service.acceptaPropostes(exam.getId(), session.getId(), professor);

            assertThat(acceptades).isEqualTo(1);
            assertThat(aliena.getManualScore()).isNull();
        }

        @Test
        void acceptar_totes_d_un_examen_aliè_llanca_AccessDenied() {
            when(examService.getEntity(exam.getId())).thenReturn(exam);
            doThrow(new org.springframework.security.access.AccessDeniedException("no"))
                    .when(examService).assertOwnership(exam, professor);

            assertThatThrownBy(() -> service.acceptaPropostes(exam.getId(), null, professor))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            verify(answerRepo, never()).findPendentsRevisio(any(), any());
        }
    }

    // ── cap connexió a la BD oberta durant Docker ────────────────────────────

    /** Gestor de transaccions fals que compta quantes n'hi ha d'obertes. */
    static class ComptadorTransaccions implements org.springframework.transaction.PlatformTransactionManager {
        int obertes;
        @Override public org.springframework.transaction.TransactionStatus getTransaction(
                org.springframework.transaction.TransactionDefinition d) {
            obertes++;
            return new org.springframework.transaction.support.SimpleTransactionStatus();
        }
        @Override public void commit(org.springframework.transaction.TransactionStatus s) { obertes--; }
        @Override public void rollback(org.springframework.transaction.TransactionStatus s) { obertes--; }
    }

    @Test
    void docker_s_executa_sense_cap_transaccio_oberta() {
        ComptadorTransaccions txm = new ComptadorTransaccions();
        service = new CorrectionService(answerRepo, executionRepo, executor, questionFileRepo, examService,
                new org.springframework.transaction.support.TransactionTemplate(txm));
        Question q = question(QuestionType.BASH_CMD, "1");
        Answer a = executableAnswer(q, "ls");
        int[] obertesDurantDocker = {-1};
        when(executor.execute(any(), anyString(), anyList())).thenAnswer(inv -> {
            obertesDurantDocker[0] = txm.obertes;
            return new ExecutionResult("ok", 0, 1);
        });

        service.execute(a.getId());

        assertThat(obertesDurantDocker[0]).isZero();
        assertThat(txm.obertes).isZero();
        assertScore(a, "1");
    }

    // ── executar com a usuari (autorització) ─────────────────────────────────

    @Nested
    class ExecuteAs {

        private Answer respostaDe(User alumne) {
            return respostaDe(alumne, QuestionType.BASH_CMD, SessionStatus.IN_PROGRESS);
        }

        private Answer respostaDe(User alumne, QuestionType tipus, SessionStatus estat) {
            Question q = question(tipus, "1");
            Exam exam = Exam.builder().id(UUID.randomUUID()).status(ExamStatus.PUBLISHED).build();
            ExamSession s = ExamSession.builder().id(UUID.randomUUID()).exam(exam).student(alumne).status(estat).build();
            Answer a = Answer.builder().id(UUID.randomUUID()).question(q).session(s).contingut("ls").build();
            when(answerRepo.findById(a.getId())).thenReturn(Optional.of(a));
            return a;
        }

        @Test
        void alumne_no_executa_el_test_del_professor_ni_rep_nota() {
            User alumne = User.builder().id(UUID.randomUUID()).role(Role.STUDENT).build();
            Answer a = respostaDe(alumne, QuestionType.BASH_SCRIPT, SessionStatus.IN_PROGRESS);
            a.getQuestion().setTestScript("[ \"$(bash $SCRIPT_FILE)\" = 30 ]");
            when(questionFileRepo.findByQuestionId(any())).thenReturn(List.of());
            when(answerRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            executorReturns("sortida de l'alumne", 0);

            ExecutionResultDto r = service.executeAs(a.getId(), alumne);

            assertThat(r.output()).isEqualTo("sortida de l'alumne");
            verify(executor, never()).executeWithTest(any(), anyString(), anyString(), anyList());
            verifyNoInteractions(executionRepo);
            assertThat(a.getAutoScore()).isNull();
            assertThat(a.getAutoFeedback()).isNull();
        }

        @Test
        void alumne_no_pot_executar_despres_d_entregar() {
            User alumne = User.builder().id(UUID.randomUUID()).role(Role.STUDENT).build();
            Answer a = respostaDe(alumne, QuestionType.BASH_CMD, SessionStatus.SUBMITTED);

            assertThatThrownBy(() -> service.executeAs(a.getId(), alumne))
                    .isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(executor);
        }

        @Test
        void alumne_no_pot_executar_si_l_examen_s_ha_tancat() {
            User alumne = User.builder().id(UUID.randomUUID()).role(Role.STUDENT).build();
            Answer a = respostaDe(alumne, QuestionType.BASH_CMD, SessionStatus.IN_PROGRESS);
            a.getSession().getExam().setStatus(ExamStatus.CLOSED);

            assertThatThrownBy(() -> service.executeAs(a.getId(), alumne))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("tancat");
            verifyNoInteractions(executor);
        }

        @Test
        void professor_si_que_executa_amb_el_test() {
            Answer a = respostaDe(User.builder().id(UUID.randomUUID()).role(Role.STUDENT).build(),
                    QuestionType.BASH_SCRIPT, SessionStatus.SUBMITTED);
            a.getQuestion().setTestScript("exit 0");
            when(questionFileRepo.findByQuestionId(any())).thenReturn(List.of());
            when(answerRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(executionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            executorWithTestReturns("", 0);

            service.executeAs(a.getId(), PROFESSOR);

            verify(executor).executeWithTest(any(), anyString(), anyString(), anyList());
            assertScore(a, "1");
        }

        @Test
        void alumne_executa_la_seva_resposta() {
            User alumne = User.builder().id(UUID.randomUUID()).role(Role.STUDENT).build();
            Answer a = respostaDe(alumne);
            when(questionFileRepo.findByQuestionId(any())).thenReturn(List.of());
            when(answerRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            executorReturns("ok", 0);

            service.executeAs(a.getId(), alumne);

            verify(executor).execute(any(), anyString(), anyList());
        }

        @Test
        void alumne_no_pot_executar_la_resposta_d_un_altre() {
            User propietari = User.builder().id(UUID.randomUUID()).role(Role.STUDENT).build();
            User altre = User.builder().id(UUID.randomUUID()).role(Role.STUDENT).build();
            Answer a = respostaDe(propietari);

            assertThatThrownBy(() -> service.executeAs(a.getId(), altre))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            verifyNoInteractions(executor);
        }

        @Test
        void professor_amb_permis_executa() {
            Answer a = respostaDe(User.builder().id(UUID.randomUUID()).role(Role.STUDENT).build());
            when(questionFileRepo.findByQuestionId(any())).thenReturn(List.of());
            when(answerRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            executorReturns("ok", 0);

            service.executeAs(a.getId(), PROFESSOR);

            verify(examService).assertOwnership(a.getSession().getExam(), PROFESSOR);
            verify(executor).execute(any(), anyString(), anyList());
        }

        @Test
        void professor_d_un_altre_examen_no_pot_executar_ni_canviar_la_proposta() {
            Answer a = respostaDe(User.builder().id(UUID.randomUUID()).role(Role.STUDENT).build());
            doThrow(new org.springframework.security.access.AccessDeniedException("no"))
                    .when(examService).assertOwnership(a.getSession().getExam(), PROFESSOR);

            assertThatThrownBy(() -> service.executeAs(a.getId(), PROFESSOR))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            verifyNoInteractions(executor, executionRepo);
            assertThat(a.getAutoScore()).isNull();
        }
    }

    // ── comentari del professor ──────────────────────────────────────────────

    @Nested
    class Comentari {

        private Answer resposta() {
            Exam exam = Exam.builder().id(UUID.randomUUID()).build();
            ExamSession s = ExamSession.builder().id(UUID.randomUUID()).exam(exam).build();
            Answer a = Answer.builder().id(UUID.randomUUID()).session(s).question(question(QuestionType.SHORT, "2")).build();
            when(answerRepo.findById(a.getId())).thenReturn(Optional.of(a));
            return a;
        }

        @Test
        void desa_el_comentari_sense_espais_als_extrems() {
            Answer a = resposta();
            when(answerRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AnswerDto dto = service.setComentari(a.getId(), "  Falta justificar la resposta.  ", PROFESSOR);

            assertThat(a.getComentari()).isEqualTo("Falta justificar la resposta.");
            assertThat(dto.comentari()).isEqualTo("Falta justificar la resposta.");
            verify(examService).assertOwnership(a.getSession().getExam(), PROFESSOR);
        }

        @Test
        void comentari_buit_l_esborra() {
            Answer a = resposta();
            a.setComentari("antic");
            when(answerRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.setComentari(a.getId(), "   ", PROFESSOR);

            assertThat(a.getComentari()).isNull();
        }

        @Test
        void comentari_massa_llarg_es_rebutja() {
            Answer a = resposta();

            assertThatThrownBy(() -> service.setComentari(a.getId(), "x".repeat(2001), PROFESSOR))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(answerRepo, never()).save(any());
        }

        @Test
        void professor_d_un_altre_examen_no_pot_comentar() {
            Answer a = resposta();
            doThrow(new org.springframework.security.access.AccessDeniedException("no"))
                    .when(examService).assertOwnership(a.getSession().getExam(), PROFESSOR);

            assertThatThrownBy(() -> service.setComentari(a.getId(), "hola", PROFESSOR))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            assertThat(a.getComentari()).isNull();
        }
    }

    // ── puntuació manual ─────────────────────────────────────────────────────

    @Nested
    class SetManualScore {

        private Answer manualAnswer(String punts) {
            Answer a = answer(question(QuestionType.LONG, punts), "resposta llarga");
            return a;
        }

        @Test
        void puntuacio_valida_es_desa_amb_data_de_correccio() {
            Answer a = manualAnswer("5");
            when(answerRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            LocalDateTime abans = LocalDateTime.now();

            AnswerDto dto = service.setManualScore(a.getId(), new BigDecimal("3.5"), PROFESSOR);

            assertThat(a.getManualScore()).isEqualByComparingTo("3.5");
            assertThat(a.getCorrectedAt()).isNotNull().isAfterOrEqualTo(abans);
            assertThat(dto).isNotNull();
            verify(answerRepo).save(a);
        }

        @Test
        void accepta_zero() {
            Answer a = manualAnswer("5");
            when(answerRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.setManualScore(a.getId(), BigDecimal.ZERO, PROFESSOR);

            assertThat(a.getManualScore()).isEqualByComparingTo("0");
        }

        @Test
        void accepta_el_maxim_exacte() {
            Answer a = manualAnswer("5");
            when(answerRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.setManualScore(a.getId(), new BigDecimal("5.00"), PROFESSOR);

            assertThat(a.getManualScore()).isEqualByComparingTo("5");
        }

        @Test
        void negativa_llanca_IllegalArgument_i_no_desa() {
            Answer a = manualAnswer("5");

            assertThatThrownBy(() -> service.setManualScore(a.getId(), new BigDecimal("-0.01"), PROFESSOR))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("5");
            verify(answerRepo, never()).save(any());
            assertThat(a.getManualScore()).isNull();
        }

        @Test
        void superior_al_maxim_llanca_IllegalArgument_i_no_desa() {
            Answer a = manualAnswer("5");

            assertThatThrownBy(() -> service.setManualScore(a.getId(), new BigDecimal("5.01"), PROFESSOR))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(answerRepo, never()).save(any());
        }

        @Test
        void no_modifica_la_nota_automatica() {
            Answer a = manualAnswer("5");
            a.setAutoScore(new BigDecimal("2"));
            when(answerRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.setManualScore(a.getId(), new BigDecimal("4"), PROFESSOR);

            assertThat(a.getAutoScore()).isEqualByComparingTo("2");
        }

        @Test
        void professor_sense_permis_sobre_l_examen_llanca_AccessDenied_i_no_desa() {
            Answer a = manualAnswer("5");
            doThrow(new org.springframework.security.access.AccessDeniedException("no"))
                    .when(examService).assertOwnership(any(), eq(PROFESSOR));

            assertThatThrownBy(() -> service.setManualScore(a.getId(), BigDecimal.ONE, PROFESSOR))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            assertThat(a.getManualScore()).isNull();
            verify(answerRepo, never()).save(any());
        }

        @Test
        void resposta_inexistent_llanca_NoSuchElement() {
            UUID id = UUID.randomUUID();
            when(answerRepo.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.setManualScore(id, BigDecimal.ONE, PROFESSOR))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }
}
