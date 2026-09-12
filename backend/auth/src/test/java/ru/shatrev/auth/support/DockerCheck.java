package ru.shatrev.auth.support;

import java.util.concurrent.TimeUnit;

/** Быстрая проверка Docker для пропуска Testcontainers-тестов без запуска контейнеров. */
public final class DockerCheck {

    private static volatile Boolean available;

    private DockerCheck() {
    }

    /** Вызывается JUnit через @EnabledIf; результат кэшируется. */
    public static boolean isDockerAvailable() {
        Boolean cached = available;
        if (cached != null) {
            return cached;
        }
        boolean result = probe();
        available = result;
        return result;
    }

    private static boolean probe() {
        try {
            ProcessBuilder builder = new ProcessBuilder("docker", "info", "--format", "{{.ServerVersion}}");
            builder.redirectErrorStream(true);
            Process process = builder.start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
