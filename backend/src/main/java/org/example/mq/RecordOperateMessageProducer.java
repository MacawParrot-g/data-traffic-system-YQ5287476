package org.example.mq;

import org.example.common.UserContext;
import org.example.config.RabbitMQConfig;
import org.example.entity.RecordDeleteMessage;
import org.example.entity.RecordUpdateMessage;
import org.example.entity.TestStatic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RecordOperateMessageProducer {

    private static final Logger log = LoggerFactory.getLogger(RecordOperateMessageProducer.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public boolean sendUpdateMessage(TestStatic record) {
        try {
            String username = UserContext.getUsername();
            RecordUpdateMessage message = new RecordUpdateMessage(record, System.currentTimeMillis(), username);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.UPDATE_EXCHANGE,
                    RabbitMQConfig.UPDATE_ROUTING_KEY,
                    message
            );
            log.info("更新消息已投递至RabbitMQ, URL: {}, user: {}", record.getUrl(), username);
            return true;
        } catch (Exception e) {
            log.error("更新消息投递失败, URL: {}, 原因: {}", record.getUrl(), e.getMessage());
            return false;
        }
    }

    public boolean sendDeleteMessage(String hash) {
        try {
            String username = UserContext.getUsername();
            RecordDeleteMessage message = new RecordDeleteMessage(hash, System.currentTimeMillis(), username);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.DELETE_EXCHANGE,
                    RabbitMQConfig.DELETE_ROUTING_KEY,
                    message
            );
            log.info("删除消息已投递至RabbitMQ, hash: {}, user: {}", hash, username);
            return true;
        } catch (Exception e) {
            log.error("删除消息投递失败, hash: {}, 原因: {}", hash, e.getMessage());
            return false;
        }
    }
}