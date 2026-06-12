package com.campusqa.controller;

import com.campusqa.dto.ApiResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestPart(Exception ex) {
        return ResponseEntity.ok(ApiResponse.failure("请求参数缺失，请检查必需参数或上传文件"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.ok(ApiResponse.failure("请求 JSON 格式错误，请检查请求体"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.ok(ApiResponse.failure("请求参数格式错误，请检查 id 或数字参数"));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException ex) {
        return ResponseEntity.ok(ApiResponse.failure("文件上传失败，请检查 multipart/form-data 请求"));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDatabase(DataAccessException ex) {
        return ResponseEntity.ok(ApiResponse.failure("数据库操作失败，请检查数据格式或数据库初始化状态"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        return ResponseEntity.ok(ApiResponse.failure("系统处理失败，请稍后重试或检查请求参数"));
    }
}
