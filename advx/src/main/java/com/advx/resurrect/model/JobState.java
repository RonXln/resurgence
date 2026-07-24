package com.advx.resurrect.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 任务状态：贯穿整个复活流程的完整数据包。
 */
public class JobState {
    private final String jobId;
    private final Instant createdAt;
    private volatile JobStatus status = JobStatus.PENDING;
    private volatile String errorMessage;

    // 中间产物
    private volatile String uploadedFilePath;
    private volatile String extractedDir;
    private volatile ProjectSnapshot snapshot;
    private final List<AgentOpinion> opinions = new CopyOnWriteArrayList<>();
    private volatile ResurrectionPlan plan;
    private volatile String resurrectedHtml;   // Level 2 产出：可以塞进 iframe 的 HTML
    private volatile boolean htmlIsFallback;   // 是否兜底到 Level 1
    private volatile PitchDoc pitch;

    public JobState(String jobId) {
        this.jobId = jobId;
        this.createdAt = Instant.now();
    }

    public String getJobId() { return jobId; }
    public Instant getCreatedAt() { return createdAt; }
    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getUploadedFilePath() { return uploadedFilePath; }
    public void setUploadedFilePath(String uploadedFilePath) { this.uploadedFilePath = uploadedFilePath; }
    public String getExtractedDir() { return extractedDir; }
    public void setExtractedDir(String extractedDir) { this.extractedDir = extractedDir; }

    public ProjectSnapshot getSnapshot() { return snapshot; }
    public void setSnapshot(ProjectSnapshot snapshot) { this.snapshot = snapshot; }

    public List<AgentOpinion> getOpinions() { return new ArrayList<>(opinions); }
    public void addOpinion(AgentOpinion opinion) { opinions.add(opinion); }

    public ResurrectionPlan getPlan() { return plan; }
    public void setPlan(ResurrectionPlan plan) { this.plan = plan; }

    public String getResurrectedHtml() { return resurrectedHtml; }
    public void setResurrectedHtml(String resurrectedHtml) { this.resurrectedHtml = resurrectedHtml; }
    public boolean isHtmlIsFallback() { return htmlIsFallback; }
    public void setHtmlIsFallback(boolean htmlIsFallback) { this.htmlIsFallback = htmlIsFallback; }

    public PitchDoc getPitch() { return pitch; }
    public void setPitch(PitchDoc pitch) { this.pitch = pitch; }
}
