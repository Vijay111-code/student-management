package com.schoolapp;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class StudentManagementServer {
    private static final Path DATA_FILE = Path.of("data", "students.db");
    private static final int DEFAULT_PORT = 8080;

    private final StudentRepository repository = new StudentRepository(DATA_FILE);

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", String.valueOf(DEFAULT_PORT)));
        StudentManagementServer app = new StudentManagementServer();
        app.start(port);
    }

    private void start(int port) throws Exception {
        repository.load();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::serveStatic);
        server.createContext("/api/students", this::handleStudents);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.printf("Student Management website is running at http://localhost:%d/%n", port);
        Thread.currentThread().join();
    }

    private void handleStudents(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if ("/api/students".equals(path) && "GET".equals(method)) {
                sendJson(exchange, 200, repository.toJson());
                return;
            }

            if ("/api/students".equals(path) && "POST".equals(method)) {
                Student student = Student.fromMap(readJsonBody(exchange));
                Student created = repository.create(student);
                sendJson(exchange, 201, created.toJson());
                return;
            }

            String prefix = "/api/students/";
            if (path.startsWith(prefix)) {
                int id = Integer.parseInt(path.substring(prefix.length()));
                if ("PUT".equals(method)) {
                    Student student = Student.fromMap(readJsonBody(exchange));
                    Optional<Student> updated = repository.update(id, student);
                    sendJson(exchange, updated.isPresent() ? 200 : 404, updated.map(Student::toJson).orElse("{\"error\":\"Student not found\"}"));
                    return;
                }

                if ("DELETE".equals(method)) {
                    boolean deleted = repository.delete(id);
                    sendJson(exchange, deleted ? 200 : 404, deleted ? "{\"ok\":true}" : "{\"error\":\"Student not found\"}");
                    return;
                }
            }

            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
        } catch (IllegalArgumentException ex) {
            sendJson(exchange, 400, "{\"error\":\"" + Json.escape(ex.getMessage()) + "\"}");
        } catch (Exception ex) {
            sendJson(exchange, 500, "{\"error\":\"Server error\"}");
        }
    }

    private void serveStatic(HttpExchange exchange) throws IOException {
        String requestPath = exchange.getRequestURI().getPath();
        if (requestPath.equals("/")) {
            requestPath = "/index.html";
        }

        Path file = Path.of("public", requestPath.substring(1)).normalize();
        if (!file.startsWith(Path.of("public")) || !Files.exists(file) || Files.isDirectory(file)) {
            sendText(exchange, 404, "Not found", "text/plain; charset=utf-8");
            return;
        }

        String contentType = contentType(file);
        byte[] bytes = Files.readAllBytes(file);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Map<String, String> readJsonBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return Json.parseFlatObject(body);
        }
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        sendText(exchange, statusCode, json, "application/json; charset=utf-8");
    }

    private void sendText(HttpExchange exchange, int statusCode, String text, String contentType) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String contentType(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    static class StudentRepository {
        private final Path file;
        private final List<Student> students = new ArrayList<>();
        private final AtomicInteger nextId = new AtomicInteger(1);

        StudentRepository(Path file) {
            this.file = file;
        }

        synchronized void load() throws IOException {
            Files.createDirectories(file.getParent());
            students.clear();

            if (!Files.exists(file)) {
                seed();
                save();
                return;
            }

            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.isBlank()) continue;
                Student student = Student.fromRecord(line);
                students.add(student);
                nextId.set(Math.max(nextId.get(), student.id + 1));
            }
        }

        synchronized Student create(Student student) throws IOException {
            student.id = nextId.getAndIncrement();
            students.add(student);
            save();
            return student;
        }

        synchronized Optional<Student> update(int id, Student next) throws IOException {
            for (int i = 0; i < students.size(); i++) {
                if (students.get(i).id == id) {
                    next.id = id;
                    students.set(i, next);
                    save();
                    return Optional.of(next);
                }
            }
            return Optional.empty();
        }

        synchronized boolean delete(int id) throws IOException {
            boolean removed = students.removeIf(student -> student.id == id);
            if (removed) save();
            return removed;
        }

        synchronized String toJson() {
            return students.stream()
                .sorted(Comparator.comparing(Student::name))
                .map(Student::toJson)
                .reduce("[", (left, right) -> left.equals("[") ? left + right : left + "," + right) + "]";
        }

        private void seed() {
            students.add(new Student(nextId.getAndIncrement(), "Aarav Mehta", "S1001", "Grade 10", "aarav@example.com", "9876543210", "A", "Active", "Robotics club"));
            students.add(new Student(nextId.getAndIncrement(), "Diya Sharma", "S1002", "Grade 9", "diya@example.com", "9876501234", "A+", "Active", "Class representative"));
            students.add(new Student(nextId.getAndIncrement(), "Kabir Rao", "S1003", "Grade 11", "kabir@example.com", "9123456780", "B+", "Inactive", "Transfer pending"));
        }

        private void save() throws IOException {
            Files.createDirectories(file.getParent());
            List<String> lines = students.stream().map(Student::toRecord).toList();
            Files.write(file, lines, StandardCharsets.UTF_8);
        }
    }

    static class Student {
        int id;
        String name;
        String rollNo;
        String grade;
        String email;
        String phone;
        String score;
        String status;
        String notes;

        Student(int id, String name, String rollNo, String grade, String email, String phone, String score, String status, String notes) {
            this.id = id;
            this.name = name;
            this.rollNo = rollNo;
            this.grade = grade;
            this.email = email;
            this.phone = phone;
            this.score = score;
            this.status = status;
            this.notes = notes;
        }

        static Student fromMap(Map<String, String> values) {
            String name = required(values, "name");
            String rollNo = required(values, "rollNo");
            String grade = required(values, "grade");
            String email = values.getOrDefault("email", "");
            String phone = values.getOrDefault("phone", "");
            String score = values.getOrDefault("score", "");
            String status = values.getOrDefault("status", "Active");
            String notes = values.getOrDefault("notes", "");
            return new Student(0, name, rollNo, grade, email, phone, score, status, notes);
        }

        static Student fromRecord(String record) {
            String[] parts = record.split("\\t", -1);
            if (parts.length != 9) {
                throw new IllegalArgumentException("Invalid student record");
            }
            return new Student(
                Integer.parseInt(parts[0]),
                decode(parts[1]),
                decode(parts[2]),
                decode(parts[3]),
                decode(parts[4]),
                decode(parts[5]),
                decode(parts[6]),
                decode(parts[7]),
                decode(parts[8])
            );
        }

        String toRecord() {
            return id + "\t" + encode(name) + "\t" + encode(rollNo) + "\t" + encode(grade) + "\t" + encode(email) + "\t" + encode(phone) + "\t" + encode(score) + "\t" + encode(status) + "\t" + encode(notes);
        }

        String toJson() {
            return "{" +
                "\"id\":" + id + "," +
                "\"name\":\"" + Json.escape(name) + "\"," +
                "\"rollNo\":\"" + Json.escape(rollNo) + "\"," +
                "\"grade\":\"" + Json.escape(grade) + "\"," +
                "\"email\":\"" + Json.escape(email) + "\"," +
                "\"phone\":\"" + Json.escape(phone) + "\"," +
                "\"score\":\"" + Json.escape(score) + "\"," +
                "\"status\":\"" + Json.escape(status) + "\"," +
                "\"notes\":\"" + Json.escape(notes) + "\"" +
                "}";
        }

        String name() {
            return name;
        }

        private static String required(Map<String, String> values, String key) {
            String value = values.getOrDefault(key, "").trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException(key + " is required");
            }
            return value;
        }

        private static String encode(String value) {
            return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
        }

        private static String decode(String value) {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }
    }

    static class Json {
        static Map<String, String> parseFlatObject(String json) {
            Map<String, String> values = new LinkedHashMap<>();
            String text = json == null ? "" : json.trim();
            if (!text.startsWith("{") || !text.endsWith("}")) {
                throw new IllegalArgumentException("Request body must be a JSON object");
            }

            int i = 1;
            while (i < text.length() - 1) {
                i = skipWhitespaceAndComma(text, i);
                if (i >= text.length() - 1) break;

                Parsed key = parseString(text, i);
                i = skipWhitespace(text, key.nextIndex);
                if (i >= text.length() || text.charAt(i) != ':') {
                    throw new IllegalArgumentException("Invalid JSON object");
                }
                i = skipWhitespace(text, i + 1);

                Parsed value;
                if (text.charAt(i) == '"') {
                    value = parseString(text, i);
                } else {
                    int start = i;
                    while (i < text.length() - 1 && text.charAt(i) != ',') i++;
                    value = new Parsed(text.substring(start, i).trim(), i);
                }

                values.put(key.value, value.value);
                i = value.nextIndex;
            }
            return values;
        }

        static String escape(String value) {
            StringBuilder out = new StringBuilder();
            for (char ch : (value == null ? "" : value).toCharArray()) {
                switch (ch) {
                    case '\\' -> out.append("\\\\");
                    case '"' -> out.append("\\\"");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> out.append(ch);
                }
            }
            return out.toString();
        }

        private static Parsed parseString(String text, int index) {
            if (text.charAt(index) != '"') {
                throw new IllegalArgumentException("Expected JSON string");
            }
            StringBuilder out = new StringBuilder();
            int i = index + 1;
            while (i < text.length()) {
                char ch = text.charAt(i++);
                if (ch == '"') {
                    return new Parsed(out.toString(), i);
                }
                if (ch == '\\' && i < text.length()) {
                    char escaped = text.charAt(i++);
                    switch (escaped) {
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case '"' -> out.append('"');
                        case '\\' -> out.append('\\');
                        default -> out.append(escaped);
                    }
                } else {
                    out.append(ch);
                }
            }
            throw new IllegalArgumentException("Unclosed JSON string");
        }

        private static int skipWhitespaceAndComma(String text, int index) {
            int i = index;
            while (i < text.length() && (Character.isWhitespace(text.charAt(i)) || text.charAt(i) == ',')) i++;
            return i;
        }

        private static int skipWhitespace(String text, int index) {
            int i = index;
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
            return i;
        }

        record Parsed(String value, int nextIndex) {
        }
    }
}
