package com.releasedash.exception;

import com.releasedash.cmdb.CmdbConflictException;
import com.releasedash.cmdb.CmdbGithubException;
import com.releasedash.cmdb.CmdbItemNotFoundException;
import com.releasedash.cmdb.CmdbNotEnabledException;
import com.releasedash.cmdb.CmdbNotImplementedException;
import com.releasedash.cmdb.CmdbValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CmdbNotEnabledException.class)
    public ResponseEntity<Map<String, String>> handleCmdbNotEnabled(CmdbNotEnabledException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(CmdbNotImplementedException.class)
    public ResponseEntity<Map<String, String>> handleCmdbNotImplemented(CmdbNotImplementedException e) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
            .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(CmdbItemNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleCmdbItemNotFound(CmdbItemNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(CmdbGithubException.class)
    public ResponseEntity<Map<String, String>> handleCmdbGithub(CmdbGithubException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(CmdbValidationException.class)
    public ResponseEntity<Map<String, Object>> handleCmdbValidation(CmdbValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", e.getMessage(), "errors", e.getErrors()));
    }

    @ExceptionHandler(CmdbConflictException.class)
    public ResponseEntity<Map<String, String>> handleCmdbConflict(CmdbConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("error", e.getMessage()));
    }
    
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException e) {
        if (e.getMessage() != null && e.getMessage().contains("not found")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Internal server error"));
    }
}
