package com.advx.resurrect.service;

import com.advx.resurrect.config.AppProperties;
import com.github.junrar.Junrar;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 压缩包解压器。支持 zip / tar / tar.gz / 7z / rar。
 * 内置 zip-slip 与 zip-bomb 防护。
 */
@Service
public class ArchiveExtractor {

    private static final Logger log = LoggerFactory.getLogger(ArchiveExtractor.class);

    private final AppProperties props;

    public ArchiveExtractor(AppProperties props) {
        this.props = props;
    }

    /**
     * 解压到目标目录。返回解压后实际的项目根（如果压缩包只包含一个顶级目录，就返回它）。
     */
    public Path extract(Path archive, Path targetDir, String originalFilename) throws IOException {
        Files.createDirectories(targetDir);
        String lower = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);

        try {
            if (lower.endsWith(".zip")) {
                extractZip(archive, targetDir);
            } else if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) {
                extractTarGz(archive, targetDir);
            } else if (lower.endsWith(".tar")) {
                extractTar(archive, targetDir);
            } else if (lower.endsWith(".7z")) {
                extractSevenZ(archive, targetDir);
            } else if (lower.endsWith(".rar")) {
                Junrar.extract(archive.toFile(), targetDir.toFile());
            } else {
                // 尝试当 zip 处理（有些用户不带后缀）
                extractZip(archive, targetDir);
            }
        } catch (Exception e) {
            throw new IOException("解压失败: " + e.getMessage(), e);
        }

        return findProjectRoot(targetDir);
    }

    private void extractZip(Path archive, Path targetDir) throws IOException {
        try (ZipArchiveInputStream zin = new ZipArchiveInputStream(
                new BufferedInputStream(new FileInputStream(archive.toFile())), "UTF-8", true, true)) {
            walk(zin, targetDir);
        }
    }

    private void extractTar(Path archive, Path targetDir) throws IOException {
        try (TarArchiveInputStream tin = new TarArchiveInputStream(
                new BufferedInputStream(new FileInputStream(archive.toFile())))) {
            walk(tin, targetDir);
        }
    }

    private void extractTarGz(Path archive, Path targetDir) throws IOException {
        try (TarArchiveInputStream tin = new TarArchiveInputStream(
                new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(archive.toFile()))))) {
            walk(tin, targetDir);
        }
    }

    private void extractSevenZ(Path archive, Path targetDir) throws IOException {
        long totalWritten = 0;
        long maxUncompressed = (long) props.getUpload().getMaxUploadMb() * 50L * 1024L * 1024L; // 50x 上限，防 bomb
        int fileCount = 0;
        int maxFiles = props.getUpload().getMaxFiles();

        try (SevenZFile sevenZFile = new SevenZFile(archive.toFile())) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                if (++fileCount > maxFiles) throw new IOException("文件数超过上限: " + maxFiles);
                Path out = safeResolve(targetDir, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Files.createDirectories(out.getParent());
                try (var os = Files.newOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = sevenZFile.read(buf)) > 0) {
                        totalWritten += n;
                        if (totalWritten > maxUncompressed) throw new IOException("解压体积超过安全上限");
                        os.write(buf, 0, n);
                    }
                }
            }
        }
    }

    private void walk(ArchiveInputStream in, Path targetDir) throws IOException {
        ArchiveEntry entry;
        long totalWritten = 0;
        long maxUncompressed = (long) props.getUpload().getMaxUploadMb() * 50L * 1024L * 1024L;
        int fileCount = 0;
        int maxFiles = props.getUpload().getMaxFiles();

        while ((entry = in.getNextEntry()) != null) {
            if (!in.canReadEntryData(entry)) continue;
            if (++fileCount > maxFiles) throw new IOException("文件数超过上限: " + maxFiles);
            Path out = safeResolve(targetDir, entry.getName());
            if (entry.isDirectory()) {
                Files.createDirectories(out);
                continue;
            }
            Files.createDirectories(out.getParent());
            // 逐块写入并累计校验
            try (var os = Files.newOutputStream(out, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    totalWritten += n;
                    if (totalWritten > maxUncompressed) {
                        throw new IOException("解压体积超过安全上限（可能是 zip-bomb）");
                    }
                    os.write(buf, 0, n);
                }
            }
        }
    }

    /** 防 zip-slip：确保解压路径始终在 targetDir 之内。 */
    private Path safeResolve(Path targetDir, String entryName) throws IOException {
        Path resolved = targetDir.resolve(entryName).normalize();
        if (!resolved.startsWith(targetDir.normalize())) {
            throw new IOException("非法的压缩包条目路径: " + entryName);
        }
        return resolved;
    }

    /** 如果解压结果只有一个顶级目录，返回它；否则返回 targetDir 本身。 */
    private Path findProjectRoot(Path targetDir) throws IOException {
        try (var stream = Files.list(targetDir)) {
            var list = stream.toList();
            if (list.size() == 1 && Files.isDirectory(list.get(0))) {
                return list.get(0);
            }
            return targetDir;
        }
    }

    /** 允许外部工具类调用；供测试。 */
    @SuppressWarnings("unused")
    private static InputStream buffered(InputStream in) { return new BufferedInputStream(in); }
}
