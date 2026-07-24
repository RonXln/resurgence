package com.advx.resurrect.service;

import com.advx.resurrect.config.AppProperties;
import com.advx.resurrect.model.ProjectSnapshot;
import org.mozilla.universalchardet.UniversalDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 分层读取项目：
 *  L1（骨架）：目录树、README、依赖清单、入口文件
 *  L2（抽样）：TODO/FIXME 附近代码、最大文件片段、最近修改文件
 */
@Service
public class ProjectReader {

    private static final Logger log = LoggerFactory.getLogger(ProjectReader.class);

    private static final Set<String> SKIP_DIRS = Set.of(
            "node_modules", ".git", ".svn", ".hg", "target", "build", "dist", "out",
            ".idea", ".vscode", "__pycache__", ".venv", "venv", ".gradle", ".mvn",
            "bin", "obj", ".next", ".nuxt", ".cache", ".pytest_cache"
    );

    private static final Set<String> BINARY_EXT = Set.of(
            "png","jpg","jpeg","gif","bmp","ico","webp","svg","pdf","zip","gz","tar","7z","rar",
            "class","jar","war","ear","exe","dll","so","dylib","bin","dat","db","sqlite",
            "mp3","mp4","mov","avi","mkv","wav","flac","ttf","otf","woff","woff2"
    );

    private static final Set<String> CODE_EXT = Set.of(
            "java","kt","scala","groovy",
            "js","jsx","ts","tsx","mjs","cjs",
            "py","rb","php","go","rs","swift","cs","cpp","c","h","hpp",
            "html","css","scss","sass","less","vue","svelte",
            "sh","bash","ps1","bat",
            "sql","graphql","proto","toml","yml","yaml","json","xml","ini","conf"
    );

    private static final List<String> KEY_FILE_PATTERNS = List.of(
            "README", "README.md", "README.txt", "README.rst",
            "package.json", "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle",
            "requirements.txt", "pyproject.toml", "setup.py", "Pipfile",
            "go.mod", "Cargo.toml", "composer.json", "Gemfile",
            ".env.example", "docker-compose.yml", "Dockerfile",
            "TODO", "TODO.md", "CHANGELOG.md", "NOTES.md"
    );

    private static final Pattern TODO_PATTERN = Pattern.compile(
            "(?i)(TODO|FIXME|XXX|HACK|BUG|WIP|WORKAROUND|TEMP|DEPRECATED)[:\\s]"
    );

    private static final List<String> DEATH_KEYWORDS = List.of(
            "deprecated", "abandoned", "no longer maintained", "archived",
            "give up", "giving up", "放弃", "废弃", "烂尾", "不再维护",
            "not working", "broken", "does not work", "半成品"
    );

    private final AppProperties props;

    public ProjectReader(AppProperties props) {
        this.props = props;
    }

    public ProjectSnapshot read(Path projectRoot) throws IOException {
        long maxTextBytes = props.getUpload().getMaxTextBytes();
        int maxFiles = props.getUpload().getMaxFiles();

        List<Path> allFiles = new ArrayList<>();
        long[] totalBytes = {0};
        int[] fileCounter = {0};

        Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (SKIP_DIRS.contains(dir.getFileName().toString())) return FileVisitResult.SKIP_SUBTREE;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (++fileCounter[0] > maxFiles) return FileVisitResult.TERMINATE;
                totalBytes[0] += attrs.size();
                allFiles.add(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });

        String projectName = projectRoot.getFileName() != null
                ? projectRoot.getFileName().toString() : "unknown-project";

