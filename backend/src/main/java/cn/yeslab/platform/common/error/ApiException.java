package cn.yeslab.platform.common.error;

import org.springframework.http.HttpStatus;
import java.util.Map;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final Map<String, String> fields;

    public ApiException(HttpStatus status, String message) {
        this(status, message, Map.of());
    }

    public ApiException(HttpStatus status, String message, Map<String, String> fields) {
        super(message);
        this.status = status;
        this.fields = Map.copyOf(fields);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, String> getFields() { return fields; }
}
