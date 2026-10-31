package com.tripbiseo.config;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * MyBatis 변환 도구: List<String> ↔ "bus,subway,walk" (trip.transports 칸).
 * XML에서 typeHandler="com.tripbiseo.config.CsvTypeHandler"로 지정해서 씀
 */
public class CsvTypeHandler extends BaseTypeHandler<List<String>> {

    /** 저장할 때: ["bus", "walk"] → "bus,walk" */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> list, JdbcType type) throws SQLException {
        ps.setString(i, String.join(",", list));
    }

    /** 읽을 때(컬럼 이름으로) */
    @Override
    public List<String> getNullableResult(ResultSet rs, String column) throws SQLException {
        return split(rs.getString(column));
    }

    /** 읽을 때(컬럼 번호로) */
    @Override
    public List<String> getNullableResult(ResultSet rs, int index) throws SQLException {
        return split(rs.getString(index));
    }

    /** 읽을 때(프로시저 결과) */
    @Override
    public List<String> getNullableResult(CallableStatement cs, int index) throws SQLException {
        return split(cs.getString(index));
    }

    // "bus, walk" → ["bus", "walk"] (빈 칸은 빼기)
    private static List<String> split(String value) {
        List<String> list = new ArrayList<>();
        if (value == null) {
            return list;
        }
        for (String part : value.split(",")) {
            if (!part.isBlank()) {
                list.add(part.trim());
            }
        }
        return list;
    }
}
