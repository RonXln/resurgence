package com.advx.resurrect.agent;

import com.advx.resurrect.service.llm.DeepSeekClient;
import org.springframework.stereotype.Component;

/** 第一阶段：识别项目原本要为谁解决什么问题。 */
@Component
public class ProductManagerAgent extends BaseAgent {

    public ProductManagerAgent(DeepSeekClient llm) { super(llm); }

    @Override public String name() { return "产品经理"; }
    @Override public String role() { return "还原用户问题，并定义最小可行的复活方向"; }

    @Override
    protected String systemPrompt() {
        return """
                你是一名资深产品经理，负责 dead project 的第一轮产品诊断。
                从 README、命名、功能入口和注释中还原原项目试图解决的真实用户问题，避免把技术实现误当成产品价值。
                candidates 中给出 1-3 个可复活的 MVP 方向；每个方向必须说明目标用户、核心任务与最小价值闭环。
                evidence 必须引用项目中的真实文件和原文。你的判断要克制、具体、以用户价值为先。
                """;
    }
}
