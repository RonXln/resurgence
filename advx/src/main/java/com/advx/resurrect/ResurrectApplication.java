package com.advx.resurrect;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 再活一次：数字项目复活系统 —— 应用入口
 */
@SpringBootApplication
public class ResurrectApplication {
    public static void main(String[] args) {
        // 尝试从多个候选位置加载 .env，避免因工作目录不同而找不到
        loadDotenv();
        SpringApplication.run(ResurrectApplication.class, args);
    }

    private static void loadDotenv() {
        // 候选目录：当前工作目录、advx/ 子目录、上级目录（覆盖从 IDE / 命令行 / 工作区根启动等情况）
        Path cwd = Paths.get("").toAbsolutePath();
        List<Path> candidates = new ArrayList<>();
        candidates.add(cwd);
        candidates.add(cwd.resolve("advx"));
        if (cwd.getParent() != null) candidates.add(cwd.getParent());

        for (Path dir : candidates) {
            Path envFile = dir.resolve(".env");
            if (Files.isRegularFile(envFile)) {
                Dotenv dotenv = Dotenv.configure()
                        .directory(dir.toString())
                        .ignoreIfMalformed()
                        .load();
                dotenv.entries().forEach(e -> {
                    if (System.getenv(e.getKey()) == null && System.getProperty(e.getKey()) == null) {
                        System.setProperty(e.getKey(), e.getValue());
                    }
                });
                System.out.println("[dotenv] loaded from " + envFile);
                return;
            }
        }
        System.out.println("[dotenv] no .env found under: " + candidates);
    }
}
