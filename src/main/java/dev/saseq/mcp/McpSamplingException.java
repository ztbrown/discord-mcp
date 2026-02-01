package dev.saseq.mcp;

/**
 * Exception thrown when MCP sampling requests fail.
 */
public class McpSamplingException extends Exception {

    private final int errorCode;

    public McpSamplingException(String message) {
        super(message);
        this.errorCode = 0;
    }

    public McpSamplingException(String message, int errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public McpSamplingException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = 0;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
