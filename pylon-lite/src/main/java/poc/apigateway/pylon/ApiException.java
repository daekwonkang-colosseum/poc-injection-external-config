package poc.apigateway.pylon;

public class ApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String specId;
    private final int statusCode;

    public ApiException(String specId, int statusCode, String message) {
        super("[" + specId + "] " + message);
        this.specId = specId;
        this.statusCode = statusCode;
    }

    public ApiException(String specId, String message, Throwable cause) {
        super("[" + specId + "] " + message, cause);
        this.specId = specId;
        this.statusCode = 0;
    }

    public String getSpecId() {
        return specId;
    }

    /** HTTP 상태를 특정할 수 없는 실패(타임아웃 등)는 0이다. */
    public int getStatusCode() {
        return statusCode;
    }
}
