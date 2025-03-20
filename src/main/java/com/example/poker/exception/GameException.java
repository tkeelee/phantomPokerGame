package com.example.poker.exception;

/**
 * 游戏异常类
 * 用于表示游戏过程中的业务逻辑异常
 */
public class GameException extends RuntimeException {
    
    private String errorCode;
    
    /**
     * 创建游戏异常
     * @param message 异常信息
     */
    public GameException(String message) {
        super(message);
    }
    
    /**
     * 创建带错误代码的游戏异常
     * @param message 异常信息
     * @param errorCode 错误代码
     */
    public GameException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    /**
     * 获取错误代码
     * @return 错误代码
     */
    public String getErrorCode() {
        return errorCode;
    }
} 