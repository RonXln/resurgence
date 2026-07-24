package com.advx.resurrect.model;

import java.time.Instant;

/**
 * SSE 推送给前端的进度事件。
 */
public record ProgressEvent(
        String jobId,
        JobStatus status,
        String stage,      // 阶段或岗位名称（如 "产品经理"）
        String message,    // 人可读消息
        Integer progress,  // 0-100，可空
        Object payload,    // 阶段性负载（如某 Agent 的观点摘要），可空
        Instant timestamp
) {
    public static ProgressEvent of(String jobId, JobStatus status, String stage, String message) {
        return new ProgressEvent(jobId, status, stage, message, null, null, Instant.now());
    }

    public static ProgressEvent of(String jobId, JobStatus status, String stage, String message, int progress) {
        return new ProgressEvent(jobId, status, stage, message, progress, null, Instant.now());
    }

    public static ProgressEvent of(String jobId, JobStatus status, String stage, String message, int progress, Object payload) {
        return new ProgressEvent(jobId, status, stage, message, progress, payload, Instant.now());
    }

    /** 无 progress、只带 payload 的事件。 */
    public static ProgressEvent withPayload(String jobId, JobStatus status, String stage, String message, Object payload) {
        return new ProgressEvent(jobId, status, stage, message, null, payload, Instant.now());
    }

    /** 无 progress、无 payload 的事件（stage 之外仅 message）。 */
    public static ProgressEvent tick(String jobId, JobStatus status, String stage, String message) {
        return new ProgressEvent(jobId, status, stage, message, null, null, Instant.now());
    }
}
