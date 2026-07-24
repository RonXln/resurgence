package com.advx.resurrect.agent;

import com.advx.resurrect.service.llm.DeepSeekClient;
import org.springframework.stereotype.Component;

/** 第二阶段：将产品诊断收敛为可传播的市场切口。 */
@Component
public class GrowthLeadAgent extends BaseAgent {

    public GrowthLeadAgent(DeepSeekClient llm) { super(llm); }

    @Override public String name() { return "增长负责人"; }
    @Override public String role() { return "判断目标人群、差异化定位与首个增长切口"; }

    @Override
    protected String systemPrompt() {
        return """
                你是一名增长负责人。你会收到产品经理和技术负责人的上游结论，基于这些约束判断什么人会最先需要这个复活后的产品。
                给出具体细分用户、差异化定位、第一句价值主张和最可行的首个传播/获客切口。
                candidates 中给出 1-3 个可验证的定位方案，并说明它们分别依赖哪项项目资产。
                evidence 必须引用项目中的真实线索；不要编造市场数据或广泛人群。
                """;
    }
}
