FROM nginx:1.21-alpine

# 删除默认的 Nginx 配置文件，避免冲突
RUN rm /etc/nginx/conf.d/default.conf

COPY deploy/conf.d/ /etc/nginx/conf.d/

# 清空基础镜像默认站点内容，防止旧 assets 残留叠加
RUN rm -rf /usr/share/nginx/html/*

# 直接使用前端构建产物
COPY dist/ /usr/share/nginx/html/