# ============================================================
# Job Tracker 백엔드 Dockerfile (multi-stage 빌드)
# 1단계: Maven으로 컴파일 → 2단계: JRE만 담은 가벼운 실행 이미지
# ============================================================

# ---- 1단계: 빌드 ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

# 의존성 캐시 최적화: pom.xml만 먼저 복사해 의존성 다운로드
# (소스가 바뀌어도 pom이 같으면 캐시 재사용 → 빌드 빨라짐)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 소스 복사 후 패키징 (테스트는 CI에서 별도 실행하므로 여기선 스킵)
COPY src ./src
RUN mvn package -DskipTests -B

# ---- 2단계: 실행 이미지 (경량화) ----
FROM eclipse-temurin:25-jre
WORKDIR /app

# 1단계에서 만든 jar만 복사 (빌드 도구는 포함하지 않음)
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
