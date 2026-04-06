## Build Stage ##
FROM docker.io/library/eclipse-temurin:17-jdk AS builder

WORKDIR /app

# Gradle Wrapper 및 설정 파일 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# 소스 복사
COPY src src

# gradlew 실행 권한 부여
RUN chmod +x ./gradlew

# 테스트는 일단 제외하고 빌드
RUN ./gradlew clean bootJar -x test

## Run Stage ##
FROM docker.io/library/eclipse-temurin:17-jre

WORKDIR /app

# 업로드 디렉토리 생성
RUN mkdir -p /app/uploads

# builder stage에서 jar 복사
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]