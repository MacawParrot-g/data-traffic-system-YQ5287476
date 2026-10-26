package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.example.entity.Template;

import java.util.List;

@Mapper
public interface TemplateMapper extends BaseMapper<Template> {

    @Select("SELECT * FROM template WHERE type = #{type} ORDER BY hash")
    List<Template> selectByType(@Param("type") String type);

    @Select("SELECT content FROM template WHERE type = #{type} ORDER BY hash")
    List<String> selectContentByType(@Param("type") String type);

    @Insert("INSERT INTO template (hash, content, type, writer) VALUES (#{hash}, #{content}, #{type}, #{writer})")
    int insertTemplate(Template template);

    @Update("UPDATE template SET content = #{content}, type = #{type} WHERE hash = #{hash}")
    int updateTemplate(Template template);

    @Delete("DELETE FROM template WHERE hash = #{hash}")
    int deleteByHash(@Param("hash") String hash);

    @Select("SELECT COUNT(*) FROM template")
    long countAll();

    @Select("<script>" +
            "SELECT * FROM template WHERE 1=1" +
            "<if test='type != null and type != \"\"'> AND type = #{type}</if>" +
            "<if test='keyword != null and keyword != \"\"'> AND content LIKE CONCAT('%', #{keyword}, '%')</if>" +
            "<if test='writer != null and writer != \"\"'> AND writer LIKE CONCAT('%', #{writer}, '%')</if>" +
            " ORDER BY type ASC, hash DESC" +
            " LIMIT #{size} OFFSET #{offset}" +
            "</script>")
    List<Template> searchTemplates(@Param("type") String type,
                                   @Param("keyword") String keyword,
                                   @Param("writer") String writer,
                                   @Param("size") int size,
                                   @Param("offset") int offset);

    @Select("<script>" +
            "SELECT COUNT(*) FROM template WHERE 1=1" +
            "<if test='type != null and type != \"\"'> AND type = #{type}</if>" +
            "<if test='keyword != null and keyword != \"\"'> AND content LIKE CONCAT('%', #{keyword}, '%')</if>" +
            "<if test='writer != null and writer != \"\"'> AND writer LIKE CONCAT('%', #{writer}, '%')</if>" +
            "</script>")
    long countSearch(@Param("type") String type,
                     @Param("keyword") String keyword,
                     @Param("writer") String writer);
}