package com.advx.resurrect.model;

import java.util.List;

/**
 * 复活企划书 + VC 直通车包。
 */
public record PitchDoc(
        String oneLiner,               // 一句话说清项目
        String problem,                // 要解决的问题
        String solution,               // 解决方案
        String targetUser,             // 目标用户
        List<String> marketAssumptions,// 市场假设 3 条
        String nextExperiment,         // 下一个最小可验证实验
        List<VcLink> vcLinks           // VC 直通车链接
) {
    public record VcLink(String name, String url, String note) {}
}
