package com.advx.resurrect.model;

/**
 * 任务生命周期状态。
 */
public enum JobStatus {
    PENDING,        // 已创建，未开始
    EXTRACTING,     // 解压中
    READING,        // 读取项目文件中
    ANALYZING,      // 多 Agent 分析中
    ARBITRATING,    // 仲裁中
    GENERATING,     // 生成复活体 HTML 与企划书
    DONE,           // 完成
    FAILED          // 失败
}
