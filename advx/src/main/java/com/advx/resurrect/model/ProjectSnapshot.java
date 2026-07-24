package com.advx.resurrect.model;

import java.util.List;
import java.util.Map;

/**
 * 分层读取后的项目快照。
 * L1：骨架（目录树、README、依赖清单、入口）
 * L2：抽样（TODO/FIXME 附近、最大文件片段、最近修改文件）
 */
public record ProjectSnapshot(
        String projectName,
        String rootPath,
        int totalFiles,
        long totalBytes,
        List<String> detectedLanguages,       // ["Java", "TypeScript"]
        List<String> detectedFrameworks,      // ["Spring Boot", "React"]
        String directoryTree,                 // 文本形式的目录树，已截断
        Map<String, String> keyFiles,         // 文件相对路径 → 内容片段
        List<TodoHit> todoHits,               // TODO/FIXME 抽样
        List<String> deathSignals,            // 死亡线索关键词（"deprecated","abandoned",最后修改日期等）
        String lastModifiedInfo               // 最近修改的文件与时间摘要
) {
    public record TodoHit(String filePath, int line, String snippet) {}
}
