package com.advx.resurrect.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 复活系统配置，映射 application.yml 中的 resurrect.* 段。
 */
@Configuration
@ConfigurationProperties(prefix = "resurrect")
public class AppProperties {

    private Upload upload = new Upload();
    private DeepSeek deepseek = new DeepSeek();
    private Image image = new Image();

    public Upload getUpload() { return upload; }
    public void setUpload(Upload upload) { this.upload = upload; }

    public DeepSeek getDeepseek() { return deepseek; }
    public void setDeepseek(DeepSeek deepseek) { this.deepseek = deepseek; }

    public Image getImage() { return image; }
    public void setImage(Image image) { this.image = image; }

    public static class Upload {
        private int maxUploadMb = 20;
        private int maxFiles = 5000;
        private long maxTextBytes = 5L * 1024 * 1024;
        private String workdir;

        public int getMaxUploadMb() { return maxUploadMb; }
        public void setMaxUploadMb(int maxUploadMb) { this.maxUploadMb = maxUploadMb; }
        public int getMaxFiles() { return maxFiles; }
        public void setMaxFiles(int maxFiles) { this.maxFiles = maxFiles; }
        public long getMaxTextBytes() { return maxTextBytes; }
        public void setMaxTextBytes(long maxTextBytes) { this.maxTextBytes = maxTextBytes; }
        public String getWorkdir() { return workdir; }
        public void setWorkdir(String workdir) { this.workdir = workdir; }
    }

    public static class DeepSeek {
        private String apiKey = "";
        private String baseUrl = "https://api.deepseek.com";
        private String model = "deepseek-chat";
        private int timeoutSeconds = 120;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    public static class Image {
        private String apiKey = "";
        private String baseUrl = "https://api.siliconflow.cn/v1";
        private String model = "black-forest-labs/FLUX.1-schnell";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }
}
