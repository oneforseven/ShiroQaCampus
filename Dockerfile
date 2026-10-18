# ============================================================
# shiro-ai-agent 后端：Spring Boot 3.4.7 / Java 21 / Maven
# 构建：docker build -t shiro-ai-agent:latest .
# ============================================================

# ---------- 阶段 1：构建 ----------
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# 先只拷 pom，利用 Docker 层缓存下载依赖
COPY pom.xml ./

# 然后取消下面这行注释
# COPY docker-mvn-settings.xml /root/.m2/settings.xml
RUN mvn -B dependency:go-offline -DskipTests || true

# 拷源码构建（跳过测试：测试用例依赖数据库等外部环境）
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------- 阶段 2：运行 ----------
FROM eclipse-temurin:21-jre-alpine
LABEL description="shiro-ai-agent backend"

RUN apk add --no-cache tzdata

# RUN apk add --no-cache nodejs npm

# 非 root 运行
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
RUN mkdir -p /app/tmp && chown -R app:app /app
# 应用写盘目录（聊天记忆 / PDF / 下载文件），用 volume 挂载持久化
VOLUME ["/app/tmp"]

COPY --from=builder /build/target/shiro-ai-agent-0.0.1-SNAPSHOT.jar app.jar

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom" \
    SPRING_PROFILES_ACTIVE=prod \
    TZ=Asia/Shanghai

EXPOSE 8123
USER app
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]