package com.examplatform.infrastructure.execution;

import com.examplatform.domain.model.QuestionType;
import com.examplatform.domain.port.ScriptExecutor.ExecutionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Executa codi de veritat amb Docker: comprova que el sandbox conté els abusos habituals. */
@Tag("integration")
class DockerSandboxIntegrationTest {

    @TempDir Path scripts;
    DockerScriptExecutor executor;

    @BeforeEach
    void setUp() {
        scripts.toFile().setReadable(true, false);
        scripts.toFile().setExecutable(true, false);
        executor = new DockerScriptExecutor();
        ReflectionTestUtils.setField(executor, "scriptsHostPath", scripts.toString());
        ReflectionTestUtils.setField(executor, "timeoutSeconds", 4);
        ReflectionTestUtils.setField(executor, "memoryLimit", "128m");
        ReflectionTestUtils.setField(executor, "cpus", "0.5");
        ReflectionTestUtils.setField(executor, "bashImage", "bash:5");
        ReflectionTestUtils.setField(executor, "psImage", "mcr.microsoft.com/powershell:latest");
        ReflectionTestUtils.setField(executor, "javaImage", "eclipse-temurin:21-jdk-alpine");
        ReflectionTestUtils.setField(executor, "pidsLimit", 128);
        // Etiqueta pròpia: la neteja dels tests no pot tocar execucions reals de la mateixa màquina
        ReflectionTestUtils.setField(executor, "labelValue", "test");
    }

    @AfterEach
    void netejaSiCal() {
        executor.netejaContenidorsOrfes();
    }

    private static int contenidorsVius() throws Exception {
        Process p = new ProcessBuilder("docker", "ps", "-q", "--filter", "label=" + DockerScriptExecutor.LABEL + "=test")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).strip();
        p.waitFor(10, TimeUnit.SECONDS);
        return out.isEmpty() ? 0 : out.split("\\s+").length;
    }

    @Test
    void codi_normal_s_executa_com_a_usuari_sense_privilegis() {
        ExecutionResult r = executor.execute(QuestionType.BASH_CMD, "echo \"uid=$(id -u)\"", List.of());

        assertThat(r.exitCode()).isZero();
        assertThat(r.output()).contains("uid=65534");
    }

    @Test
    void bucle_infinit_s_atura_i_no_deixa_el_contenidor_viu() throws Exception {
        ExecutionResult r = executor.execute(QuestionType.BASH_CMD, "while :; do :; done", List.of());

        assertThat(r.output()).contains("[Timeout:");
        assertThat(contenidorsVius()).isZero();
    }

    @Test
    void sortida_massiva_es_talla_sense_esperar_el_timeout() throws Exception {
        long start = System.currentTimeMillis();
        ExecutionResult r = executor.execute(QuestionType.BASH_CMD, "yes", List.of());
        long ms = System.currentTimeMillis() - start;

        assertThat(r.output()).contains("[Output truncat");
        assertThat(r.output().length()).isLessThan(11_000);
        assertThat(ms).isLessThan(4_000);
        assertThat(contenidorsVius()).isZero();
    }

    @Test
    void fork_bomb_queda_continguda() throws Exception {
        ExecutionResult r = executor.execute(QuestionType.BASH_CMD, "f(){ f|f& }; f; sleep 1; echo viu", List.of());

        assertThat(r.output()).containsAnyOf("Resource temporarily unavailable", "Timeout");
        assertThat(contenidorsVius()).isZero();
    }

    @Test
    void java_compila_i_s_executa_sense_privilegis() {
        ExecutionResult r = executor.execute(QuestionType.JAVA_PROG,
                "public class Main { public static void main(String[] a) { System.out.println(40 + 2); } }", List.of());

        assertThat(r.exitCode()).isZero();
        assertThat(r.output()).contains("42");
    }

    @Test
    void test_script_amb_script_de_l_alumne() {
        ExecutionResult r = executor.executeWithTest(QuestionType.BASH_SCRIPT, "echo \"Hola, $1!\"",
                "[ \"$(bash \"$SCRIPT_FILE\" Anna)\" = \"Hola, Anna!\" ]", List.of());

        assertThat(r.exitCode()).isZero();
    }
}
