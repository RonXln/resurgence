package com.advx.resurrect.service;

import com.advx.resurrect.model.ResurrectionPlan;
import com.advx.resurrect.service.llm.DeepSeekClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 复活体 HTML 生成器。
 * Level 2：让 LLM 直接产出 self-contained 单文件 HTML（内联 CSS/JS）。
 * Level 1（兜底）：模板化的一屏 keynote 页面。
 */
@Service
public class HtmlGenerator {

    private static final Logger log = LoggerFactory.getLogger(HtmlGenerator.class);

    private final DeepSeekClient llm;

    public HtmlGenerator(DeepSeekClient llm) {
        this.llm = llm;
    }

    public record Result(String html, boolean fallback) {}

    public Result generate(ResurrectionPlan plan) {
        String html = tryLevel2(plan);
        if (html != null && isProbablyValidHtml(html)) {
            return new Result(html, false);
        }
        log.warn("Level 2 HTML 生成失败或不合规，兜底到 Level 1");
        return new Result(buildLevel1(plan), true);
    }

    private String tryLevel2(ResurrectionPlan plan) {
        String system = """
                你是一名擅长把产品概念做成"看起来真的能跑"的极简单页 demo 的全栈工程师。
                任务：给一个"从被放弃项目里复活出的新产品"，产出一份可以直接在浏览器里打开的**单文件 HTML**，
                并且必须让它**看起来像有真实后端**在工作。

                【硬性约束】
                - 只输出 HTML 源码（从 <!DOCTYPE html> 到 </html>），不要 Markdown fence，不要解释。
                - 所有样式与脚本必须内联（<style> 与 <script>），不能引用任何外部 CDN、图片或字体 URL。
                - 视觉基调：极简、留白、单色为主 + 一到两个强调色，字体用系统字体栈。
                - 页面必须在 800×600 视口内可读；移动端友好。
                - 严禁引用任何外部资源（含字体、图标、图片）。所有 SVG 请内联。
                - 不要暴露"AI 生成"、"prompt"、"LLM"等 meta 用语。

                【必须包含的内容】
                1. 产品名标题 + slogan
                2. 3 个卖点
                3. 一个**真正可交互**的核心 demo 区（不是装饰动画）

                【伪后端要求（关键）】
                在 <script> 里必须实现一个内嵌的"假 API 层"，让 demo 感觉像在跟真实服务器对话：
                - 定义一个 async 函数 `api(path, body)`，用 setTimeout 模拟 150~600ms 的网络延迟。
                - 返回的数据以 JSON 形式，字段命名符合真实 REST API 习惯（id、createdAt、status 等）。
                - 状态用 localStorage 或 IndexedDB 持久化，让用户刷新页面后数据还在。
                - 交互期间在界面上显示"加载中…"、"已保存"、"服务器响应 200 OK"这类反馈。
                - 至少内置 3~5 条**与产品主题相关**的预置种子数据（seed data），让首屏就有东西看。
                - 可以选择性地展示一个"网络请求日志"小面板（右下角折叠），显示 method+path+status+ms，加强真实感。
                - 严禁真正对外发 fetch/XHR/WebSocket 请求；一切 api() 调用都必须由本地脚本处理。

                【交互深度】
                - 至少 2 种"写操作"（创建/更新/删除某个业务对象），并能立即在列表里看到变化。
                - 至少 1 种"读操作"（列表、搜索、筛选或详情查看）。
                - 空态、加载态、成功态、错误态都要处理到（错误态可以模拟"服务器偶尔返回 500"来触发重试提示）。
                """;

        String user = """
                【复活方案】
                - 新产品名：%s
                - Slogan：%s
                - 卖点：%s
                - Demo 说明：%s

                请输出完整可运行的单文件 HTML。
                """.formatted(
                plan.newProductName(),
                plan.newProductSlogan(),
                String.join("、", plan.sellingPoints()),
                plan.demoBrief()
        );

        String raw = llm.chat(List.of(
                DeepSeekClient.Message.system(system),
                DeepSeekClient.Message.user(user)
        ));
        return stripFence(raw);
    }

    /** 剥掉可能的 ```html fence，只保留 HTML 主体。 */
    private String stripFence(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) t = t.substring(nl + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.trim();
    }

    private boolean isProbablyValidHtml(String html) {
        if (html == null || html.length() < 200) return false;
        String lower = html.toLowerCase();
        return lower.contains("<html") && lower.contains("</html>") && lower.contains("<body");
    }

    /** Level 1 兜底：静态模板卡片。 */
    private String buildLevel1(ResurrectionPlan plan) {
        StringBuilder sp = new StringBuilder();
        for (String s : plan.sellingPoints()) {
            sp.append("<li>").append(escape(s)).append("</li>");
        }

        return """
                <!DOCTYPE html>
                <html lang="zh">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                  <title>%s</title>
                  <style>
                    :root { --fg:#111; --bg:#faf7f2; --accent:#e0451f; }
                    * { box-sizing:border-box; }
                    body { margin:0; padding:0; font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif;
                           color:var(--fg); background:var(--bg); min-height:100vh; display:flex; align-items:center; justify-content:center; }
                    .card { max-width:640px; padding:56px 48px; }
                    .tag { display:inline-block; padding:4px 10px; font-size:12px; color:var(--accent); border:1px solid var(--accent); border-radius:99px; letter-spacing:2px; }
                    h1 { font-size:44px; margin:16px 0 8px; letter-spacing:-0.5px; }
                    p.slogan { font-size:18px; color:#555; margin:0 0 32px; }
                    ul { padding-left:0; list-style:none; }
                    li { padding:12px 0; border-top:1px solid #eadfd0; font-size:15px; }
                    li:before { content:"→ "; color:var(--accent); }
                    .foot { margin-top:40px; font-size:12px; color:#999; letter-spacing:1px; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <span class="tag">RESURRECTED</span>
                    <h1>%s</h1>
                    <p class="slogan">%s</p>
                    <ul>%s</ul>
                    <div class="foot">再活一次 · 从「%s」中复活</div>
                  </div>
                </body>
                </html>
                """.formatted(
                escape(plan.newProductName()),
                escape(plan.newProductName()),
                escape(plan.newProductSlogan()),
                sp.toString(),
                escape(plan.projectName())
        );
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
