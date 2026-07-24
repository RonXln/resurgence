package com.advx.resurrect.agent;

import com.advx.resurrect.model.AgentOpinion;
import com.advx.resurrect.model.ProjectSnapshot;
import com.advx.resurrect.service.llm.DeepSeekClient;

import java.util.List;

/**
 * Agent 基类：给定 system prompt 和 role，公用 LLM 调用与 JSON 解析。
 */
public abstract class BaseAgent implements Agent {

    protected final DeepSeekClient llm;

    protected BaseAgent(DeepSeekClient llm) {
        this.llm = llm;
    }

    /** 该 Agent 的 system prompt。要求 LLM 输出 JSON。 */
    protected abstract String systemPrompt();

    /** 该 Agent 分给项目摘要多少字符预算。 */
    protected int snapshotBudget() { return 8000; }

    @Override
    public AgentOpinion analyze(ProjectSnapshot snapshot, List<AgentOpinion> upstreamOpinions) {
        String brief = AgentUtils.buildSnapshotBrief(snapshot, snapshotBudget());
        String upstream = AgentUtils.buildOpinionsBrief(upstreamOpinions);
        String user = """
                以下是一个被放弃的项目的自动化快照。请以你的角色分析它，并**严格按 JSON 输出**，不要包裹任何 Markdown fence，字段：
                {
                  "summary": "你的核心观点，一段中文，60-180字",
                  "evidence": [
                    {"file": "相对路径", "quote": "从项目中原文引用的短句（<=120字）", "reason": "为什么这段能支持你的观点"}
                  ],
                  "candidates": [
                    {"title": "候选'活着的点'名称（<=20字）", "rationale": "为什么它可以再活一次（<=100字）", "source_hint": "来自哪个文件/模块"}
                  ]
                }
                至少给 2 条 evidence 和 1-3 条 candidates。禁止捏造项目里没出现的文件名。

                === 项目快照 ===
                %s

                === 上游岗位结论 ===
                %s
                """.formatted(brief, upstream);

        String raw = llm.chatJson(List.of(
                DeepSeekClient.Message.system(systemPrompt()),
                DeepSeekClient.Message.user(user)
        ));
        return AgentUtils.parseOpinion(name(), role(), raw);
    }
}
