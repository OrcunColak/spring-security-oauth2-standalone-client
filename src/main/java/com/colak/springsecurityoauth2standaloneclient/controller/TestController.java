package com.colak.springsecurityoauth2standaloneclient.controller;


import com.colak.springsecurityoauth2standaloneclient.service.TestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/apis/v1/")
@RequiredArgsConstructor
public class TestController {

    private final TestService testService;

    // http://localhost:8084/apis/v1/test
    @GetMapping("test")
    public String getMessage() {
        return testService.getMessage();
    }

}
