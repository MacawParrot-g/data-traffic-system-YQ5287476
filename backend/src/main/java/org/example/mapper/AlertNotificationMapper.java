package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.example.entity.AlertNotification;

import java.util.List;

@Mapper
public interface AlertNotificationMapper extends BaseMapper<AlertNotification> {

    @Insert("INSERT INTO alert_notification (receiver, type, title, content, sender, acknowledged, created_at) " +
            "VALUES (#{receiver}, #{type}, #{title}, #{content}, #{sender}, #{acknowledged}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertAlert(AlertNotification alert);

    @Select("SELECT * FROM alert_notification WHERE sender = #{sender} ORDER BY created_at DESC LIMIT #{size} OFFSET #{offset}")
    List<AlertNotification> selectBySender(@Param("sender") String sender, @Param("size") int size, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM alert_notification WHERE sender = #{sender}")
    long countBySender(@Param("sender") String sender);

    @Update("UPDATE alert_notification SET acknowledged = 1 WHERE receiver = #{receiver} AND title = #{title} AND acknowledged = 0")
    int ackByReceiverAndTitle(@Param("receiver") String receiver, @Param("title") String title);

    @Delete("DELETE FROM alert_notification WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("SELECT * FROM alert_notification WHERE receiver = #{receiver} AND acknowledged = 0 ORDER BY created_at DESC")
    List<AlertNotification> selectUnacknowledged(@Param("receiver") String receiver);
}
