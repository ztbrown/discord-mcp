package dev.saseq.mcp;

/**
 * Represents a message in the MCP sampling request.
 * Each message has a role (user/assistant) and content.
 */
public class SamplingMessage {

    private String role;
    private SamplingContent content;

    public SamplingMessage() {
    }

    public SamplingMessage(String role, String textContent) {
        this.role = role;
        this.content = new SamplingContent("text", textContent);
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public SamplingContent getContent() {
        return content;
    }

    public void setContent(SamplingContent content) {
        this.content = content;
    }

    /**
     * Represents the content of a sampling message.
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
