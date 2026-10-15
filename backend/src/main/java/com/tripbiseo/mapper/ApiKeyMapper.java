package com.tripbiseo.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 개발용 API 키 (api_key 테이블) */
@Mapper
public interface ApiKeyMapper {

    @Select("SELECT name, value FROM api_key")
    List<Map<String, Object>> findAll();

    @Insert("INSERT INTO api_key (name, value) VALUES (#{name}, #{value}) "
          + "ON CONFLICT (name) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW()")
    int save(@Param("name") String name, @Param("value") String value);

    @Delete("DELETE FROM api_key WHERE name = #{name}")
    int delete(String name);
}
