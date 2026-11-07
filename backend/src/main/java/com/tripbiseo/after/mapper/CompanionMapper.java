package com.tripbiseo.after.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.tripbiseo.after.dto.Companion;

/** ⑤ 같이 간 사람 (trip_companion). SQL은 resources/mapper/after/CompanionMapper.xml */
@Mapper
public interface CompanionMapper {

    /** 한 여행의 같이 간 사람, 보이는 순서대로 */
    List<Companion> selectCompanionList(long tripId);

    /** 한 사람 저장 */
    int insertCompanion(Companion companion);

    /** 이름 바꾸기. 이 여행 사람이 아니면 0줄 */
    int updateCompanionName(@Param("tripId") long tripId, @Param("companionId") long companionId, @Param("name") String name);

    /** 더치페이 낼 돈 정하기. null이면 자동으로 되돌림. 이 여행 사람이 아니면 0줄 */
    int updateShareAmount(@Param("tripId") long tripId, @Param("companionId") long companionId, @Param("amount") Integer amount);

    /** 이 여행 결제의 낸 사람 이름을 같이 바꾸기 (payer는 이름 글자로 저장돼 있어서) */
    int updatePayerName(@Param("tripId") long tripId, @Param("oldName") String oldName, @Param("newName") String newName);
}
