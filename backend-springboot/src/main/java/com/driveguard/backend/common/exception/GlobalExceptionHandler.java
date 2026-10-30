
package com.driveguard.backend.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 1. Xu ly loi nghiep vu co HTTP status
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(
            ResponseStatusException ex
    ) {

        String message = ex.getReason() != null
                ? ex.getReason()
                : "Yeu cau khong hop le";

        return ResponseEntity
                .status(ex.getStatusCode())
                .body(Map.of(
                        "success", false,
                        "message", message
                ));
    }

    // 2. Xu ly loi Validation (@Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex
    ) {

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Du lieu dau vao khong hop le");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "success", false,
                        "message", message
                ));
    }

    // 3. Xu ly JSON sai cu phap hoac khong doc duoc
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidJson(
            HttpMessageNotReadableException ex
    ) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "success", false,
                        "message", "Du lieu JSON khong hop le"
                ));
    }

    // 4. Xu ly cac loi he thong khong mong muon
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(
            Exception ex
    ) {

        log.error("Unexpected API error", ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "success", false,
                        "message", "Da xay ra loi he thong"
                ));
    }
}
