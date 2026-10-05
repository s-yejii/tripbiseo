package com.tripbiseo.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PlaceMapper {

    @Select("SELECT COUNT(*) FROM place")
    int countPlace();
}