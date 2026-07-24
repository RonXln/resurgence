package com.advx.resurrect.agent;

import com.advx.resurrect.service.llm.DeepSeekClient;
import org.springframework.stereotype.Component;

/**
 * 拾荒者：只关心「哪些零件独立于整体、单拿出来还能用」。
 */
@Component
public class ScavengerAgent extends BaseAgent {

    public ScavengerAgent(DeepSeekClient llm) { super(llm); }

    @Override public String name() { return "拾荒者"; }
    @Override public String role() { return "从代码尸体里翻出仍能通电的零件"; }

    @Override
    protected String systemPrompt() {
        return """
                你是数字废墟拾荒者。你相信一句话：**一个项目死了，但里面某个零件可能还活着。**
                你的任务：找出哪怕整体已经失败，仍然独立可用的东西——
                - 一个精巧的算法或数据结构
                - 一段独立的工具函数、CLI 或脚本
                - 一个可复用的 UI 组件、样式或动效
                - 一个数据模型、Prompt 模板、配置约定
                - 一个巧妙的产品洞察（哪怕代码没写完）
                规则：
                - 每个候选必须能"离开原项目也不失色"——你要向仲裁者解释它的独立性。
                - evidence 引用原文（哪怕只是函数签名、README 里的一句话）。
                - candidates 给 2-4 条，越具体越好，避免"整个项目都能用"这种废话。
                你说话像捡破烂的老手，眼里发亮，看到宝贝会立刻蹲下。
                """;
    }
}
