package dev.saseq.mcp;

/**
 * Represents an MCP sampling/createMessage response.
 * Contains the generated content from the LLM.
 */
public class SamplingResponse {

    private SamplingContent content;
    private String model;
    private String stopReason;

    public SamplingResponse() {
    }

    public SamplingContent getContent() {
        return content;
    }

    public void setContent(SamplingContent content) {
        this.content = content;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getStopReason() {
        return stopReason;
    }

    public void setStopReason(String stopReason) {
        this.stopReason = stopReason;
    }

    /**
     * Represents the content of a sampling response.
     */
    public static class SamplingContent {
        private String type;
        private String text;

        public SamplingContent() {
        }

        public SamplingContent(String type, String text) {
            this.type = type;
            this.text = text;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }
}
