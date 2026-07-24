package com.advx.resurrect.agent;

import com.advx.resurrect.model.AgentOpinion;
import com.advx.resurrect.model.ProjectSnapshot;

import java.util.List;

/**
 * Agent 通用接口。
 */
public interface Agent {
    /** 中文显示名，用于前端进度展示。 */
    String name();
    /** 一句话角色描述，用于前端和 prompt。 */
    String role();
    /**
     * 分析项目快照，并在需要时参考上游岗位已经给出的观点。
     * 空列表代表该岗位处于工作流第一阶段。
     */
    AgentOpinion analyze(ProjectSnapshot snapshot, List<AgentOpinion> upstreamOpinions);
}
