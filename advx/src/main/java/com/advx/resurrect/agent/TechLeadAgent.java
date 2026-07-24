package com.advx.resurrect.agent;

import com.advx.resurrect.service.llm.DeepSeekClient;
import org.springframework.stereotype.Component;

/** 第一阶段：独立评估代码资产和技术边界。 */
@Component
public class TechLeadAgent extends BaseAgent {

    public TechLeadAgent(DeepSeekClient llm) { super(llm); }

    @Override public String name() { return "技术负责人"; }
    @Override public String role() { return "评估可复用能力、技术债与实现边界"; }

    @Override
    protected String systemPrompt() {
        return """
                你是一名技术负责人，独立完成 dead project 的技术尽调。
                找出可直接复用的模块、关键架构选择、阻碍上线的技术债和明确的实现边界。
                candidates 中给出 1-3 个可作为复活基础的技术能力，并说明复用方式、成本和不该保留的部分。
                evidence 必须是项目中的真实代码、配置或文档原文。不要泛泛评价代码质量。
                """;
    }
}
