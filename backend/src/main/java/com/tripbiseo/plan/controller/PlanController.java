package com.tripbiseo.plan.controller;

import java.io.IOException;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tripbiseo.common.auth.LoginUser;
import com.tripbiseo.plan.dto.PlanChatRequest;
import com.tripbiseo.plan.dto.PlanChatResponse;
import com.tripbiseo.plan.dto.PlanGenerateRequest;
import com.tripbiseo.plan.dto.PlanGenerateResponse;
import com.tripbiseo.plan.dto.CaptureResponse;
import com.tripbiseo.plan.dto.StayCaptureResponse;
import com.tripbiseo.plan.service.CaptureService;
import com.tripbiseo.plan.service.PlanService;

import lombok.RequiredArgsConstructor;

/** ③ AI 일정 만들기 (저장 전 초안): 대화 → 조건 → 초안. 점검(/api/plans/check)은 ②의 PlanCheckController */
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;
    private final CaptureService captureService;
    private final LoginUser loginUser;

    /** 사용자 말 → 여행 조건 정리 */
    @PostMapping("/chat")
    public PlanChatResponse chatPlan(@RequestBody PlanChatRequest request) {
        return planService.chatPlan(request);
    }

    /** 조건 → 일정 초안 (저장 안 함) */
    @PostMapping("/generate")
    public PlanGenerateResponse generatePlan(@RequestBody PlanGenerateRequest request) {
        return planService.generatePlan(loginUser.id(), request);
    }

    /** 예약 캡처 사진 → 장소 이름·날짜·시각 (고정 일정 후보, 저장 안 함). MultipartFile: 화면이 올린 파일 */
    @PostMapping("/capture")
    public CaptureResponse readCapture(@RequestParam(value = "image", required = false) MultipartFile image) throws IOException {
        byte[] bytes = image == null ? null : image.getBytes();
        return captureService.readCapture(bytes, image == null ? null : image.getContentType());
    }

    /** 숙소 예약 캡처 사진 → 숙소 이름·주소·체크인·체크아웃 + 위치 (숙소 후보, 저장 안 함) */
    @PostMapping("/capture/stay")
    public StayCaptureResponse readStayCapture(@RequestParam(value = "image", required = false) MultipartFile image) throws IOException {
        byte[] bytes = image == null ? null : image.getBytes();
        return captureService.readStayCapture(bytes, image == null ? null : image.getContentType());
    }
}
