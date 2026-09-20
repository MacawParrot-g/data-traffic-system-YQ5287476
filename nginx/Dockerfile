FROM nginx:1.21-alpine

# 删除默认的 Nginx 配置文件，避免冲突
RUN rm /etc/nginx/conf.d/default.conf

# 去掉 nginx/ 前缀，因为构建上下文已经是 nginx 目录
COPY conf.d/ /etc/nginx/conf.d/

# 同样去掉 nginx/ 前缀
COPY html/dist/ /usr/share/nginx/html/
