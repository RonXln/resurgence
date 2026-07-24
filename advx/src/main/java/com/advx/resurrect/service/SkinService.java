package com.advx.resurrect.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 皮肤服务：为 LLM 生成的原始 HTML 注入"皮肤切换 runtime"。
 *
 * 设计原则：不能依赖 LLM 用什么 CSS 变量名，所以采用**根节点 CSS filter + accent 变量覆盖**方案，
 * 对任何 HTML 都能工作。
 *
 * 皮肤实现方式：给 <html> 加类 rz-skin-<name>，用 filter 改变整体色调；
 * 同时定义几个可选覆盖变量（--rz-accent 等），供 demo 里主动引用（可选）。
 */
@Service
public class SkinService {

    /** 皮肤定义。id 用小写英文；label 是给用户看的中文名。 */
    public record Skin(String id, String label, String description) {}

    public static final List<Skin> SKINS = List.of(
            new Skin("paper",  "纸本原味", "保留 AI 现场生成的原始配色"),
            new Skin("noir",   "深夜反色", "整体反色 + 暖色回填，适合暗光"),
            new Skin("sunset", "落日暖橙", "整体偏暖，营造黄昏感"),
            new Skin("mint",   "薄荷冷绿", "偏冷，像医院/实验室"),
            new Skin("mono",   "极简黑白", "去饱和度，只留结构")
    );

    private static final Map<String, String> SKIN_FILTERS = Map.of(
            "paper",  "none",
            "noir",   "invert(1) hue-rotate(180deg) contrast(0.95)",
            "sunset", "hue-rotate(-20deg) saturate(1.2) brightness(1.02)",
            "mint",   "hue-rotate(85deg) saturate(0.85)",
            "mono",   "grayscale(1) contrast(1.05)"
    );

    /** 校验并归一化。未知皮肤退回 paper。 */
    public String normalize(String skin) {
        if (skin == null) return "paper";
        String s = skin.trim().toLowerCase();
        return SKIN_FILTERS.containsKey(s) ? s : "paper";
    }

    /**
     * 把皮肤 runtime 注入 LLM 生成的 HTML。
     * 追加一段 &lt;style&gt; + &lt;script&gt; 到 &lt;/head&gt; 前。
     * 若找不到 &lt;/head&gt;，则加在 &lt;body&gt; 之前的合适位置；再不行就在文档最前面。
     */
    public String inject(String rawHtml, String initialSkin) {
        String skin = normalize(initialSkin);
        String block = buildInjection(skin);

        if (rawHtml == null || rawHtml.isBlank()) return rawHtml;

        // 优先插到 </head> 之前
        int idx = indexOfIgnoreCase(rawHtml, "</head>");
        if (idx >= 0) {
            return rawHtml.substring(0, idx) + block + rawHtml.substring(idx);
        }
        // 再退：插到 <body 之前
        idx = indexOfIgnoreCase(rawHtml, "<body");
        if (idx >= 0) {
            return rawHtml.substring(0, idx) + block + rawHtml.substring(idx);
        }
        // 都没有，就贴在最前
        return block + rawHtml;
    }

    private String buildInjection(String initialSkin) {
        StringBuilder filters = new StringBuilder();
        for (Map.Entry<String, String> e : SKIN_FILTERS.entrySet()) {
            filters.append("html.rz-skin-").append(e.getKey())
                    .append(" body { filter: ").append(e.getValue()).append("; }\n");
        }

        // language=HTML
        return """
                <style id="rz-skin-style">
                %s
                html { transition: filter 0.35s ease; }
                /* 隔离一些常见的\"必须真彩\"元素，避免被 filter 破坏 */
                html.rz-skin-noir body img,
                html.rz-skin-noir body video { filter: invert(1) hue-rotate(180deg); }
                </style>
                <script id="rz-skin-runtime">
                (function () {
                  var VALID = %s;
                  function apply(skin) {
                    if (!skin || VALID.indexOf(skin) === -1) skin = 'paper';
                    var html = document.documentElement;
                    VALID.forEach(function (s) { html.classList.remove('rz-skin-' + s); });
                    html.classList.add('rz-skin-' + skin);
                    try { localStorage.setItem('rz-skin', skin); } catch (_) {}
                    window.__rzSkin = skin;
                  }
                  // 初始：URL ?skin=xxx > localStorage > 服务端注入的 initialSkin
                  var initial = %s;
                  try {
                    var q = new URLSearchParams(location.search).get('skin');
                    if (q) initial = q;
                    else {
                      var stored = localStorage.getItem('rz-skin');
                      if (stored) initial = stored;
                    }
                  } catch (_) {}
                  apply(initial);
                  // 接收父窗口 postMessage 切皮肤
                  window.addEventListener('message', function (e) {
                    if (!e.data || typeof e.data !== 'object') return;
                    if (e.data.type === 'rz:setSkin') apply(e.data.skin);
                  });
                })();
                </script>
                """.formatted(
                filters.toString(),
                toJsArray(SKIN_FILTERS.keySet()),
                jsString(initialSkin)
        );
    }

    private static String toJsArray(Iterable<String> items) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String s : items) {
            if (!first) sb.append(',');
            sb.append(jsString(s));
            first = false;
        }
        return sb.append(']').toString();
    }

    private static String jsString(String s) {
        if (s == null) return "''";
        String escaped = s.replace("\\", "\\\\").replace("'", "\\'");
        return "'" + escaped + "'";
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase().indexOf(needle.toLowerCase());
    }
}
