package com.sky.handler;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

//全局异常处理器，处理项目中抛出的业务异常
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    //捕获业务异常
    @ExceptionHandler
    public Result exceptionHandler(BaseException ex){
        log.error("异常信息：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    //捕获 @Valid 参数校验失败，返回第一条校验提示
    @ExceptionHandler
    public Result exceptionHandler(MethodArgumentNotValidException ex){
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse(MessageConstant.UNKNOWN_ERROR);
        log.error("参数校验失败：{}", message);
        return Result.error(message);
    }

    //捕获唯一键冲突等数据完整性异常
    @ExceptionHandler
    public Result exceptionHandler(DuplicateKeyException ex){
        log.error("唯一键冲突：{}", ex.getMessage());
        return Result.error(MessageConstant.ALREADY_EXISTS);
    }

    //兜底其余异常（空指针、SQL异常等），保证前端始终拿到统一的Result结构
    @ExceptionHandler
    public Result exceptionHandler(Exception ex){
        log.error("系统异常：", ex);
        return Result.error(MessageConstant.UNKNOWN_ERROR);
    }

}