        // 语言/框架检测
        Map<String, Integer> langCount = new HashMap<>();
        Set<String> frameworks = new LinkedHashSet<>();
        for (Path f : allFiles) {
            String name = f.getFileName().toString().toLowerCase(Locale.ROOT);
            String ext = getExt(name);
            if (CODE_EXT.contains(ext)) {
                langCount.merge(langNameFromExt(ext), 1, Integer::sum);
            }
            if (name.equals("package.json")) frameworks.add("Node/JS");
            if (name.equals("pom.xml")) frameworks.add("Maven/Java");
            if (name.startsWith("build.gradle")) frameworks.add("Gradle");
            if (name.equals("requirements.txt") || name.equals("pyproject.toml")) frameworks.add("Python");
            if (name.equals("go.mod")) frameworks.add("Go");
            if (name.equals("cargo.toml")) frameworks.add("Rust");
            if (name.equals("dockerfile")) frameworks.add("Docker");
            if (name.equals("next.config.js") || name.equals("next.config.mjs")) frameworks.add("Next.js");
            if (name.equals("vite.config.js") || name.equals("vite.config.ts")) frameworks.add("Vite");
        }

        // 目录树（截断到 2000 行）
        String dirTree = buildDirTree(projectRoot, 3);

        // Key files：读全文（截断到 8KB 每份）
        Map<String, String> keyFiles = new LinkedHashMap<>();
        long readBudget = maxTextBytes;
        for (Path f : allFiles) {
            String rel = projectRoot.relativize(f).toString().replace('\\', '/');
            String base = f.getFileName().toString();
            if (matchesKey(base)) {
                String text = readTextTruncated(f, 8192);
                if (text != null) {
                    keyFiles.put(rel, text);
                    readBudget -= text.length();
                    if (readBudget < 0) break;
                }
            }
        }

        // TODO 抽样
        List<ProjectSnapshot.TodoHit> todos = new ArrayList<>();
        List<String> deathSignals = new ArrayList<>();
        outer:
        for (Path f : allFiles) {
            String name = f.getFileName().toString().toLowerCase(Locale.ROOT);
            String ext = getExt(name);
            if (BINARY_EXT.contains(ext)) continue;
            if (!CODE_EXT.contains(ext) && !name.startsWith("readme") && !name.startsWith("todo")) continue;
            String text = readTextTruncated(f, 32 * 1024);
            if (text == null) continue;

            String rel = projectRoot.relativize(f).toString().replace('\\', '/');
            String[] lines = text.split("\n");
            for (int i = 0; i < lines.length; i++) {
                Matcher m = TODO_PATTERN.matcher(lines[i]);
                if (m.find()) {
                    String snippet = lines[i].trim();
                    if (snippet.length() > 200) snippet = snippet.substring(0, 200) + "…";
                    todos.add(new ProjectSnapshot.TodoHit(rel, i + 1, snippet));
                    if (todos.size() >= 40) break outer;
                }
            }

            String lower = text.toLowerCase(Locale.ROOT);
            for (String kw : DEATH_KEYWORDS) {
                if (lower.contains(kw)) {
                    deathSignals.add("[" + rel + "] " + kw);
                    if (deathSignals.size() >= 20) break;
                }
            }
        }

        // 最近修改文件
        List<Path> byMtime = new ArrayList<>(allFiles);
        byMtime.sort((a, b) -> Long.compare(mtime(b), mtime(a)));
        StringBuilder recent = new StringBuilder();
        Instant latest = null;
        Instant earliest = null;
        for (int i = 0; i < Math.min(8, byMtime.size()); i++) {
            Path f = byMtime.get(i);
            Instant t = Instant.ofEpochMilli(mtime(f));
            if (i == 0) latest = t;
            if (i == byMtime.size() - 1) earliest = t;
            String rel = projectRoot.relativize(f).toString().replace('\\', '/');
            recent.append(t).append("  ").append(rel).append("\n");
        }
        if (latest != null) {
            recent.insert(0, "最近改动: " + latest + "\n---\n");
        }

        // 抽样若干最大的源码文件（塞给 Agent 用）
        List<Path> bySize = new ArrayList<>(allFiles);
        bySize.sort((a, b) -> Long.compare(sizeOf(b), sizeOf(a)));
        for (Path f : bySize) {
            String rel = projectRoot.relativize(f).toString().replace('\\', '/');
            if (keyFiles.containsKey(rel)) continue;
            String name = f.getFileName().toString().toLowerCase(Locale.ROOT);
            String ext = getExt(name);
            if (!CODE_EXT.contains(ext)) continue;
            String text = readTextTruncated(f, 4096);
            if (text == null) continue;
            keyFiles.put(rel, text);
            readBudget -= text.length();
            if (readBudget < 0 || keyFiles.size() >= 24) break;
        }

