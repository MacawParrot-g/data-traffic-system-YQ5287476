package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("alert_notification")
public class AlertNotification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String receiver;
    private String type;
    private String title;
    private String content;
    private String sender;
    private Boolean acknowledged;
    private String createdAt;
}
