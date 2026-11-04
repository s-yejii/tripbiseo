package com.tripbiseo.live.service;

import com.tripbiseo.live.dto.LinkFitRequest;
import com.tripbiseo.live.dto.LinkFitResponse;

/** 여행 중에 링크로 찾은 장소를 남은 일정에 넣어 보기 (③ 링크 + ④ 계산기) */
public interface LiveLinkService {

    /** 방해가 가장 적은 자리를 찾고, 늦어지거나 같은 종류가 겹치면 고를 방법을 같이 만듦. 저장하지 않음 */
    LinkFitResponse fitPlace(long memberId, long tripId, LinkFitRequest request);
}