        List<String> langs = langCount.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .map(e -> e.getKey() + "(" + e.getValue() + ")")
                .limit(6)
                .toList();

        return new ProjectSnapshot(
                projectName,
                projectRoot.toString(),
                allFiles.size(),
                totalBytes[0],
                langs,
                new ArrayList<>(frameworks),
                dirTree,
                keyFiles,
                todos,
                deathSignals,
                recent.toString()
        );
    }

    private String buildDirTree(Path root, int maxDepth) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> s = Files.walk(root, maxDepth)) {
            s.filter(p -> {
                for (Path part : p) {
                    if (SKIP_DIRS.contains(part.toString())) return false;
                }
                return true;
            }).sorted().forEach(p -> {
                int depth = root.relativize(p).getNameCount();
                if (p.equals(root)) return;
                sb.append("  ".repeat(Math.max(0, depth - 1)));
                sb.append(Files.isDirectory(p) ? "📁 " : "📄 ");
                sb.append(p.getFileName().toString());
                sb.append("\n");
                if (sb.length() > 6000) {
                    // 截断标记
                }
            });
        }
        if (sb.length() > 6000) {
            return sb.substring(0, 6000) + "\n... (目录树已截断)";
        }
        return sb.toString();
    }

    private boolean matchesKey(String basename) {
        for (String p : KEY_FILE_PATTERNS) {
            if (basename.equalsIgnoreCase(p)) return true;
        }
        String lower = basename.toLowerCase(Locale.ROOT);
        return lower.startsWith("readme") || lower.startsWith("todo") || lower.equals("main.py")
                || lower.equals("app.py") || lower.equals("index.js") || lower.equals("index.ts")
                || lower.equals("main.java") || lower.equals("main.go");
    }

    private String readTextTruncated(Path f, int maxBytes) {
        try {
            long sz = Files.size(f);
            if (sz == 0) return "";
            byte[] head = new byte[(int) Math.min(sz, maxBytes)];
            try (var is = Files.newInputStream(f)) {
                int total = 0;
                while (total < head.length) {
                    int r = is.read(head, total, head.length - total);
                    if (r < 0) break;
                    total += r;
                }
            }
            Charset cs = detectCharset(head);
            String s = new String(head, cs);
            if (sz > maxBytes) s += "\n... (已截断)";
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    private Charset detectCharset(byte[] bytes) {
        try {
            UniversalDetector d = new UniversalDetector(null);
            d.handleData(bytes, 0, bytes.length);
            d.dataEnd();
            String enc = d.getDetectedCharset();
            d.reset();
            if (enc != null) return Charset.forName(enc);
        } catch (Exception ignored) {}
        return StandardCharsets.UTF_8;
    }

    private long mtime(Path p) {
        try { return Files.getLastModifiedTime(p).toMillis(); }
        catch (IOException e) { return 0L; }
    }

    private long sizeOf(Path p) {
        try { return Files.size(p); } catch (IOException e) { return 0L; }
    }

    private String getExt(String name) {
        int i = name.lastIndexOf('.');
        return (i < 0) ? "" : name.substring(i + 1);
    }

    private String langNameFromExt(String ext) {
        return switch (ext) {
            case "java" -> "Java";
            case "kt" -> "Kotlin";
            case "js","jsx","mjs","cjs" -> "JavaScript";
            case "ts","tsx" -> "TypeScript";
            case "py" -> "Python";
            case "go" -> "Go";
            case "rs" -> "Rust";
            case "rb" -> "Ruby";
            case "php" -> "PHP";
            case "cs" -> "C#";
            case "cpp","c","h","hpp" -> "C/C++";
            case "swift" -> "Swift";
            case "html","css","scss","sass","less" -> "Web";
            case "vue" -> "Vue";
            case "svelte" -> "Svelte";
            default -> ext;
        };
    }
}
