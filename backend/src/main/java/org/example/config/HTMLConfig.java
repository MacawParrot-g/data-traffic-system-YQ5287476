package org.example.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class HTMLConfig implements WebMvcConfigurer{
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/html/**")
                .addResourceLocations("classpath:/html/");
    }
}
//这个类是我以前在学校里写的，目的是给让SpringBoot识别resources根目录下的html视图目录，让这个目录下的前端视图可以正常加载
//但是现在我的前端已经改用Vite构建的Vue架构了，这个类就失去了用处
//这个配置类的代码非常古老，从我2026三月开始写毕业项目，一直到我进入公司，变得是时代，不变的还是这串代码。
//当时我还连续几个小时帮女同学调项目呢，她的毕业设计和期末作业就用了我的这个配置类，哈哈
//按理来说，这个类不应该存在于这里，但是我还是留着了，反正不拖累这个业务系统的运行，算是给自己留点念想吧。
//优化这个业务系统，是我作为一个运维开发工程师的职责，但是保留这个类，是我作为一个活生生的人存在的证据
