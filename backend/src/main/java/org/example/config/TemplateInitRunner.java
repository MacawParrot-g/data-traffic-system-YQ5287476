package org.example.config;

import org.example.service.TemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class TemplateInitRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TemplateInitRunner.class);

    @Autowired
    private TemplateService templateService;

    @Override
    public void run(String... args) {
        try {
            templateService.seedIfEmpty();
        } catch (Exception e) {
            log.error("template 种子数据初始化失败: {}", e.getMessage());
        }
    }
}