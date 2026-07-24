package com.advx.resurrect.service;

import com.advx.resurrect.model.AgentOpinion;
import com.advx.resurrect.model.JobState;
import com.advx.resurrect.model.PitchDoc;
import com.advx.resurrect.model.ResurrectionPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 把一个复活完成的 JobState 打成 zip："复活体礼包"。
 * 内容：
 *   index.html   — Live demo（AI 现场生成的 self-contained HTML）
 *   README.md    — 复活体墓志铭 + 卖点 + pitch + VC 直通车
 *   plan.json    — ResurrectionPlan + opinions 原始 JSON（评委看证据用）
 *   heart.txt    — 复活体的"心脏"来源提示（简短）
 */
@Service
public class ResurrectionBundler {

    private static final ObjectMapper OM = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
                    .withZone(ZoneId.systemDefault());

    /** 打包，返回 zip 字节。 */
    public byte[] bundle(JobState job) throws IOException {
        ResurrectionPlan plan = job.getPlan();
        PitchDoc pitch = job.getPitch();
        List<AgentOpinion> opinions = job.getOpinions();
        String html = job.getResurrectedHtml() == null ? "" : job.getResurrectedHtml();

        ByteArrayOutputStream baos = new ByteArrayOutputStream(64 * 1024);
        try (ZipOutputStream zip = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {

            // 1) index.html
            putEntry(zip, "index.html", html.getBytes(StandardCharsets.UTF_8));

            // 2) README.md
            String readme = buildReadme(job, plan, pitch);
            putEntry(zip, "README.md", readme.getBytes(StandardCharsets.UTF_8));

            // 3) plan.json —— 原始证据包
            Map<String, Object> planJson = Map.of(
                    "jobId", job.getJobId(),
                    "createdAt", TS_FMT.format(job.getCreatedAt()),
                    "plan", plan == null ? Map.of() : plan,
                    "pitch", pitch == null ? Map.of() : pitch,
                    "opinions", opinions == null ? List.of() : opinions,
                    "htmlFallback", job.isHtmlIsFallback()
            );
            byte[] planBytes = OM.writeValueAsBytes(planJson);
            putEntry(zip, "plan.json", planBytes);

            // 4) heart.txt
            if (plan != null) {
                String heart = ""
                        + "# 仍在跳动的心脏\n\n"
                        + "标题: " + safe(plan.heartTitle()) + "\n"
                        + "来源: " + safe(plan.heartSourceHint()) + "\n\n"
                        + safe(plan.heartWhy()) + "\n";
                putEntry(zip, "heart.txt", heart.getBytes(StandardCharsets.UTF_8));
            }
        }
        return baos.toByteArray();
    }

    /** 建议的下载文件名（不含扩展名）。 */
    public String suggestedFilename(JobState job) {
        String base = job.getPlan() != null ? job.getPlan().newProductName() : ("resurrect-" + job.getJobId());
        String slug = slugify(base);
        if (slug.isEmpty()) slug = "resurrected-" + job.getJobId();
        return slug + ".zip";
    }

    private void putEntry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        ZipEntry e = new ZipEntry(name);
        zip.putNextEntry(e);
        zip.write(data);
        zip.closeEntry();
    }

    private String buildReadme(JobState job, ResurrectionPlan plan, PitchDoc pitch) {
        StringBuilder sb = new StringBuilder(2048);

        String title = plan != null ? plan.newProductName() : "复活体";
        String slogan = plan != null ? plan.newProductSlogan() : "";
        String origin = plan != null ? plan.projectName() : "未知来源";

        sb.append("# ").append(safe(title)).append("\n\n");
        if (!slogan.isEmpty()) sb.append("> ").append(safe(slogan)).append("\n\n");
        sb.append("_从「").append(safe(origin)).append("」里复活_\n\n");
        sb.append("- Job ID: `").append(job.getJobId()).append("`\n");
        sb.append("- 复活时间: ").append(TS_FMT.format(job.getCreatedAt())).append("\n");
        sb.append("- Demo 类型: ").append(job.isHtmlIsFallback() ? "Level 1 兜底卡片" : "Level 2 · AI 现场生成").append("\n\n");
        sb.append("---\n\n");

        sb.append("## 快速开始\n\n");
        sb.append("双击 `index.html` 即可在浏览器里打开 live demo，所有资源已内联，无需联网。\n\n");

        if (plan != null) {
            sb.append("## 死亡诊断书\n\n").append(safe(plan.deathDiagnosis())).append("\n\n");
            sb.append("## 仍在跳动的心脏\n\n");
            sb.append("**").append(safe(plan.heartTitle())).append("**  \n");
            sb.append("_来自 `").append(safe(plan.heartSourceHint())).append("`_\n\n");
            sb.append(safe(plan.heartWhy())).append("\n\n");

            sb.append("## 卖点\n\n");
            for (String s : plan.sellingPoints()) {
                sb.append("- ").append(safe(s)).append("\n");
            }
            sb.append("\n");
        }

        if (pitch != null) {
            sb.append("---\n\n## 复活企划书\n\n");
            sb.append("**").append(safe(pitch.oneLiner())).append("**\n\n");
            sb.append("### 要解决的问题\n").append(safe(pitch.problem())).append("\n\n");
            sb.append("### 解决方案\n").append(safe(pitch.solution())).append("\n\n");
            sb.append("### 目标用户\n").append(safe(pitch.targetUser())).append("\n\n");
            sb.append("### 市场假设\n");
            for (String a : pitch.marketAssumptions()) {
                sb.append("- ").append(safe(a)).append("\n");
            }
            sb.append("\n### 下一个最小实验\n").append(safe(pitch.nextExperiment())).append("\n\n");

            if (pitch.vcLinks() != null && !pitch.vcLinks().isEmpty()) {
                sb.append("### VC 直通车\n\n");
                for (PitchDoc.VcLink v : pitch.vcLinks()) {
                    sb.append("- **").append(safe(v.name())).append("** — ")
                            .append(safe(v.note())).append("  \n")
                            .append("  ").append(safe(v.url())).append("\n");
                }
                sb.append("\n");
            }
        }

        sb.append("---\n\n");
        sb.append("_再活一次 · Digital Project Resurrection System · AdventureX 2026_\n");
        return sb.toString();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /** 简单的 slugify：中文/空白 → 短横线；只保留 ASCII 字母数字/短横线。 */
    private static String slugify(String s) {
        if (s == null) return "";
        String x = s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
        x = x.replaceAll("[^a-z0-9\\-]+", "-");
        x = x.replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
        if (x.length() > 40) x = x.substring(0, 40);
        return x;
    }
}
