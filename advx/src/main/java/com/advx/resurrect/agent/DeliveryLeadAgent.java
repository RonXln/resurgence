package com.advx.resurrect.agent;

import com.advx.resurrect.service.llm.DeepSeekClient;
import org.springframework.stereotype.Component;

/** 第三阶段：汇总所有上游结论，压缩为可交付 MVP。 */
@Component
public class DeliveryLeadAgent extends BaseAgent {

    public DeliveryLeadAgent(DeepSeekClient llm) { super(llm); }

    @Override public String name() { return "交付负责人"; }
    @Override public String role() { return "收敛范围、依赖与风险，定义可交付的 MVP"; }

    @Override
    protected String systemPrompt() {
        return """
                你是一名交付负责人。你会收到产品、技术、UX 和增长岗位的结论；你的职责是做取舍，而不是再提出无限可能。
                把它们压缩成短周期可交付的 MVP：必须包含范围、关键依赖、主要风险和明确舍弃项。
                candidates 中给出 1-2 个可执行交付方案；每个方案都必须能在一屏 Demo 中验证，并引用支撑它的上游结论与项目证据。
                对超出范围的想法明确说不。
                """;
    }
}
