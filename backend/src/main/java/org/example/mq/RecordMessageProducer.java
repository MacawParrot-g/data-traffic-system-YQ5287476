package org.example.mq;

import org.example.common.UserContext;
import org.example.config.RabbitMQConfig;
import org.example.entity.RecordInsertMessage;
import org.example.entity.TestStatic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RecordMessageProducer {

    private static final Logger log = LoggerFactory.getLogger(RecordMessageProducer.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public boolean sendInsertMessage(TestStatic record) {
        try {
            String username = UserContext.getUsername();
            RecordInsertMessage message = new RecordInsertMessage(record, System.currentTimeMillis(), username);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.RECORD_EXCHANGE,
                    RabbitMQConfig.RECORD_ROUTING_KEY,
                    message
            );
            log.info("入库消息已投递至RabbitMQ, URL: {}, user: {}", record.getUrl(), username);
            return true;
        } catch (Exception e) {
            log.error("RabbitMQ消息投递失败, URL: {}, 原因: {}", record.getUrl(), e.getMessage());
            return false;
        }
    }
}