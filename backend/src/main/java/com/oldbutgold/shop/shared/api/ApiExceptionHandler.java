package com.oldbutgold.shop.shared.api;

import com.oldbutgold.shop.modules.identity.application.DuplicateEmailException;
import com.oldbutgold.shop.modules.identity.application.DuplicatePhoneException;
import com.oldbutgold.shop.modules.identity.application.EkycUnavailableException;
import com.oldbutgold.shop.modules.identity.application.SellerActivationDisabledException;
import com.oldbutgold.shop.modules.identity.application.InvalidAvatarException;
import com.oldbutgold.shop.modules.identity.application.AvatarStorageUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.dao.DataIntegrityViolationException;
import com.oldbutgold.shop.modules.identity.application.RegistrationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(com.oldbutgold.shop.modules.communication.application.CommunicationException.class)
    ResponseEntity<ApiError> communicationFailure(com.oldbutgold.shop.modules.communication.application.CommunicationException error,HttpServletRequest request){return response(HttpStatus.valueOf(error.status()),error.code(),error.getMessage(),request,List.of());}
    @ExceptionHandler(com.oldbutgold.shop.modules.identity.application.AccountRestrictedException.class)
    ResponseEntity<?> accountRestricted(com.oldbutgold.shop.modules.identity.application.AccountRestrictedException error) {
        return ResponseEntity.status(403).header("Cache-Control","no-store").body(java.util.Map.of(
                "code","ACCOUNT_RESTRICTED","message",error.getMessage(),"accountStatus",error.accountStatus(),
                "supportInstruction","Liên hệ bộ phận hỗ trợ O.G Shop để kiểm tra tình trạng tài khoản.",
                "allowedFunctions",List.of("CONTACT_SUPPORT")));
    }
    @ExceptionHandler(RegistrationException.class)
    ResponseEntity<ApiError> registrationFailure(RegistrationException exception, HttpServletRequest request) {
        var result = response(HttpStatus.valueOf(exception.status()), exception.code(), exception.getMessage(), request, List.of());
        var headers = new org.springframework.http.HttpHeaders();
        headers.putAll(result.getHeaders());
        headers.setCacheControl("no-store");
        if (exception.retryAfter() > 0) headers.set("Retry-After", Long.toString(exception.retryAfter()));
        return new ResponseEntity<>(result.getBody(), headers, result.getStatusCode());
    }
    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ApiError> unauthorized(RuntimeException exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Phiên đăng nhập không hợp lệ.", request, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(AccessDeniedException exception, HttpServletRequest request) {
        String msg = (exception.getMessage() != null && !exception.getMessage().isBlank() && !exception.getMessage().equalsIgnoreCase("Access Denied"))
                ? exception.getMessage()
                : "Yêu cầu không được phép.";
        return response(HttpStatus.FORBIDDEN, "ACCESS_DENIED", msg, request, List.of());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    ResponseEntity<ApiError> duplicateEmail(DuplicateEmailException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Email này đã được sử dụng.", request, List.of());
    }

    @ExceptionHandler(DuplicatePhoneException.class)
    ResponseEntity<ApiError> duplicatePhone(DuplicatePhoneException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "PHONE_ALREADY_REGISTERED", exception.getMessage() != null ? exception.getMessage() : "Số điện thoại này đã được sử dụng bởi một tài khoản khác.", request, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> dataConflict(DataIntegrityViolationException exception, HttpServletRequest request) {
        String detail = exception.getMostSpecificCause() != null ? exception.getMostSpecificCause().getMessage() : "";
        String msg = "Dữ liệu xung đột với trạng thái hiện tại.";
        if (detail != null) {
            if (detail.contains("uq_users_phone_number") || detail.contains("phone_number")) {
                msg = "Số điện thoại này đã được sử dụng bởi một tài khoản khác.";
            } else if (detail.contains("uq_users_email") || detail.contains("users_email")) {
                msg = "Email này đã được sử dụng bởi một tài khoản khác.";
            } else if (detail.contains("Only KTV can decide a pending eKYC revision")) {
                msg = "Hồ sơ eKYC không ở trạng thái chờ duyệt (PENDING) hoặc đã được xử lý bởi tiến trình khác.";
            } else if (detail.contains("eKYC identity/private evidence incomplete")) {
                msg = "Hồ sơ eKYC chưa có đủ 4 loại tài liệu bắt buộc (ảnh mặt trước, mặt sau, ảnh khuôn mặt và ảnh tham chiếu).";
            } else if (detail.contains("Canonical role grant requires approval authority")) {
                msg = "Cấp quyền Người bán yêu cầu hồ sơ hợp lệ và sự phê duyệt từ Kỹ thuật viên.";
            } else if (detail.contains("Seller prerequisites incomplete")) {
                msg = "Hồ sơ Người bán cần có địa chỉ lấy hàng và thông tin tài khoản ngân hàng.";
            } else if (detail.contains("Quick authentication unavailable/exhausted")) {
                msg = "Phiên xác thực nhanh không khả dụng hoặc đã vượt quá 15 lần thử cho phép. Vui lòng tạo phiên mới.";
            } else if (detail.contains("Quick authentication cooldown")) {
                msg = "Vui lòng đợi 3 giây trước khi thực hiện lần quét tiếp theo.";
            } else if (detail.contains("Quick authentication result requires server match")) {
                msg = "Kết quả xác thực nhanh chỉ được chấp nhận khi khuôn mặt khớp với mẫu eKYC.";
            } else if (detail.contains("Quick auth requires current approved private reference")) {
                msg = "Xác thực nhanh yêu cầu ảnh tham chiếu eKYC đã được phê duyệt và còn hiệu lực.";
            } else if (detail.contains("Hồ sơ không ở trạng thái sẵn sàng nộp")) {
                msg = "Hồ sơ eKYC không ở trạng thái sẵn sàng nộp hoặc đã được cập nhật bởi tiến trình khác.";
            } else if (detail.contains("identity_document_registry")) {
                msg = "Số CCCD này đã được liên kết với một tài khoản khác trong hệ thống.";
            }
        }
        return response(HttpStatus.CONFLICT, "DATA_CONFLICT", msg, request, List.of());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            IllegalArgumentException.class})
    ResponseEntity<ApiError> invalidInput(Exception exception, HttpServletRequest request) {
        List<FieldError> fields = exception instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldErrors().stream()
                    .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
                    .toList()
                : List.of();
        String message = (exception instanceof IllegalArgumentException iae && iae.getMessage() != null && !iae.getMessage().isBlank())
                ? iae.getMessage()
                : "Dữ liệu gửi lên không hợp lệ.";
        return response(HttpStatus.BAD_REQUEST, "INVALID_INPUT", message, request, fields);
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiError> illegalState(IllegalStateException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "ILLEGAL_STATE", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(com.oldbutgold.shop.modules.fulfillment.infrastructure.client.GhnApiException.class)
    ResponseEntity<ApiError> ghnApiError(com.oldbutgold.shop.modules.fulfillment.infrastructure.client.GhnApiException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "GHN_API_ERROR", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(com.oldbutgold.shop.modules.fulfillment.application.FulfillmentFeatureLockedException.class)
    ResponseEntity<ApiError> fulfillmentFeatureLocked(
            com.oldbutgold.shop.modules.fulfillment.application.FulfillmentFeatureLockedException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, "FULFILLMENT_FEATURE_LOCKED", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(InvalidAvatarException.class)
    ResponseEntity<ApiError> invalidAvatar(InvalidAvatarException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_AVATAR", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    ResponseEntity<ApiError> responseStatus(org.springframework.web.server.ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String code = status.name();
        String message = exception.getReason() != null ? exception.getReason() : exception.getMessage();
        return response(status, code, message, request, List.of());
    }

    @ExceptionHandler(AvatarStorageUnavailableException.class)
    ResponseEntity<ApiError> avatarUnavailable(AvatarStorageUnavailableException exception, HttpServletRequest request) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "AVATAR_STORAGE_UNAVAILABLE", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(EkycUnavailableException.class)
    ResponseEntity<ApiError> ekycUnavailable(EkycUnavailableException exception, HttpServletRequest request) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "EKYC_UNAVAILABLE",
                "Dịch vụ xác minh hiện không khả dụng. Hồ sơ chưa được xác minh.", request, List.of());
    }

    @ExceptionHandler(SellerActivationDisabledException.class)
    ResponseEntity<ApiError> sellerActivationDisabled(SellerActivationDisabledException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "MVP_ACTIVATION_DISABLED", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(com.oldbutgold.shop.modules.catalog.application.ProductNotFoundException.class)
    ResponseEntity<ApiError> productNotFound(com.oldbutgold.shop.modules.catalog.application.ProductNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(com.oldbutgold.shop.modules.catalog.application.CategoryNotFoundException.class)
    ResponseEntity<ApiError> categoryNotFound(com.oldbutgold.shop.modules.catalog.application.CategoryNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(com.oldbutgold.shop.modules.catalog.application.SellerRequiredException.class)
    ResponseEntity<ApiError> sellerRequired(com.oldbutgold.shop.modules.catalog.application.SellerRequiredException exception, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "SELLER_REQUIRED", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(com.oldbutgold.shop.modules.catalog.application.ProductStateConflictException.class)
    ResponseEntity<ApiError> productStateConflict(com.oldbutgold.shop.modules.catalog.application.ProductStateConflictException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "PRODUCT_STATE_CONFLICT", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(com.oldbutgold.shop.modules.catalog.application.CommandKeyConflictException.class)
    ResponseEntity<ApiError> commandKeyConflict(com.oldbutgold.shop.modules.catalog.application.CommandKeyConflictException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "COMMAND_KEY_CONFLICT", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler({
            com.oldbutgold.shop.modules.catalog.application.ProductVersionConflictException.class,
            org.springframework.orm.ObjectOptimisticLockingFailureException.class,
            org.springframework.dao.OptimisticLockingFailureException.class
    })
    ResponseEntity<ApiError> productVersionConflict(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "PRODUCT_VERSION_CONFLICT",
                "Tin đăng đã được cập nhật bởi một phiên làm việc khác. Vui lòng tải lại trang.", request, List.of());
    }

    @ExceptionHandler(com.oldbutgold.shop.modules.payment.application.PaymentStateConflictException.class)
    ResponseEntity<ApiError> paymentStateConflict(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "PAYMENT_STATE_CONFLICT", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler({org.springframework.dao.DataAccessException.class, org.springframework.orm.jpa.JpaSystemException.class})
    ResponseEntity<ApiError> databaseError(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "DATABASE_ERROR", "Đã xảy ra lỗi khi lưu trữ dữ liệu. Vui lòng thử lại sau.", request, List.of());
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
