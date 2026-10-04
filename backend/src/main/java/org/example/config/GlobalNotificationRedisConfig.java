package org.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class GlobalNotificationRedisConfig implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(GlobalNotificationRedisConfig.class);
    private LettuceConnectionFactory factory;

    @Bean
    public StringRedisTemplate globalNotificationRedisTemplate(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        config.setDatabase(13);
        factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();

        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(factory);
        template.afterPropertiesSet();
        return template;
    }

    @Override
    public void destroy() {
        if (factory != null) {
            try {
                factory.destroy();
                log.info("通知系统Redis连接工厂已关闭");
            } catch (Exception e) {
                log.warn("关闭通知系统Redis连接工厂异常: {}", e.getMessage());
            }
        }
    }
}