package com.examplatform.infrastructure.execution;

import com.examplatform.domain.model.QuestionType;
import com.examplatform.domain.port.ScriptExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

@Component
public class DockerScriptExecutor implements ScriptExecutor {

    private static final Logger log = LoggerFactory.getLogger(DockerScriptExecutor.class);

    @Value("${execution.scripts-host-path}")
    private String scriptsHostPath;

    @Value("${execution.timeout-seconds}")
    private int timeoutSeconds;

    @Value("${execution.memory-limit}")
    private String memoryLimit;

    @Value("${execution.cpus}")
    private String cpus;

    @Value("${execution.bash-image}")
    private String bashImage;

    @Value("${execution.powershell-image}")
    private String psImage;

    @Value("${execution.java-image}")
    private String javaImage;

    @Value("${execution.pids-limit:128}")
    private int pidsLimit;

    /** Etiqueta dels contenidors d'execució, per poder-los aturar i netejar. */
    static final String LABEL = "exam-platform.exec";

    /** Valor de l'etiqueta: separa les execucions d'aquesta instància (p. ex. dels tests). */
    @Value("${execution.label-value:1}")
    private String labelValue = "1";

    /** Màxim de caràcters de sortida que es llegeixen; si se supera, s'atura l'execució. */
    static final int MAX_OUTPUT_CHARS = 64_000;

    /** Usuari sense privilegis (nobody) amb què s'executa el codi dins del contenidor. */
    private static final String SANDBOX_USER = "65534:65534";

