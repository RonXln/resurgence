package com.advx.resurrect.model;

import java.util.List;

/**
 * 产品负责人产出的复活方案。
 */
public record ResurrectionPlan(
        String projectName,
        String deathDiagnosis,     // 死亡诊断书正文（Markdown）
        String heartTitle,         // "复活的心脏"标题：那个被选中的活着的点
        String heartWhy,           // 为什么选它
        String heartSourceHint,    // 来自原项目哪个文件/模块
        String newProductName,     // 复活后新产品的名字
        String newProductSlogan,   // 一句话 slogan
        List<String> sellingPoints,// 3 个卖点
        String demoBrief           // 给 HTML 生成器的 demo 说明
) {}
