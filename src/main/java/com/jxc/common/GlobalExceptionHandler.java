package com.jxc.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 全局异常处理器：保证任何异常都以统一 Result 结构返回，而不是 Spring 默认的错误 JSON。
 *
 * <p>处理顺序由 Spring 按异常类型精确度选择：参数校验 → 唯一键冲突 → 业务异常(RuntimeException)
 * → 请求体/参数错误 → 兜底 Exception。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Pattern ROW_INDEX = Pattern.compile("(?:details|items)\\[(\\d+)]");

    /** 参数校验失败统一用 400，业务失败才是 500，便于前端与日志区分 */
    private static final int BAD_REQUEST = 400;

    /** 请求体字段校验失败（@Valid） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        FieldError error = e.getBindingResult().getFieldError();
        String msg = error == null ? "请求参数校验失败" : describe(error);
        return Result.error(BAD_REQUEST, msg);
    }

    /** 表单绑定校验失败 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        FieldError error = e.getBindingResult().getFieldError();
        return Result.error(BAD_REQUEST, error == null ? "请求参数校验失败" : describe(error));
    }

    /** 方法参数级约束（如 @Validated 下的路径参数） */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
            return Result.error(BAD_REQUEST, violation.getMessage());
        }
        return Result.error(BAD_REQUEST, "请求参数校验失败");
    }

    /**
     * Spring 6.1 起，{@code @Valid @RequestBody List<Xxx>} 这类「集合元素」的校验失败
     * 抛的是 HandlerMethodValidationException（而不是 MethodArgumentNotValidException），
     * 必须单独处理，否则会返回框架默认的 400 响应，破坏统一 Result 约定。
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public Result<Void> handleHandlerMethodValidation(HandlerMethodValidationException e) {
        for (ParameterValidationResult result : e.getAllValidationResults()) {
            for (var error : result.getResolvableErrors()) {
                String msg = error.getDefaultMessage();
                if (msg != null && !msg.isEmpty()) {
                    return Result.error(BAD_REQUEST, msg);
                }
            }
        }
        return Result.error(BAD_REQUEST, "请求参数校验失败");
    }

    /** 唯一键冲突（并发下生成重复单号、编码重复等） */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一约束冲突: {}", e.getMessage());
        return Result.error("数据重复，请检查编码/单号是否已存在");
    }

    /** 业务异常：Service 层主动抛出，直接把 message 返回给前端 */
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntime(RuntimeException e) {
        String msg = e.getMessage();
        if (msg == null || msg.isEmpty()) {
            log.error("运行时异常", e);
            msg = "操作失败，请稍后重试";
        }
        return Result.error(msg);
    }

    /** 请求体格式错误 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.error(BAD_REQUEST, "请求参数格式错误");
    }

    /** 缺少上传文件 */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public Result<Void> handleMissingPart(MissingServletRequestPartException e) {
        return Result.error(BAD_REQUEST, "请选择要上传的文件");
    }

    /** 上传文件过大 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUpload(MaxUploadSizeExceededException e) {
        return Result.error(BAD_REQUEST, "上传文件过大，请拆分后再导入");
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public Result<Void> handleBadRequest(Exception e) {
        log.warn("请求参数错误: {}", e.getMessage());
        return Result.error(BAD_REQUEST, "请求参数错误");
    }

    /** 静态资源/接口路径不存在：保持 404，不要被兜底逻辑变成 500 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoResourceFoundException e) {
        return ResponseEntity.status(404).body(Result.error(404, "请求的资源不存在"));
    }

    /** 兜底 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        String msg = e.getMessage();
        return Result.error("系统异常" + (msg == null || msg.isEmpty() ? "" : "：" + msg));
    }

    /** 把 details[2].quantity 这类字段路径翻译成「第 3 行明细：...」，便于用户定位 */
    private String describe(FieldError error) {
        String message = error.getDefaultMessage() == null ? "参数不合法" : error.getDefaultMessage();
        Matcher matcher = ROW_INDEX.matcher(error.getField() == null ? "" : error.getField());
        if (matcher.find()) {
            return "第 " + (Integer.parseInt(matcher.group(1)) + 1) + " 行明细：" + message;
        }
        return message;
    }
}
