package com.sailpoint.connector.accessio.racf.security;

import sailpoint.tools.Util;
import java.util.regex.Pattern;

/**
 * Input validation utility for security hardening
 * 
 * @author SailPoint Professional Services
 * @version 1.0.0
 */
public class InputValidator {
    
    // Safe string pattern - alphanumeric, dots, underscores, at signs, hyphens
    private static final Pattern SAFE_STRING_PATTERN = Pattern.compile("^[a-zA-Z0-9._@-]+$");
    private static final Pattern USER_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{1,32}$");
    private static final Pattern ROLE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{1,32}$");
    private static final Pattern ORG_UNIT_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{1,50}$");
    
    // Maximum field lengths
    private static final int MAX_STRING_LENGTH = 255;
    private static final int MAX_USER_ID_LENGTH = 32;
    private static final int MAX_ROLE_ID_LENGTH = 32;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    
    /**
     * Sanitize general string input
     */
    public static String sanitizeInput(String input, String fieldName) {
        if (input == null) return null;
        
        // Trim whitespace
        input = input.trim();
        
        // Check for empty string after trim
        if (input.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        
        // Check length
        if (input.length() > MAX_STRING_LENGTH) {
            throw new IllegalArgumentException(fieldName + " exceeds maximum length of " + MAX_STRING_LENGTH);
        }
        
        // Check for potentially malicious patterns
        if (containsMaliciousPatterns(input)) {
            throw new IllegalArgumentException(fieldName + " contains potentially malicious content");
        }
        
        return input;
    }
    
    /**
     * Validate user ID format and content
     */
    public static void validateUserId(String userId) {
        if (Util.isNullOrEmpty(userId)) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        
        userId = userId.trim();
        
        if (userId.length() > MAX_USER_ID_LENGTH) {
            throw new IllegalArgumentException("User ID exceeds maximum length of " + MAX_USER_ID_LENGTH);
        }
        
        if (!USER_ID_PATTERN.matcher(userId).matches()) {
            throw new IllegalArgumentException("User ID contains invalid characters. Only alphanumeric, dots, underscores, and hyphens allowed");
        }
    }
    
    /**
     * Validate role ID format and content
     */
    public static void validateRoleId(String roleId) {
        if (Util.isNullOrEmpty(roleId)) {
            throw new IllegalArgumentException("Role ID cannot be null or empty");
        }
        
        roleId = roleId.trim();
        
        if (roleId.length() > MAX_ROLE_ID_LENGTH) {
            throw new IllegalArgumentException("Role ID exceeds maximum length of " + MAX_ROLE_ID_LENGTH);
        }
        
        if (!ROLE_ID_PATTERN.matcher(roleId).matches()) {
            throw new IllegalArgumentException("Role ID contains invalid characters. Only alphanumeric, dots, underscores, and hyphens allowed");
        }
    }
    
    /**
     * Validate organizational unit ID
     */
    public static void validateOrgUnitId(String orgUnitId) {
        if (Util.isNullOrEmpty(orgUnitId)) {
            throw new IllegalArgumentException("Org Unit ID cannot be null or empty");
        }
        
        orgUnitId = orgUnitId.trim();
        
        if (!ORG_UNIT_PATTERN.matcher(orgUnitId).matches()) {
            throw new IllegalArgumentException("Org Unit ID contains invalid characters");
        }
    }
    
    /**
     * Validate email address format
     */
    public static void validateEmail(String email) {
        if (Util.isNullOrEmpty(email)) {
            return; // Email is optional in many cases
        }
        
        email = email.trim();
        
        // Basic email validation
        if (!email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            throw new IllegalArgumentException("Invalid email format");
        }
        
        if (email.length() > MAX_STRING_LENGTH) {
            throw new IllegalArgumentException("Email exceeds maximum length");
        }
    }
    
    /**
     * Validate description field
     */
    public static String validateDescription(String description) {
        if (description == null) return null;
        
        description = description.trim();
        
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Description exceeds maximum length of " + MAX_DESCRIPTION_LENGTH);
        }
        
        // Remove potentially dangerous HTML/script content
        description = description.replaceAll("<[^>]*>", ""); // Strip HTML tags
        description = description.replaceAll("javascript:", ""); // Remove javascript: URLs
        description = description.replaceAll("vbscript:", ""); // Remove vbscript: URLs
        
        return description;
    }
    
    /**
     * Check for malicious patterns in input
     */
    private static boolean containsMaliciousPatterns(String input) {
        if (input == null) return false;
        
        String lowerInput = input.toLowerCase();
        
        // Check for common injection patterns
        String[] maliciousPatterns = {
            "<script", "javascript:", "vbscript:", "onload=", "onerror=",
            "eval(", "expression(", "url(", "import(", "\\x", "&#x",
            "union select", "drop table", "insert into", "delete from",
            "../", "..\\", "%2e%2e", "%252e", "file://", "ftp://",
            "data:", "blob:", "filesystem:"
        };
        
        for (String pattern : maliciousPatterns) {
            if (lowerInput.contains(pattern)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Sanitize SQL-like input to prevent injection
     */
    public static String sanitizeSqlInput(String input) {
        if (input == null) return null;
        
        // Remove dangerous SQL characters and keywords
        input = input.replaceAll("[';\"\\\\]", "");
        input = input.replaceAll("(?i)(union|select|insert|update|delete|drop|create|alter|exec|execute)", "");
        
        return input.trim();
    }
    
    /**
     * Validate workflow ID format
     */
    public static void validateWorkflowId(String workflowId) {
        if (Util.isNullOrEmpty(workflowId)) {
            throw new IllegalArgumentException("Workflow ID cannot be null or empty");
        }
        
        if (!workflowId.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("Workflow ID contains invalid characters");
        }
    }
}
