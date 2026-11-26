package com.oldbutgold.shop.modules.trustsafety.application;

import static org.assertj.core.api.Assertions.assertThat;

final class LiveUiTestRunner {
    static void run(ProcessBuilder process) throws Exception {
        var output = java.nio.file.Files.createTempFile("og-complaint-ui-", ".log");
        try {
            var run = process.redirectOutput(output.toFile()).start();
            if (!run.waitFor(100,java.util.concurrent.TimeUnit.SECONDS)) {
                run.destroyForcibly();
                throw new AssertionError("UI test exceeded 100 seconds");
            }
            assertThat(run.exitValue()).withFailMessage(() -> {
                try { return java.nio.file.Files.readString(output); }
                catch (java.io.IOException ex) { return ex.getMessage(); }
            }).isZero();
        } finally { java.nio.file.Files.deleteIfExists(output); }
    }
}
