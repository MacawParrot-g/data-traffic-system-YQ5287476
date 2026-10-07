package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface RetestMapper {

    @Select("<script>" +
            "SELECT DISTINCT t.bundleId AS bundleId, g.grade AS grade " +
            "FROM test_static t " +
            "INNER JOIN grade_bundle g ON t.bundleId = g.bundleId " +
            "WHERE g.grade IN ('A', 'B') " +
            "AND t.bundleId IS NOT NULL AND t.bundleId != '' " +
            "AND STR_TO_DATE(CASE WHEN INSTR(t.record_data, '/') > 0 THEN REPLACE(t.record_data, '/', '-') ELSE LEFT(t.record_data, 10) END, '%Y-%m-%d') &gt;= STR_TO_DATE(#{startDate}, '%Y-%m-%d')" +
            "</script>")
    List<Map<String, Object>> selectRetestCandidates(@Param("startDate") String startDate);
}
