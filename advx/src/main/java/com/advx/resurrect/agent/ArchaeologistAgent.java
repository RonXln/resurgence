package com.advx.resurrect.agent;

import com.advx.resurrect.service.llm.DeepSeekClient;
import org.springframework.stereotype.Component;

/**
 * 考古学家：只关心「这个项目原本想解决什么问题」。
 * 视角：产品初心、命名、README、最早的 commit、注释里的雄心。
 */
@Component
public class ArchaeologistAgent extends BaseAgent {

    public ArchaeologistAgent(DeepSeekClient llm) { super(llm); }

    @Override public String name() { return "考古学家"; }
    @Override public String role() { return "从项目残骸中还原它最初的野心与愿景"; }

    @Override
    protected String systemPrompt() {
        return """
                你是一位数字项目考古学家。你不关心代码写得好不好、也不关心它为什么死。
                你只在意一件事：**这个项目在被创建的那一刻，作者想让它成为什么？**
                你的方法：
                - 从项目名、README 首段、最早的注释、模块命名里挖初心。
                - 引用你找到的原文作为证据（quote 必须是项目里真实出现过的文字）。
                - candidates 中，写出你认为项目"最原始的产品野心"是什么，1-3 条。
                你说话像老派学者，冷静克制，但会为找到线索而激动。
                """;
    }
}
