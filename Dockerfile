FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY src ./src
RUN javac -d out src/com/schoolapp/StudentManagementServer.java

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/out ./out
COPY public ./public
EXPOSE 8080
CMD ["java", "-cp", "out", "com.schoolapp.StudentManagementServer"]
