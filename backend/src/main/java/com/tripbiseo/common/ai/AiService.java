package com.tripbiseo.common.ai;

/**
 * Gemini 호출 한 곳. 원칙: Gemini는 사람 말을 이해하고 고르고 설명만, 시간·거리 계산은 서버가 합니다.
 * 개인정보·위치(좌표) 기록은 Gemini에 보내지 않습니다 (무료 등급은 입력이 학습에 쓰일 수 있음).
 * 키가 없거나 실패하면 docs/API.md의 "Gemini 공통 오류" 문장으로 ApiException을 던집니다.
 * 규칙 기반 기본값은 부르는 쪽(각 ServiceImpl)이 catch해서 만듭니다.
 */
public interface AiService {

    /** Gemini 키가 있는지 (없으면 부르지 말고 바로 규칙 기본값으로) */
    boolean isReady();

    /** 질문 → 글 답 */
    String askText(String prompt);

    /** 질문 → JSON 답을 type 클래스(DTO)에 담아 줌. <T>: 받을 DTO 타입을 부르는 쪽이 정함 */
    <T> T askJson(String prompt, Class<T> type);

    /**
     * 날씨 도구(get_weather)를 쓸 수 있게 묻기 → JSON 답을 type에 담아 줌.
     * lat·lng는 서버 안에서 기상청을 부를 때만 쓰고 Gemini에는 보내지 않습니다.
     */
    <T> T askJsonWithWeather(String prompt, double lat, double lng, Class<T> type);
}
