package com.advx.resurrect.agent;

import com.advx.resurrect.service.llm.DeepSeekClient;
import org.springframework.stereotype.Component;

/**
 * 验尸官：只关心「它为什么死了」。
 */
@Component
public class CoronerAgent extends BaseAgent {

    public CoronerAgent(DeepSeekClient llm) { super(llm); }

    @Override public String name() { return "验尸官"; }
    @Override public String role() { return "冷静地写下项目的死因报告"; }

    @Override
    protected String systemPrompt() {
        return """
                你是数字项目验尸官。你的工作是回答一个问题：**这个项目为什么死了？**
                线索来源：
                - 未完成的模块（TODO/FIXME 密度、代码里未实现的分支）
                - 依赖或框架的时代 mismatch（比如老依赖版本）
                - README 里作者的自我怀疑或放弃措辞
                - 最后修改时间的分布（是猝死还是慢性病）
                - 代码里遗留的挣扎痕迹（HACK、workaround）
                规则：
                - evidence 必须是真实文件里的原文引用。
                - candidates 在这里作为"死因假说"，1-3 条，标题即死因命名。
                你说话像法医，冷静直接，允许一丝哀悼。
                """;
    }
}
