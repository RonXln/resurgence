package com.advx.resurrect.service.llm;

import com.advx.resurrect.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek Chat 客户端。
 * 兼容 OpenAI 规范：POST {base}/v1/chat/completions
 */
@Component
public class DeepSeekClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);

    private final AppProperties props;
    private final WebClient client;
    private final ObjectMapper om = new ObjectMapper();

    public DeepSeekClient(AppProperties props) {
        this.props = props;
        this.client = WebClient.builder()
                .baseUrl(props.getDeepseek().getBaseUrl())
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    public record Message(String role, String content) {
        public static Message system(String s) { return new Message("system", s); }
        public static Message user(String s) { return new Message("user", s); }
    }

    /** 普通 chat，返回文本内容。 */
    public String chat(List<Message> messages) {
        return chat(messages, false, 0.6);
    }

    public String chatJson(List<Message> messages) {
        return chat(messages, true, 0.3);
    }

    private String chat(List<Message> messages, boolean jsonMode, double temperature) {
        String apiKey = props.getDeepseek().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("DEEPSEEK_API_KEY 未配置，返回 mock 响应");
            return jsonMode ? "{\"mock\": true}" : "（未配置 DeepSeek API Key，这是 mock 响应）";
        }

        ObjectNode body = om.createObjectNode();
        body.put("model", props.getDeepseek().getModel());
        body.put("temperature", temperature);
        body.put("stream", false);
        if (jsonMode) {
            ObjectNode fmt = body.putObject("response_format");
            fmt.put("type", "json_object");
        }
        ArrayNode msgs = body.putArray("messages");
        for (Message m : messages) {
            ObjectNode n = msgs.addObject();
            n.put("role", m.role());
            n.put("content", m.content());
        }

        try {
            Mono<JsonNode> mono = client.post()
                    .uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body.toString())
                    .retrieve()
                    .bodyToMono(JsonNode.class);
            JsonNode resp = mono.block(Duration.ofSeconds(props.getDeepseek().getTimeoutSeconds()));
            if (resp == null) throw new IllegalStateException("DeepSeek 返回为空");
            JsonNode choice = resp.path("choices").path(0).path("message").path("content");
            return choice.asText("");
        } catch (Exception e) {
            log.error("DeepSeek 调用失败: {}", e.getMessage());
            return jsonMode
                    ? "{\"error\":\"" + e.getMessage().replace("\"","\\\"") + "\"}"
                    : "（DeepSeek 调用异常：" + e.getMessage() + "）";
        }
    }
}
