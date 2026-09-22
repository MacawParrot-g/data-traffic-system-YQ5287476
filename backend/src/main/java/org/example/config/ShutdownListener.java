package org.example.config;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

@Component
public class ShutdownListener implements ApplicationListener<ContextClosedEvent> {

    private final ShutdownNotifier shutdownNotifier;

    public ShutdownListener(ShutdownNotifier shutdownNotifier) {
        this.shutdownNotifier = shutdownNotifier;
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        shutdownNotifier.notifyAll("shutdown", "后端服务正在关闭，请重新登录或联系系统管理员");
    }
}