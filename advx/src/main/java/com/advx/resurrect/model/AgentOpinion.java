package com.advx.resurrect.model;

import java.util.List;

/**
 * 单个 Agent 的分析产出。
 */
public record AgentOpinion(
        String agentName,           // "考古学家" / "验尸官" / "拾荒者" / "改造家"
        String role,                // 一句话角色描述
        String summary,             // 核心观点，一段话
        List<Evidence> evidence,    // 引用的代码/文本证据
        List<Candidate> candidates  // 该 Agent 给出的"复活候选点"（拾荒者/改造家会给多个）
) {
    /** 证据：来自项目哪个文件的哪段文字。 */
    public record Evidence(String filePath, String quote, String reason) {}

    /** 复活候选：一个可能"再活一次"的点。 */
    public record Candidate(String title, String rationale, String sourceHint) {}
}
