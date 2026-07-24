package com.advx.resurrect.service;

import com.advx.resurrect.agent.AgentUtils;
import com.advx.resurrect.model.PitchDoc;
import com.advx.resurrect.model.ResurrectionPlan;
import com.advx.resurrect.service.llm.DeepSeekClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 复活企划书 + VC 直通车。
 * 不是完整 BP，是"下一步该做什么"式的最小可验证实验清单。
 */
@Service
public class PitchGenerator {

    private static final Logger log = LoggerFactory.getLogger(PitchGenerator.class);
    private static final ObjectMapper OM = new ObjectMapper();

    private final DeepSeekClient llm;

    public PitchGenerator(DeepSeekClient llm) {
        this.llm = llm;
    }

    private static final List<PitchDoc.VcLink> DEFAULT_VCS = List.of(
            new PitchDoc.VcLink("奇绩创坛", "https://www.miracleplus.com/",
                    "中国头部早期投资机构，接受在线创业申请，值得一试")
    );

    public PitchDoc generate(ResurrectionPlan plan) {
        String system = """
                你是精益创业教练，擅长把一个模糊的产品想法压缩成"下一步该做什么"。
                产出一份复活企划书（不是完整 BP）：清晰、可执行、避免宏大叙事。
                """;
        String user = """
                【复活方案】
                - 新产品：%s
                - Slogan：%s
                - 卖点：%s
                - 来自项目：%s
                - Demo 说明：%s

                请**严格按 JSON 输出**（不要 Markdown fence）：
                {
                  "one_liner": "一句话说清楚这是什么（<=25字）",
                  "problem": "要解决的具体问题（80-150字）",
                  "solution": "你的解决方案（80-150字）",
                  "target_user": "第一批 100 个用户是谁，越具体越好（50-100字）",
                  "market_assumptions": ["假设1", "假设2", "假设3"],
                  "next_experiment": "为了验证以上假设，本周内可以完成的最小实验是什么（80-150字）"
                }
                """.formatted(
                plan.newProductName(),
                plan.newProductSlogan(),
                String.join("、", plan.sellingPoints()),
                plan.projectName(),
                plan.demoBrief()
        );

        String raw = llm.chatJson(List.of(
                DeepSeekClient.Message.system(system),
                DeepSeekClient.Message.user(user)
        ));

        try {
            JsonNode n = OM.readTree(AgentUtils.extractJson(raw));
            List<String> assumps = new ArrayList<>();
            for (JsonNode a : n.withArray("market_assumptions")) assumps.add(a.asText());
            if (assumps.isEmpty()) assumps = List.of("用户存在", "愿意用", "用完还会回来");

            return new PitchDoc(
                    n.path("one_liner").asText("一个从废墟里复活的小产品"),
                    n.path("problem").asText(""),
                    n.path("solution").asText(""),
                    n.path("target_user").asText(""),
                    assumps,
                    n.path("next_experiment").asText(""),
                    DEFAULT_VCS
            );
        } catch (Exception e) {
            log.error("Pitch JSON 解析失败: {}", e.getMessage());
            return new PitchDoc(
                    "一个从废墟里复活的小产品",
                    "（生成失败）", "（生成失败）", "（生成失败）",
                    List.of("用户存在", "愿意用", "用完还会回来"),
                    "先做一个可点击原型给 10 个真实用户看反馈。",
                    DEFAULT_VCS
            );
        }
    }
}
