package org.example.service.impl;

import org.example.common.Result;
import org.example.config.ShutdownNotifier;
import org.example.entity.AlertNotification;
import org.example.entity.SysUser;
import org.example.mapper.AlertNotificationMapper;
import org.example.mapper.SysUserMapper;
import org.example.service.AlertNotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AlertNotificationServiceImpl implements AlertNotificationService {

    private final AlertNotificationMapper alertNotificationMapper;
    private final SysUserMapper sysUserMapper;
    private final ShutdownNotifier shutdownNotifier;

    public AlertNotificationServiceImpl(AlertNotificationMapper alertNotificationMapper,
                                        SysUserMapper sysUserMapper,
                                        ShutdownNotifier shutdownNotifier) {
        this.alertNotificationMapper = alertNotificationMapper;
        this.sysUserMapper = sysUserMapper;
        this.shutdownNotifier = shutdownNotifier;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result sendAlert(String receivers, String type, String title, String content, String sender) {
        if (title == null || title.isBlank()) return Result.fail("标题不能为空");
        if (type == null || type.isBlank()) type = "INFO";
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        List<String> receiverList;
        if ("ALL".equalsIgnoreCase(receivers)) {
            receiverList = sysUserMapper.findAll().stream().map(SysUser::getName).toList();
        } else {
            receiverList = List.of(receivers.split(","));
        }

        int count = 0;
        for (String r : receiverList) {
            String trimmed = r.trim();
            if (trimmed.isEmpty()) continue;
            AlertNotification alert = new AlertNotification();
            alert.setReceiver(trimmed);
            alert.setType(type);
            alert.setTitle(title.trim());
            alert.setContent(content != null ? content.trim() : "");
            alert.setSender(sender);
            alert.setAcknowledged(false);
            alert.setCreatedAt(now);
            alertNotificationMapper.insertAlert(alert);
            String json = String.format("{\"title\":\"%s\",\"content\":\"%s\",\"type\":\"%s\"}",
                    escapeJson(title.trim()),
                    escapeJson(content != null ? content.trim() : ""),
                    escapeJson(type));
            shutdownNotifier.sendToUser(trimmed, "alert", json);
            count++;
        }
        return Result.success("通知已推送至 " + count + " 个用户");
    }

    @Override
    @Transactional(readOnly = true)
    public Result getSentAlerts(String sender, int page, int size) {
        int offset = (page - 1) * size;
        List<AlertNotification> list = alertNotificationMapper.selectBySender(sender, size, offset);
        long total = alertNotificationMapper.countBySender(sender);
        return Result.success("查询成功", list, null, total, page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result acknowledgeAlert(String receiver, String title) {
        if (receiver == null || receiver.isBlank() || title == null || title.isBlank()) {
            return Result.fail("参数不完整");
        }
        alertNotificationMapper.ackByReceiverAndTitle(receiver, title);
        return Result.success("已确认");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result deleteAlert(Long id) {
        if (id == null) return Result.fail("ID不能为空");
        int count = alertNotificationMapper.deleteById(id);
        return count > 0 ? Result.success("删除成功") : Result.fail("通知不存在或已删除");
    }

    @Override
    @Transactional(readOnly = true)
    public Result getUnacknowledged(String receiver) {
        List<AlertNotification> list = alertNotificationMapper.selectUnacknowledged(receiver);
        return Result.success("查询成功", list);
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
