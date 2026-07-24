package com.advx.resurrect.service;

import com.advx.resurrect.agent.*;
import com.advx.resurrect.model.*;
import com.advx.resurrect.store.JobStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 编排：解压 → 读取 → 多 Agent 并行 → 仲裁 → HTML + Pitch。
 */
@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final ArchiveExtractor extractor;
    private final ProjectReader reader;
    private final ArchaeologistAgent archaeologist;
    private final CoronerAgent coroner;
    private final ScavengerAgent scavenger;
    private final ReformerAgent reformer;
    private final ArbiterAgent arbiter;
    private final HtmlGenerator htmlGen;
    private final PitchGenerator pitchGen;
    private final JobStore store;

    // 用一个专门的池跑 Agent，避免占满 Spring 的默认线程
    private final ExecutorService pool = Executors.newFixedThreadPool(6, r -> {
        Thread t = new Thread(r, "agent-worker");
        t.setDaemon(true);
        return t;
    });

    public AgentOrchestrator(ArchiveExtractor extractor,
                             ProjectReader reader,
                             ArchaeologistAgent archaeologist,
                             CoronerAgent coroner,
                             ScavengerAgent scavenger,
                             ReformerAgent reformer,
                             ArbiterAgent arbiter,
                             HtmlGenerator htmlGen,
                             PitchGenerator pitchGen,
                             JobStore store) {
        this.extractor = extractor;
        this.reader = reader;
        this.archaeologist = archaeologist;
        this.coroner = coroner;
        this.scavenger = scavenger;
        this.reformer = reformer;
        this.arbiter = arbiter;
        this.htmlGen = htmlGen;
        this.pitchGen = pitchGen;
        this.store = store;
    }

    @Async
    public void runAsync(String jobId, Path archivePath, String originalFilename, Path workDir) {
        JobState job = store.get(jobId);
        try {
            // 1. 解压
            job.setStatus(JobStatus.EXTRACTING);
            store.publish(ProgressEvent.of(jobId, JobStatus.EXTRACTING, "解压", "正在打开压缩包…", 5));
            Path projectRoot = extractor.extract(archivePath, workDir.resolve("extracted"), originalFilename);
            job.setExtractedDir(projectRoot.toString());
            store.publish(ProgressEvent.of(jobId, JobStatus.EXTRACTING, "解压", "解压完成", 15));

            // 2. 分层读取
            job.setStatus(JobStatus.READING);
            store.publish(ProgressEvent.of(jobId, JobStatus.READING, "读取", "开始扫描项目文件…", 20));
            ProjectSnapshot snapshot = reader.read(projectRoot);
            job.setSnapshot(snapshot);
            store.publish(ProgressEvent.of(jobId, JobStatus.READING, "读取",
                    "读取完成：%d 文件，语言 %s".formatted(snapshot.totalFiles(),
                            String.join("/", snapshot.detectedLanguages())),
                    35, snapshotBrief(snapshot)));

            // 3. 4 个 Agent 并行
            job.setStatus(JobStatus.ANALYZING);
            store.publish(ProgressEvent.of(jobId, JobStatus.ANALYZING, "分析",
                    "四位 Agent 已上场：考古学家、验尸官、拾荒者、改造家", 40));

            List<Agent> agents = List.of(archaeologist, coroner, scavenger, reformer);
            List<CompletableFuture<AgentOpinion>> futures = agents.stream()
                    .map(a -> CompletableFuture.supplyAsync(() -> {
                        store.publish(ProgressEvent.tick(jobId, JobStatus.ANALYZING,
                                a.name(), a.name() + " 正在阅读代码…"));
                        AgentOpinion op = a.analyze(snapshot);
                        job.addOpinion(op);
                        store.publish(ProgressEvent.withPayload(jobId, JobStatus.ANALYZING,
                                a.name(), a.name() + " 已给出观点", op));
                        return op;
                    }, pool))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            List<AgentOpinion> opinions = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
            store.publish(ProgressEvent.of(jobId, JobStatus.ANALYZING, "分析", "四位 Agent 全部完成", 65));

            // 4. 仲裁
            job.setStatus(JobStatus.ARBITRATING);
            store.publish(ProgressEvent.of(jobId, JobStatus.ARBITRATING,
                    "仲裁者", "仲裁者正在从候选里选出唯一的复活点…", 70));
            ResurrectionPlan plan = arbiter.arbitrate(snapshot, opinions);
            job.setPlan(plan);
            store.publish(ProgressEvent.of(jobId, JobStatus.ARBITRATING,
                    "仲裁者",
                    "复活点已选定：%s → 复活为「%s」".formatted(plan.heartTitle(), plan.newProductName()),
                    80, plan));

            // 5. 并行生成 HTML 与 Pitch
            job.setStatus(JobStatus.GENERATING);
            store.publish(ProgressEvent.of(jobId, JobStatus.GENERATING,
                    "生成", "正在生成复活体 HTML 与企划书…", 85));

            CompletableFuture<HtmlGenerator.Result> htmlF =
                    CompletableFuture.supplyAsync(() -> htmlGen.generate(plan), pool);
            CompletableFuture<PitchDoc> pitchF =
                    CompletableFuture.supplyAsync(() -> pitchGen.generate(plan), pool);

            HtmlGenerator.Result htmlResult = htmlF.get();
            PitchDoc pitch = pitchF.get();

            job.setResurrectedHtml(htmlResult.html());
            job.setHtmlIsFallback(htmlResult.fallback());
            job.setPitch(pitch);

            job.setStatus(JobStatus.DONE);
            store.publish(ProgressEvent.of(jobId, JobStatus.DONE, "完成",
                    htmlResult.fallback() ? "已完成（HTML 走了 Level 1 兜底）" : "复活完成", 100));

        } catch (Exception e) {
            log.error("复活流程失败: {}", e.getMessage(), e);
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            store.publish(ProgressEvent.tick(jobId, JobStatus.FAILED, "失败",
                    "流程异常：" + e.getMessage()));
        }
    }

    private static Object snapshotBrief(ProjectSnapshot s) {
        return java.util.Map.of(
                "name", s.projectName(),
                "totalFiles", s.totalFiles(),
                "languages", s.detectedLanguages(),
                "frameworks", s.detectedFrameworks(),
                "todos", s.todoHits().size(),
                "deathSignals", s.deathSignals().size()
        );
    }

    /** 为让 workDir 的调用方能从这里拿到 archive 存放位置。 */
    public static Path defaultUploadDir(String base, String jobId) {
        return Paths.get(base, jobId);
    }
}
