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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 编排：解压 → 读取 → 分阶段岗位协作 → 产品决策 → HTML + Pitch。
 */
@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final ArchiveExtractor extractor;
    private final ProjectReader reader;
    private final ProductManagerAgent productManager;
    private final TechLeadAgent techLead;
    private final UxDesignerAgent uxDesigner;
    private final GrowthLeadAgent growthLead;
    private final DeliveryLeadAgent deliveryLead;
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
                             ProductManagerAgent productManager,
                             TechLeadAgent techLead,
                             UxDesignerAgent uxDesigner,
                             GrowthLeadAgent growthLead,
                             DeliveryLeadAgent deliveryLead,
                             ArbiterAgent arbiter,
                             HtmlGenerator htmlGen,
                             PitchGenerator pitchGen,
                             JobStore store) {
        this.extractor = extractor;
        this.reader = reader;
        this.productManager = productManager;
        this.techLead = techLead;
        this.uxDesigner = uxDesigner;
        this.growthLead = growthLead;
        this.deliveryLead = deliveryLead;
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

            // 3. 岗位协作工作流：先诊断，再设计/增长，最后收敛交付。
            job.setStatus(JobStatus.ANALYZING);
            store.publish(ProgressEvent.of(jobId, JobStatus.ANALYZING, "工作流",
                    "第一阶段：产品经理与技术负责人并行诊断", 40));
            List<AgentOpinion> discovery = runStage(job, snapshot,
                    List.of(productManager, techLead), List.of());
            store.publish(ProgressEvent.of(jobId, JobStatus.ANALYZING, "工作流",
                    "第二阶段：UX 设计与增长策略并行展开", 55));
            List<AgentOpinion> solution = runStage(job, snapshot,
                    List.of(uxDesigner, growthLead), discovery);

            List<AgentOpinion> upstream = new ArrayList<>(discovery);
            upstream.addAll(solution);
            store.publish(ProgressEvent.of(jobId, JobStatus.ANALYZING, "工作流",
                    "第三阶段：交付负责人正在收敛 MVP 范围", 70));
            List<AgentOpinion> delivery = runStage(job, snapshot, List.of(deliveryLead), upstream);

            List<AgentOpinion> opinions = new ArrayList<>(upstream);
            opinions.addAll(delivery);
            store.publish(ProgressEvent.of(jobId, JobStatus.ANALYZING, "工作流", "五位负责人已完成协作", 76));

            // 4. 仲裁
            job.setStatus(JobStatus.ARBITRATING);
            store.publish(ProgressEvent.of(jobId, JobStatus.ARBITRATING,
                    arbiter.name(), "产品负责人正在选择唯一的复活方向…", 80));
            ResurrectionPlan plan = arbiter.arbitrate(snapshot, opinions);
            job.setPlan(plan);
            store.publish(ProgressEvent.of(jobId, JobStatus.ARBITRATING,
                    arbiter.name(),
                    "复活点已选定：%s → 复活为「%s」".formatted(plan.heartTitle(), plan.newProductName()),
                    86, plan));

            // 5. 并行生成 HTML 与 Pitch
            job.setStatus(JobStatus.GENERATING);
            store.publish(ProgressEvent.of(jobId, JobStatus.GENERATING,
                    "生成", "正在生成复活体 HTML 与企划书…", 90));

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

    /** 同一阶段的岗位并行执行；下一阶段只能读取本阶段全部完成后的结论。 */
    private List<AgentOpinion> runStage(JobState job, ProjectSnapshot snapshot,
                                        List<Agent> agents, List<AgentOpinion> upstreamOpinions) {
        List<AgentOpinion> context = List.copyOf(upstreamOpinions);
        List<CompletableFuture<AgentOpinion>> futures = agents.stream()
                .map(agent -> CompletableFuture.supplyAsync(() -> {
                    store.publish(ProgressEvent.tick(job.getJobId(), JobStatus.ANALYZING,
                            agent.name(), agent.name() + " 正在梳理项目线索…"));
                    AgentOpinion opinion = agent.analyze(snapshot, context);
                    job.addOpinion(opinion);
                    store.publish(ProgressEvent.withPayload(job.getJobId(), JobStatus.ANALYZING,
                            agent.name(), agent.name() + " 已提交结论", opinion));
                    return opinion;
                }, pool))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return futures.stream().map(CompletableFuture::join).collect(Collectors.toList());
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
