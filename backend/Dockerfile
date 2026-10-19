# 1. 使用精简版的 Java 17 运行环境作为基础镜像
#FROM openjdk:17-jdk
FROM eclipse-temurin:17-jdk-alpine
# 2. 安装 Python3 用于执行运维脚本
RUN apk add --no-cache python3 && python3 -c "import ensurepip; ensurepip._main()" 2>/dev/null || true
# 3. 设置容器内的工作目录
WORKDIR /app
RUN date +"%Y-%m-%d %H:%M:%S" > /app/build_time.txt
RUN apk add --no-cache mysql-client
# 4. 复制入口点脚本
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh
# 5. 将当前目录下的 jar 包复制到容器内，并改名为 app.jar
COPY target/Automatic_test_script-1.0-SNAPSHOT.jar app.jar
# 6. 声明应用监听的端口
EXPOSE 8080

# 7. 使用入口点脚本启动（先修复venv再启动Java）
ENTRYPOINT ["/docker-entrypoint.sh"]