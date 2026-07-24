# 再活一次 · 数字项目复活系统

> AdventureX 2026 · 主题 E「反转」
> 输入一个被放弃的项目压缩包，输出一份复活体 demo + 企划书。

## 快速启动

### 1. 配置 DeepSeek API Key
在 shell 里设置环境变量（Windows PowerShell）：
```powershell
$env:DEEPSEEK_API_KEY = "sk-xxxx"
```
或直接在 [advx/src/main/resources/application.yml](advx/src/main/resources/application.yml) 里改 `resurrect.deepseek.api-key`。

### 2. 运行
```powershell
cd advx
mvn spring-boot:run
```
访问 http://localhost:8000

## 架构一句话

```
用户上传 zip
  → 安全解压（防 zip-slip / zip-bomb）
  → 分层读取（L1 骨架 + L2 抽样）
  → 4 个 Agent 并行分析
     · 考古学家：还原初心
     · 验尸官：写死因报告
     · 拾荒者：翻还能用的零件
     · 改造家：想新场景
  → 仲裁者：选唯一一个复活点
  → 并行生成：单页复活 demo (Level 2 HTML) + 复活企划书 + VC 直通车
  → 前端一屏展示
```

## 关键文件

- 入口：[ResurrectApplication.java](advx/src/main/java/com/advx/resurrect/ResurrectApplication.java)
- 编排：[AgentOrchestrator.java](advx/src/main/java/com/advx/resurrect/service/AgentOrchestrator.java)
- Agent：[agent/](advx/src/main/java/com/advx/resurrect/agent/)
- 前端：[static/index.html](advx/src/main/resources/static/index.html)
- 配置：[application.yml](advx/src/main/resources/application.yml)
