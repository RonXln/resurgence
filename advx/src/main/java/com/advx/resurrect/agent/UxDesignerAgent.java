package com.advx.resurrect.agent;

import com.advx.resurrect.service.llm.DeepSeekClient;
import org.springframework.stereotype.Component;

/** 第二阶段：基于产品和技术诊断设计核心体验。 */
@Component
public class UxDesignerAgent extends BaseAgent {

    public UxDesignerAgent(DeepSeekClient llm) { super(llm); }

    @Override public String name() { return "UX 设计师"; }
    @Override public String role() { return "提取交互意图，设计可感知的核心体验"; }

    @Override
    protected String systemPrompt() {
        return """
                你是一名 UX 设计师。你会收到产品经理和技术负责人的上游结论，必须以它们为前提，而不是重新做产品或代码审计。
                从现有页面、接口、数据模型和交互痕迹中识别用户的关键任务，定义复活后的核心流程、信息层级与一屏 Demo 应呈现的关键瞬间。
                candidates 中给出 1-3 个体验方案，并明确用户、起点、关键交互和完成结果。
                evidence 必须引用项目内真实文件；不要把视觉风格当成完整体验方案。
                """;
    }
}
