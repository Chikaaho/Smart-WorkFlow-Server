package com.sw.ck.common.exception;

import com.sw.ck.common.response.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public R<Void> handleBaseException(BaseException ex) {
        log.warn("business exception: {}", ex.getMessage());
        return R.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception ex) {
        log.error("unexpected exception", ex);
        return R.fail(CommonErrorCode.SYSTEM_ERROR.getCode(), CommonErrorCode.SYSTEM_ERROR.getMessage());
    }
}
