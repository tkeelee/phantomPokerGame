package com.example.poker.util;

import lombok.Data;

/**
 * 统一API响应封装类
 */
@Data
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;

    private ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    /**
     * 成功响应
     * @param data 响应数据
     * @return ApiResponse对象
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "success", data);
    }

    /**
     * 成功响应
     * @param message 响应消息
     * @param data 响应数据
     * @return ApiResponse对象
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    /**
     * 失败响应
     * @param message 错误消息
     * @return ApiResponse对象
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }

    /**
     * 失败响应
     * @param message 错误消息
     * @param data 错误数据
     * @return ApiResponse对象
     */
    public static <T> ApiResponse<T> error(String message, T data) {
        return new ApiResponse<>(false, message, data);
    }
} 