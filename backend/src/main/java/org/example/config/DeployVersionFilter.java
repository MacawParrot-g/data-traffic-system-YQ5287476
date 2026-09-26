package org.example.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
public class DeployVersionFilter implements Filter {

    private static final String DEPLOY_ID;

    static {
        DEPLOY_ID = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static String getDeployId() {
        return DEPLOY_ID;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setHeader("X-Deploy-Id", DEPLOY_ID);
        chain.doFilter(request, response);
    }
}
