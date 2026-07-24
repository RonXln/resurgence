package com.advx.resurrect.agent;

import com.advx.resurrect.model.AgentOpinion;
import com.advx.resurrect.model.ProjectSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 公共工具：构造项目摘要 & 解析 LLM 返回的 JSON。
 */
public final class AgentUtils {

    private AgentUtils() {}

    private static final ObjectMapper OM = new ObjectMapper();

    /** 把 ProjectSnapshot 压成给 LLM 看的项目摘要字符串（含裁剪）。 */
    public static String buildSnapshotBrief(ProjectSnapshot s, int keyFileBudgetChars) {
        StringBuilder sb = new StringBuilder();
        sb.append("【项目名】").append(s.projectName()).append('\n');
        sb.append("【文件总数】").append(s.totalFiles()).append("，总字节：").append(s.totalBytes()).append('\n');
        sb.append("【检测到的语言】").append(String.join(", ", s.detectedLanguages())).append('\n');
        sb.append("【检测到的框架/构建工具】").append(String.join(", ", s.detectedFrameworks())).append('\n');
        sb.append("\n===== 目录树（截断） =====\n").append(s.directoryTree()).append('\n');

        sb.append("\n===== 关键文件与代码片段 =====\n");
        int used = 0;
        for (Map.Entry<String, String> e : s.keyFiles().entrySet()) {
            String head = "\n--- " + e.getKey() + " ---\n";
            String body = e.getValue();
            int budgetLeft = keyFileBudgetChars - used;
            if (budgetLeft <= 200) break;
            String chunk = body.length() > budgetLeft - head.length()
                    ? body.substring(0, Math.max(0, budgetLeft - head.length() - 16)) + "\n...(截断)"
                    : body;
            sb.append(head).append(chunk);
            used += head.length() + chunk.length();
        }

        if (!s.todoHits().isEmpty()) {
            sb.append("\n\n===== TODO / FIXME / HACK 抽样 =====\n");
            int cnt = 0;
            for (ProjectSnapshot.TodoHit t : s.todoHits()) {
                sb.append("• ").append(t.filePath()).append(":").append(t.line())
                        .append("  ").append(t.snippet()).append('\n');
                if (++cnt >= 25) break;
            }
        }

        if (!s.deathSignals().isEmpty()) {
            sb.append("\n\n===== 死亡关键词命中 =====\n");
            for (String d : s.deathSignals()) sb.append("• ").append(d).append('\n');
        }

        if (s.lastModifiedInfo() != null && !s.lastModifiedInfo().isBlank()) {
            sb.append("\n===== 修改时间线索 =====\n").append(s.lastModifiedInfo());
        }
        return sb.toString();
    }

    /** 将前序岗位结论压缩为后续岗位可直接引用的上下文。 */
    public static String buildOpinionsBrief(List<AgentOpinion> opinions) {
        if (opinions == null || opinions.isEmpty()) {
            return "（暂无上游结论；请独立完成第一轮诊断。）";
        }
        StringBuilder sb = new StringBuilder();
        for (AgentOpinion opinion : opinions) {
            sb.append("【").append(opinion.agentName()).append("】\n")
                    .append(opinion.summary()).append('\n');
            if (opinion.candidates() != null) {
                for (AgentOpinion.Candidate candidate : opinion.candidates()) {
                    sb.append("- ").append(candidate.title()).append("：")
                            .append(candidate.rationale()).append('\n');
                }
            }
        }
        return sb.toString();
    }

    /** 尝试从 LLM 返回中解析出 AgentOpinion；解析失败则返回一个降级 Opinion。 */
    public static AgentOpinion parseOpinion(String agentName, String role, String rawJson) {
        try {
            JsonNode n = OM.readTree(extractJson(rawJson));
            String summary = n.path("summary").asText("");
            List<AgentOpinion.Evidence> evidences = new ArrayList<>();
            for (JsonNode e : n.withArray("evidence")) {
                evidences.add(new AgentOpinion.Evidence(
                        e.path("file").asText(""),
                        e.path("quote").asText(""),
                        e.path("reason").asText("")
                ));
            }
            List<AgentOpinion.Candidate> cands = new ArrayList<>();
            for (JsonNode c : n.withArray("candidates")) {
                cands.add(new AgentOpinion.Candidate(
                        c.path("title").asText(""),
                        c.path("rationale").asText(""),
                        c.path("source_hint").asText("")
                ));
            }
            return new AgentOpinion(agentName, role, summary, evidences, cands);
        } catch (Exception ex) {
            return new AgentOpinion(agentName, role,
                    "（解析失败）原始输出：" + (rawJson.length() > 400 ? rawJson.substring(0, 400) + "…" : rawJson),
                    List.of(), List.of());
        }
    }

    /** 有些模型会在 JSON 外包裹一层 ```json fence，兜底剥离。 */
    public static String extractJson(String s) {
        if (s == null) return "{}";
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) return s.substring(start, end + 1);
        return s;
    }
}
