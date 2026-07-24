package com.advx.resurrect.controller;

import com.advx.resurrect.config.AppProperties;
import com.advx.resurrect.model.JobState;
import com.advx.resurrect.model.JobStatus;
import com.advx.resurrect.model.ProgressEvent;
import com.advx.resurrect.service.AgentOrchestrator;
import com.advx.resurrect.service.ResurrectionBundler;
import com.advx.resurrect.store.JobStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class ResurrectController {

    private static final Logger log = LoggerFactory.getLogger(ResurrectController.class);

    private final AppProperties props;
    private final JobStore store;
    private final AgentOrchestrator orchestrator;
    private final ResurrectionBundler bundler;

    public ResurrectController(AppProperties props, JobStore store, AgentOrchestrator orchestrator,
                               ResurrectionBundler bundler) {
        this.props = props;
        this.store = store;
        this.orchestrator = orchestrator;
        this.bundler = bundler;
    }

    /** 上传压缩包，返回 jobId。 */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件为空"));
        }
        long maxBytes = (long) props.getUpload().getMaxUploadMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "文件超过 " + props.getUpload().getMaxUploadMb() + "MB 限制"));
        }

        try {
            JobState job = store.create();
            String base = props.getUpload().getWorkdir();
            if (base == null || base.isBlank()) base = System.getProperty("java.io.tmpdir") + "/resurrect";
            Path workDir = Paths.get(base, job.getJobId());
            Files.createDirectories(workDir);

            String orig = file.getOriginalFilename() == null ? "archive.zip" : file.getOriginalFilename();
            Path archive = workDir.resolve(sanitize(orig));
            file.transferTo(archive.toFile());
            job.setUploadedFilePath(archive.toString());

            store.publish(ProgressEvent.of(job.getJobId(), JobStatus.PENDING, "上传",
                    "已收到 " + orig + "（" + humanBytes(file.getSize()) + "）", 2));

            orchestrator.runAsync(job.getJobId(), archive, orig, workDir);

            return ResponseEntity.ok(Map.of(
                    "jobId", job.getJobId(),
                    "filename", orig,
                    "size", file.getSize()
            ));
        } catch (IOException e) {
            log.error("上传失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** SSE 订阅进度。 */
    @GetMapping(value = "/analyze/{jobId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyze(@PathVariable String jobId) {
        JobState job = store.get(jobId);
        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(30));
        if (job == null) {
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of("error", "任务不存在")));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        Runnable unsub = store.subscribe(jobId, event -> {
            try {
                emitter.send(SseEmitter.event().name("progress").data(event));
                if (event.status() == JobStatus.DONE || event.status() == JobStatus.FAILED) {
                    // 稍作停顿再关闭，让最后一条被前端 flush
                    Executors.newSingleThreadScheduledExecutor(r -> {
                        Thread t = new Thread(r, "sse-closer");
                        t.setDaemon(true);
                        return t;
                    }).schedule(emitter::complete, 300, TimeUnit.MILLISECONDS);
                }
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        });
        emitter.onCompletion(unsub);
        emitter.onTimeout(unsub);
        emitter.onError(t -> unsub.run());
        return emitter;
    }

    /** 拉取最终结果。 */
    @GetMapping(value = "/result/{jobId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> result(@PathVariable String jobId) {
        JobState job = store.get(jobId);
        if (job == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.ofEntries(
                Map.entry("jobId", job.getJobId()),
                Map.entry("status", job.getStatus()),
                Map.entry("error", job.getErrorMessage() == null ? "" : job.getErrorMessage()),
                Map.entry("snapshot", job.getSnapshot() == null ? Map.of() : job.getSnapshot()),
                Map.entry("opinions", job.getOpinions()),
                Map.entry("plan", job.getPlan() == null ? Map.of() : job.getPlan()),
                Map.entry("htmlFallback", job.isHtmlIsFallback()),
                Map.entry("pitch", job.getPitch() == null ? Map.of() : job.getPitch())
        ));
    }

    /** 直接返回 iframe 用的 HTML（可 srcdoc / src 二选一使用）。 */
    @GetMapping(value = "/demo/{jobId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> demo(@PathVariable String jobId) {
        JobState job = store.get(jobId);
        if (job == null || job.getResurrectedHtml() == null) {
            return ResponseEntity.status(404).contentType(MediaType.TEXT_HTML)
                    .body("<html><body><p>还没准备好</p></body></html>");
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(job.getResurrectedHtml());
    }

    /** 一键下载"复活体礼包" zip：index.html + README.md + plan.json + heart.txt。 */
    @GetMapping(value = "/download/{jobId}")
    public ResponseEntity<byte[]> download(@PathVariable String jobId) {
        JobState job = store.get(jobId);
        if (job == null || job.getResurrectedHtml() == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            byte[] zipBytes = bundler.bundle(job);
            String filename = bundler.suggestedFilename(job);
            // 兼容 ASCII fallback + RFC 5987 UTF-8 编码，处理中文文件名
            String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
            String disposition = "attachment; filename=\"resurrected.zip\"; filename*=UTF-8''" + encoded;
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .body(zipBytes);
        } catch (IOException e) {
            log.error("打包 zip 失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private static String sanitize(String filename) {
        return filename.replaceAll("[^A-Za-z0-9._\\-]+", "_");
    }

    private static String humanBytes(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return (b / 1024) + " KB";
        return String.format("%.1f MB", b / 1024.0 / 1024.0);
    }
}
