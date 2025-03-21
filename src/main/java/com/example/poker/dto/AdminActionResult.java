package com.example.poker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminActionResult {
    private boolean success;
    private String message;
    
    public static AdminActionResult success(String message) {
        return new AdminActionResult(true, message);
    }
    
    public static AdminActionResult fail(String message) {
        return new AdminActionResult(false, message);
    }
} 