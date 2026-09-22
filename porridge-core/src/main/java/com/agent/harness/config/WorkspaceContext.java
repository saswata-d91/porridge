package com.agent.harness.config;

import java.nio.file.Path;

public class WorkspaceContext {
    // ThreadLocal ensures that concurrently running subagents operate in their own isolated folders
    private static final ThreadLocal<Path> BASE_DIR = ThreadLocal.withInitial(() -> Path.of(".").toAbsolutePath().normalize());

    public static void setBaseDir(Path path) {
        BASE_DIR.set(path.toAbsolutePath().normalize());
    }

    public static Path getBaseDir() {
        return BASE_DIR.get();
    }

    public static void clear() {
        BASE_DIR.remove();
    }

    public static Path resolve(String userPath) {
        Path base = BASE_DIR.get();
        // Prevent path traversal outside the base directory (e.g. "../../../etc/passwd")
        Path resolved = base.resolve(userPath).normalize();
        if (!resolved.startsWith(base)) {
            throw new SecurityException("Security Exception: Path traversal attempt blocked for " + userPath);
        }
        return resolved;
    }
}