    /**
     * En arrencar, atura els contenidors d'execució que hagin quedat orfes
     * (p. ex. si el backend es va aturar a mitja execució).
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void netejaContenidorsOrfes() {
        try {
            Process ps = new ProcessBuilder("docker", "ps", "-q", "--filter", "label=" + LABEL + "=" + labelValue)
                    .redirectErrorStream(true).start();
            String ids = new String(ps.getInputStream().readAllBytes()).strip();
            ps.waitFor(10, TimeUnit.SECONDS);
            if (ids.isEmpty()) return;
            List<String> cmd = new ArrayList<>(List.of("docker", "rm", "-f"));
            cmd.addAll(List.of(ids.split("\\s+")));
            new ProcessBuilder(cmd).redirectErrorStream(true).start().waitFor(30, TimeUnit.SECONDS);
            log.warn("S'han aturat {} contenidors d'execució orfes", cmd.size() - 3);
        } catch (IOException e) {
            log.warn("No s'han pogut netejar els contenidors orfes: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public ExecutionResult execute(QuestionType type, String code, List<Path> dataFiles) {
        if (!type.isExecutable()) {
            throw new IllegalArgumentException("El tipus " + type + " no és executable");
        }

        String execId = UUID.randomUUID().toString();
        Path execDir  = Path.of(scriptsHostPath, execId);

        try {
            Files.createDirectories(execDir);
            Path scriptFile = execDir.resolve(scriptFilename(type));
            Files.writeString(scriptFile, code, StandardOpenOption.CREATE_NEW);
            trySetPermissions(scriptFile);

            List<String> cmd = buildRunCommand(type, execDir, scriptFile, null, dataFiles, execId);
            return runProcess(cmd, containerName(execId));
        } catch (IOException e) {
            log.error("Error executant script", e);
            return new ExecutionResult("Error d'execució: " + e.getMessage(), -1, 0);
        } finally {
            deleteDir(execDir);
        }
    }

    @Override
    public ExecutionResult executeWithTest(QuestionType type, String studentCode,
                                           String testScript, List<Path> dataFiles) {
        if (!type.isExecutable()) {
            throw new IllegalArgumentException("El tipus " + type + " no és executable");
        }

        String execId    = UUID.randomUUID().toString();
        Path   execDir   = Path.of(scriptsHostPath, execId);

        try {
            Files.createDirectories(execDir);
            Path studentFile = execDir.resolve(scriptFilename(type));
            Path testFile    = execDir.resolve("test" + scriptExt(type));
            Files.writeString(studentFile, studentCode);
            Files.writeString(testFile,    testScript);
            trySetPermissions(studentFile);
            trySetPermissions(testFile);

            List<String> cmd = buildRunCommand(type, execDir, studentFile, testFile, dataFiles, execId);
            return runProcess(cmd, containerName(execId));
        } catch (IOException e) {
            log.error("Error executant script amb test", e);
            return new ExecutionResult("Error d'execució: " + e.getMessage(), -1, 0);
        } finally {
            deleteDir(execDir);
        }
    }

    // -------------------------------------------------------------------------

    private static final Pattern SAFE_MEMORY = Pattern.compile("^\\d+[kmgKMG]?$");
    private static final Pattern SAFE_CPUS   = Pattern.compile("^\\d+(\\.\\d+)?$");

    static String containerName(String execId) {
        return "exam-exec-" + execId;
    }

    List<String> buildRunCommand(QuestionType type, Path execDir,
                                         Path scriptFile, Path testFile,
                                         List<Path> dataFiles, String execId) {
        validateDockerParams();

        // Ruta del host del directori d'execució → la muntem a /workspace dins del contenidor
        String hostExecDir = execDir.toAbsolutePath().toString();

        List<String> cmd = new ArrayList<>(List.of(
                "docker", "run", "--rm",
                "--name", containerName(execId),
                "--label", LABEL + "=" + labelValue,
                "--network=none",
                "--memory=" + memoryLimit,
                "--cpus=" + cpus,
                "--pids-limit=" + pidsLimit,
                "--cap-drop=ALL",
                "--user=" + SANDBOX_USER,
                "-e", "HOME=/tmp",
                "--security-opt", "no-new-privileges:true",
                "-v", hostExecDir + ":/workspace:ro",
                "--tmpfs=/tmp:rw,size=128m,mode=1777"
        ));

        // Java no usa --read-only perquè el JVM necessita escriure en llocs del sistema
        if (!type.isJava()) {
            cmd.add("--read-only");
        }

        // Muntem els fitxers de dades de la pregunta, si n'hi ha
        if (!dataFiles.isEmpty()) {
            // Recollim el directori pare comú dels fitxers (normalment tots són al mateix dir)
            Path filesDir = dataFiles.get(0).getParent();
            if (filesDir != null && Files.isDirectory(filesDir)) {
                cmd.addAll(List.of("-v", filesDir.toAbsolutePath() + ":/data/files:ro"));
            }
        }

        if (type.isJava()) {
            String containerScript = "/workspace/" + scriptFilename(type);
            cmd.add(javaImage);
            cmd.addAll(List.of("sh", "-c",
                    "cp " + containerScript + " /tmp/Main.java && " +
                    "cd /tmp && " +
                    "javac Main.java 2>&1 && " +
                    "java -cp . Main 2>&1"));
        } else if (testFile != null) {
            // Test script: el test rep la ruta de l'script de l'alumne via env var
            String containerStudent = "/workspace/" + scriptFilename(type);
            String containerTest    = "/workspace/test" + scriptExt(type);
            cmd.addAll(List.of("-e", "SCRIPT_FILE=" + containerStudent));
            if (type.isBash()) {
                cmd.addAll(List.of(bashImage, "bash", containerTest));
            } else {
                cmd.addAll(List.of(psImage, "pwsh", "-File", containerTest));
            }
        } else {
            String containerScript = "/workspace/" + scriptFilename(type);
            if (type.isBash()) {
                cmd.addAll(List.of(bashImage, "bash", containerScript));
            } else {
                cmd.addAll(List.of(psImage, "pwsh", "-File", containerScript));
            }
        }

        return cmd;
    }

    private ExecutionResult runProcess(List<String> cmd, String containerName) {
        try {
            long start   = System.currentTimeMillis();
            Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String output = readWithTimeout(proc, timeoutSeconds, containerName);
            long duration = System.currentTimeMillis() - start;
            int exitCode;
            try {
                exitCode = proc.waitFor(10, TimeUnit.SECONDS) ? proc.exitValue() : -1;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exitCode = -1;
            }
            return new ExecutionResult(truncate(output), exitCode, duration);
        } catch (IOException e) {
            log.error("Error llançant contenidor", e);
            return new ExecutionResult("Error llançant contenidor: " + e.getMessage(), -1, 0);
        }
    }

    private void validateDockerParams() {
        if (!SAFE_MEMORY.matcher(memoryLimit).matches())
            throw new IllegalStateException("Format de memoryLimit invàlid: " + memoryLimit);
        if (!SAFE_CPUS.matcher(cpus).matches())
            throw new IllegalStateException("Format de cpus invàlid: " + cpus);
    }

    private String scriptFilename(QuestionType type) {
        if (type.isJava()) return "Main.java";
        return "script" + scriptExt(type);
    }

    private String scriptExt(QuestionType type) {
        if (type.isBash()) return ".sh";
        if (type.isJava()) return ".java";
        return ".ps1";
    }

    private void trySetPermissions(Path file) {
        try {
            // Llegible per l'usuari sense privilegis del contenidor (no hi ha secrets: és el codi a executar)
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
        } catch (UnsupportedOperationException | IOException ignored) {}
    }

    /**
     * Llegeix la sortida amb un límit de temps i de mida. Si se supera qualsevol dels dos,
     * atura el contenidor: matar només el client {@code docker run} el deixaria executant-se.
     */
    private String readWithTimeout(Process process, int timeoutSec, String containerName) {
        StringBuffer output = new StringBuffer();
        java.util.concurrent.atomic.AtomicBoolean massaLlarga = new java.util.concurrent.atomic.AtomicBoolean();
        ExecutorService reader = Executors.newSingleThreadExecutor();
        Future<Void> future = reader.submit(() -> {
            try (Reader r = new InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)) {
                char[] buf = new char[8192];
                int n;
                while ((n = r.read(buf)) != -1) {
                    int espai = MAX_OUTPUT_CHARS - output.length();
                    if (n > espai) {
                        output.append(buf, 0, Math.max(espai, 0));
                        massaLlarga.set(true);
                        break;
                    }
                    output.append(buf, 0, n);
                }
            }
            return null;
        });
        boolean aturar = false;
        String avis = null;
        try {
            future.get(timeoutSec, TimeUnit.SECONDS);
            if (massaLlarga.get()) {
                aturar = true;
                avis = "\n[Sortida massa llarga: execució aturada als " + MAX_OUTPUT_CHARS + " caràcters]";
            }
        } catch (TimeoutException e) {
            aturar = true;
            avis = "\n[Timeout: execució cancel·lada després de " + timeoutSec + "s]";
        } catch (ExecutionException e) {
            log.warn("Error llegint la sortida de {}: {}", containerName, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            aturar = true;
        } finally {
            if (aturar) {
                aturaContenidor(containerName);
                process.destroyForcibly();
            }
            reader.shutdownNow();
        }
        String result = output.toString();
        return avis == null ? result : result + avis;
    }

    private void aturaContenidor(String containerName) {
        try {
            new ProcessBuilder("docker", "rm", "-f", containerName)
                    .redirectErrorStream(true).start().waitFor(15, TimeUnit.SECONDS);
        } catch (IOException e) {
            log.error("No s'ha pogut aturar el contenidor {}", containerName, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String truncate(String output) {
        int max = 10_000;
        return output.length() > max
                ? output.substring(0, max) + "\n[Output truncat a " + max + " caràcters]"
                : output;
    }

    private void deleteDir(Path dir) {
        try {
            if (!Files.exists(dir)) return;
            Files.walk(dir)
                 .sorted(Comparator.reverseOrder())
                 .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }
}
