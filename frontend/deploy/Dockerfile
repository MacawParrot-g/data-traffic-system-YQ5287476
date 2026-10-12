FROM nginx:1.21-alpine

# 删除默认的 Nginx 配置文件，避免冲突
RUN rm /etc/nginx/conf.d/default.conf

COPY deploy/conf.d/ /etc/nginx/conf.d/

# 直接使用前端构建产物
COPY dist/ /usr/share/nginx/html/
