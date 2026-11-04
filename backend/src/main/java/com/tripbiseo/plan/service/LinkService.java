package com.tripbiseo.plan.service;

import com.tripbiseo.plan.dto.LinkRequest;
import com.tripbiseo.plan.dto.LinkResponse;

/** ③ 인스타 링크 → 게시물에 나온 장소 찾기 */
public interface LinkService {

    /** 링크의 게시물 글(막히면 붙여 넣은 글)에서 장소 이름을 뽑아 실제 장소로 찾아 줌. 저장하지 않음 */
    LinkResponse findPlaces(LinkRequest request);
}
