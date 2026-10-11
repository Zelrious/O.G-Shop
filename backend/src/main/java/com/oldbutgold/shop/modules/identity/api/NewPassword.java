package com.oldbutgold.shop.modules.identity.api;

import com.oldbutgold.shop.modules.identity.domain.PasswordPolicy;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = NewPassword.Validator.class)
public @interface NewPassword {
    String message() default "Mật khẩu phải có ít nhất 8 ký tự và không vượt quá 72 byte UTF-8.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<NewPassword, String> {
        @Override public boolean isValid(String value, ConstraintValidatorContext context) {
            return PasswordPolicy.acceptsNewPassword(value);
        }
    }
}
