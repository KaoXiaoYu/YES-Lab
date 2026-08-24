package cn.yeslab.platform.common.api;

import java.time.Instant;

public record ApiResponse<T>(T data, Meta meta) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, new Meta("v1", Instant.now()));
    }

    public record Meta(String apiVersion, Instant generatedAt) {
    }
}
