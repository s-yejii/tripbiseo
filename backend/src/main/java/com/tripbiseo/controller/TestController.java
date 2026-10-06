package com.tripbiseo.controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.mapper.PlaceMapper;

@RestController
public class TestController {

    private final PlaceMapper placeMapper;

    public TestController(PlaceMapper placeMapper) {
        this.placeMapper = placeMapper;
    }

    @GetMapping("/db-test")
    public String dbTest() {
        int count = placeMapper.countPlace();
        return "PostgreSQL 연결 성공! place 개수: " + count;
    }
}