package com.examplatform.infrastructure.execution;

import com.examplatform.domain.model.QuestionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class DockerScriptExecutorTest {

    DockerScriptExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new DockerScriptExecutor();
        setField("scriptsHostPath", "/tmp/exam-test");
        setField("timeoutSeconds", 10);
        setField("memoryLimit",    "128m");
        setField("cpus",           "0.5");
        setField("bashImage",      "alpine:latest");
        setField("psImage",        "mcr.microsoft.com/powershell:latest");
        setField("javaImage",      "eclipse-temurin:21-jdk-alpine");
        setField("pidsLimit",      128);
    }

    // ── aïllament del contenidor ──────────────────────────────────────────────

    @Test
    void la_comanda_aplica_totes_les_restriccions_de_seguretat() {
        var cmd = executor.buildRunCommand(QuestionType.BASH_CMD, java.nio.file.Path.of("/tmp/exam-test/x"),
                java.nio.file.Path.of("/tmp/exam-test/x/script.sh"), null, java.util.List.of(), "abc");

        assertThat(cmd).contains("--rm", "--network=none", "--cap-drop=ALL", "--pids-limit=128",
                "--user=65534:65534", "--read-only", "--memory=128m", "--cpus=0.5",
                "no-new-privileges:true");
        assertThat(String.join(" ", cmd))
                .contains("--name exam-exec-abc")
                .contains("--label " + DockerScriptExecutor.LABEL + "=1")
                .contains("--tmpfs=/tmp:rw,size=128m,mode=1777")
                .contains("/tmp/exam-test/x:/workspace:ro");
    }

    @Test
    void java_tambe_s_executa_sense_privilegis() {
        var cmd = executor.buildRunCommand(QuestionType.JAVA_PROG, java.nio.file.Path.of("/tmp/exam-test/x"),
                java.nio.file.Path.of("/tmp/exam-test/x/Main.java"), null, java.util.List.of(), "j");

        assertThat(cmd).contains("--cap-drop=ALL", "--user=65534:65534", "--pids-limit=128");
    }

    // ── validateDockerParams — protecció injecció ─────────────────────────────

    @Test
    void execute_memoryLimit_invalida_llanca_IllegalStateException() {
        setField("memoryLimit", "128m; rm -rf /");

        assertThatThrownBy(() -> executor.execute(QuestionType.BASH_CMD, "echo ok"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("memoryLimit");
    }

    @Test
    void execute_cpus_invalids_llanca_IllegalStateException() {
        setField("cpus", "0.5 --privileged");

        assertThatThrownBy(() -> executor.execute(QuestionType.BASH_CMD, "echo ok"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cpus");
    }

    @Test
    void execute_memoryLimit_sense_unitat_es_valida() {
        setField("memoryLimit", "256");
        Throwable t = catchThrowable(() -> executor.execute(QuestionType.BASH_CMD, "echo ok"));
        if (t != null) assertThat(t).isNotInstanceOf(IllegalStateException.class);
    }

    @Test
    void execute_memoryLimit_amb_majuscules_es_valida() {
        setField("memoryLimit", "128M");
        Throwable t = catchThrowable(() -> executor.execute(QuestionType.BASH_CMD, "echo ok"));
        if (t != null) assertThat(t).isNotInstanceOf(IllegalStateException.class);
    }

    @Test
    void execute_cpus_decimal_es_valid() {
        setField("cpus", "1.5");
        Throwable t = catchThrowable(() -> executor.execute(QuestionType.BASH_CMD, "echo ok"));
        if (t != null) assertThat(t).isNotInstanceOf(IllegalStateException.class);
    }

    @Test
    void execute_cpus_enter_es_valid() {
        setField("cpus", "2");
        Throwable t = catchThrowable(() -> executor.execute(QuestionType.BASH_CMD, "echo ok"));
        if (t != null) assertThat(t).isNotInstanceOf(IllegalStateException.class);
    }

    // ── tipus no executable ───────────────────────────────────────────────────

    @Test
    void execute_tipus_no_executable_llanca_IllegalArgumentException() {
        assertThatThrownBy(() -> executor.execute(QuestionType.TEXT, "resposta"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── executeWithTest delega correctament ───────────────────────────────────

    @Test
    void executeWithTest_memoryInvalida_llanca_IllegalStateException() {
        setField("memoryLimit", "128m; cat /etc/passwd");

        assertThatThrownBy(() ->
                executor.executeWithTest(QuestionType.BASH_SCRIPT, "echo ok", "exit 0"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setField(String name, Object value) {
        ReflectionTestUtils.setField(executor, name, value);
    }
}
