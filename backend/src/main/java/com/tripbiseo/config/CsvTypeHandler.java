package com.tripbiseo.config;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/** List<String> ↔ "bus,subway,walk" (trip.transports 칸) */
public class CsvTypeHandler extends BaseTypeHandler<List<String>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> list, JdbcType type) throws SQLException {
        ps.setString(i, String.join(",", list));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String column) throws SQLException {
        return split(rs.getString(column));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int index) throws SQLException {
        return split(rs.getString(index));
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int index) throws SQLException {
        return split(cs.getString(index));
    }

    private static List<String> split(String v) {
        return v == null || v.isBlank() ? List.of() : Arrays.stream(v.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
