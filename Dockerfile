# ============================================================
# Job Tracker Dockerfile — 프론트+백엔드 단일 이미지 (클라우드 배포용)
# 1단계: React 빌드 → 2단계: Maven 빌드(static 포함) → 3단계: JRE 실행
# ============================================================

# ---- 1단계: 프론트 빌드 ----
FROM node:24 AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ---- 2단계: 백엔드 빌드 ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

# 의존성 캐시 최적화: pom.xml만 먼저 복사해 의존성 다운로드
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 프론트 빌드 결과물을 Spring Boot static 경로에 복사 (jar에 포함 → 루트로 서빙)
COPY src ./src
COPY --from=frontend /app/frontend/dist ./src/main/resources/static
RUN mvn package -DskipTests -B

# ---- 3단계: 실행 이미지 (경량화) ----
FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
