package com.hanumoka.sado.controller;

import com.hanumoka.sado.common.dto.ApiResponse;
import com.hanumoka.sado.common.exception.ResourceNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @GetMapping("/success")
    public ApiResponse<String> testSuccess() {
        return ApiResponse.success("Hello, SADO!");
    }

    @GetMapping("/error")
    public ApiResponse<Void> testError() {
        throw new ResourceNotFoundException("테스트 리소를 못찾음");
    }
}
