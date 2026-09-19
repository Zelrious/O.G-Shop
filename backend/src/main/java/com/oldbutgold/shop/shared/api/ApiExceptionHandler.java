package com.oldbutgold.shop.shared.api;

import com.oldbutgold.shop.modules.identity.application.DuplicateEmailException;
import com.oldbutgold.shop.modules.identity.application.EkycUnavailableException;
import com.oldbutgold.shop.modules.identity.application.InvalidRefreshTokenException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({BadCredentialsException.class, InvalidRefreshTokenException.class})
    ResponseEntity<ApiError> unauthorized(RuntimeException exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Phiên đăng nhập không hợp lệ.", request, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(AccessDeniedException exception, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Yêu cầu không được phép.", request, List.of());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    ResponseEntity<ApiError> duplicateEmail(DuplicateEmailException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Email này đã được sử dụng.", request, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> dataConflict(DataIntegrityViolationException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "DATA_CONFLICT",
                "Dữ liệu xung đột với trạng thái hiện tại.", request, List.of());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            IllegalArgumentException.class})
    ResponseEntity<ApiError> invalidInput(Exception exception, HttpServletRequest request) {
        List<FieldError> fields = exception instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldErrors().stream()
                    .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
                    .toList()
                : List.of();
        return response(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Dữ liệu gửi lên không hợp lệ.", request, fields);
    }

    @ExceptionHandler(EkycUnavailableException.class)
    ResponseEntity<ApiError> ekycUnavailable(EkycUnavailableException exception, HttpServletRequest request) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "EKYC_UNAVAILABLE",
                "Dịch vụ xác minh hiện không khả dụng. Hồ sơ chưa được xác minh.", request, List.of());
    }

    private static ResponseEntity<ApiError> response(HttpStatus status, String code, String message,
                                                      HttpServletRequest request, List<FieldError> fields) {
        String requestId = request.getHeader("X-Request-ID");
        if (requestId == null || requestId.isBlank() || requestId.length() > 100) {
            requestId = UUID.randomUUID().toString();
        }
        return ResponseEntity.status(status).body(new ApiError(code, message, requestId, fields));
    }

    public record ApiError(String code, String message, String requestId, List<FieldError> fieldErrors) {
    }

    public record FieldError(String field, String message) {
    }
}
