package org.example.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("template")
public class Template {
    @TableId
    @TableField("hash")
    private String hash;

    @TableField("content")
    private String content;

    @TableField("type")
    private String type;

    @TableField("writer")
    private String writer;
}