package com.example.poker.exception;

import com.example.poker.util.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理游戏相关异常
     * @param e 游戏异常
     * @return 错误响应
     */
    @ExceptionHandler(GameException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleGameException(GameException e) {
        logger.error("游戏异常: {}", e.getMessage());
        return ApiResponse.error(e.getMessage());
    }

    /**
     * 处理运行时异常
     * @param e 运行时异常
     * @return 错误响应
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleRuntimeException(RuntimeException e) {
        logger.error("运行时异常: ", e);
        return ApiResponse.error("服务器内部错误");
    }

    /**
     * 处理其他异常
     * @param e 异常
     * @return 错误响应
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        logger.error("系统异常: ", e);
        return ApiResponse.error("系统异常");
    }
} 