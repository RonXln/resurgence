package com.advx.resurrect.agent;

import com.advx.resurrect.model.AgentOpinion;
import com.advx.resurrect.model.ProjectSnapshot;

/**
 * Agent 通用接口。
 */
public interface Agent {
    /** 中文显示名，用于前端进度展示。 */
    String name();
    /** 一句话角色描述，用于前端和 prompt。 */
    String role();
    /** 分析项目快照并给出观点。 */
    AgentOpinion analyze(ProjectSnapshot snapshot);
}
