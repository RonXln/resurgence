package com.advx.resurrect.agent;

import com.advx.resurrect.model.AgentOpinion;
import com.advx.resurrect.model.ProjectSnapshot;
import com.advx.resurrect.model.ResurrectionPlan;
import com.advx.resurrect.service.llm.DeepSeekClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 产品负责人：读五位岗位负责人的观点，选唯一的复活点，产出 ResurrectionPlan。
 */
@Component
public class ArbiterAgent {

    private static final Logger log = LoggerFactory.getLogger(ArbiterAgent.class);
    private static final ObjectMapper OM = new ObjectMapper();

    private final DeepSeekClient llm;

    public ArbiterAgent(DeepSeekClient llm) {
        this.llm = llm;
    }

    public String name() { return "产品负责人"; }

    public ResurrectionPlan arbitrate(ProjectSnapshot snapshot, List<AgentOpinion> opinions) {
        String opinionsBlock = buildOpinionsBlock(opinions);
        String briefSnapshot = AgentUtils.buildSnapshotBrief(snapshot, 3000);

        String system = """
                你是复活系统的产品负责人。五位岗位负责人（产品经理、技术负责人、UX 设计师、增长负责人、交付负责人）已经按工作流完成分析。
                你的任务：**只选择一个**最值得交付的"复活点"，为它写一份复活方案。
                原则：
                - 同时衡量用户价值、技术可行性、体验辨识度、增长空间和交付成本。
                - 复活后的产品要**极小、极锋利、能一屏内演示**。
                - 你的选择必须引用岗位结论，并解释为什么舍弃其它候选。
                - 复活后产品名要有一丝诗意但不夸张，slogan 一句话直击要害。
                """;

        String user = """
                【原项目快照（精简）】
                %s

                【五位岗位负责人的观点】
                %s

                请**严格按 JSON 输出**（不要 Markdown fence），字段：
                {
                  "project_name": "原项目名",
                  "death_diagnosis": "死亡诊断书正文，Markdown 允许，200-400 字，引用其他 Agent 的关键观点",
                  "heart_title": "被选中的那个'活着的点'名称",
                  "heart_why": "为什么选它（150 字以内），点名它来自谁的建议",
                  "heart_source_hint": "来自原项目哪个文件/模块（引用真实路径）",
                  "new_product_name": "复活后新产品名（4-10 字）",
                  "new_product_slogan": "一句话 slogan（<=20 字）",
                  "selling_points": ["卖点1", "卖点2", "卖点3"],
                  "demo_brief": "一段给 HTML 生成器的说明：这个 demo 要呈现什么、包含哪些交互元素、视觉基调如何。300-500 字，具体到界面元素。"
                }
                """.formatted(briefSnapshot, opinionsBlock);

        String raw = llm.chatJson(List.of(
                DeepSeekClient.Message.system(system),
                DeepSeekClient.Message.user(user)
        ));

        try {
            JsonNode n = OM.readTree(AgentUtils.extractJson(raw));
            List<String> sps = new ArrayList<>();
            for (JsonNode s : n.withArray("selling_points")) sps.add(s.asText());
            if (sps.isEmpty()) sps = List.of("小而锋利", "承接旧灵感", "一屏可用");

            return new ResurrectionPlan(
                    n.path("project_name").asText(snapshot.projectName()),
                    n.path("death_diagnosis").asText("（缺失）"),
                    n.path("heart_title").asText("未命名的心脏"),
                    n.path("heart_why").asText(""),
                    n.path("heart_source_hint").asText(""),
                    n.path("new_product_name").asText("再活一次"),
                    n.path("new_product_slogan").asText("旧代码，新命运"),
                    sps,
                    n.path("demo_brief").asText("一个简洁的单页 demo，展示复活后的核心能力。")
            );
        } catch (Exception e) {
            log.error("仲裁 JSON 解析失败: {}", e.getMessage());
            return new ResurrectionPlan(
                    snapshot.projectName(),
                    "（仲裁失败，原始输出）：" + (raw.length() > 500 ? raw.substring(0, 500) + "…" : raw),
                    "未命名的心脏", "", "",
                    "再活一次", "旧代码，新命运",
                    List.of("小而锋利", "承接旧灵感", "一屏可用"),
                    "简洁单页 demo。"
            );
        }
    }

    private String buildOpinionsBlock(List<AgentOpinion> opinions) {
        StringBuilder sb = new StringBuilder();
        for (AgentOpinion op : opinions) {
            sb.append("\n### ").append(op.agentName()).append("（").append(op.role()).append("）\n");
            sb.append("观点：").append(op.summary()).append('\n');
            if (op.evidence() != null && !op.evidence().isEmpty()) {
                sb.append("证据：\n");
                for (AgentOpinion.Evidence e : op.evidence()) {
                    sb.append("  - [").append(e.filePath()).append("] \"")
                            .append(e.quote()).append("\" —— ").append(e.reason()).append('\n');
                }
            }
            if (op.candidates() != null && !op.candidates().isEmpty()) {
                sb.append("候选：\n");
                for (AgentOpinion.Candidate c : op.candidates()) {
                    sb.append("  - 【").append(c.title()).append("】")
                            .append(c.rationale()).append("（来源：").append(c.sourceHint()).append("）\n");
                }
            }
        }
        return sb.toString();
    }
}
