package com.tripbiseo.after.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.after.dto.RetroResponse;
import com.tripbiseo.after.service.RetroService;
import com.tripbiseo.common.auth.LoginUser;

import lombok.RequiredArgsConstructor;

/** ⑤ 여행 후 회고. 주소는 /api/trips/{id}/retro 그대로지만 담당은 ⑤ */
@RestController
@RequiredArgsConstructor
public class RetroController {

    private final RetroService retroService;
    private final LoginUser loginUser;

    /** 회고: 계획 vs 실제 + GPS 선 */
    @GetMapping("/api/trips/{id}/retro")
    public RetroResponse getRetro(@PathVariable long id) {
        return retroService.getRetro(loginUser.id(), id);
    }
}
