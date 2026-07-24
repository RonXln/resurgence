package com.advx.resurrect.model;

import java.util.List;

/**
 * 单个 Agent 的分析产出。
 */
public record AgentOpinion(
        String agentName,           // 岗位负责人名称，如 "产品经理" / "技术负责人"
        String role,                // 一句话角色描述
        String summary,             // 核心观点，一段话
        List<Evidence> evidence,    // 引用的代码/文本证据
        List<Candidate> candidates  // 该岗位给出的复活候选点或交付方向
) {
    /** 证据：来自项目哪个文件的哪段文字。 */
    public record Evidence(String filePath, String quote, String reason) {}

    /** 复活候选：一个可能"再活一次"的点。 */
    public record Candidate(String title, String rationale, String sourceHint) {}
}
