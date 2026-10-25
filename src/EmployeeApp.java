
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class EmployeeApp {

    static List<Employee> employees = new ArrayList<>();
    static final String FILE_NAME = "employees.csv";

    // Used to protect employee data when multiple threads access it
    static final Object lock = new Object();

    static String message = "Welcome to Employee Management System!";

    public static void main(String[] args) throws IOException {

        // Load existing employee records
        loadEmployees();

        // Background thread: save data every 30 seconds
        Thread autoSave = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(30000);
                    saveEmployees();
                    System.out.println("Employee data auto-saved.");
                } catch (InterruptedException e) {
                    System.out.println("Auto-save stopped.");
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        autoSave.setDaemon(true);
        autoSave.start();

        // Start web server on port 8080
        HttpServer server = HttpServer.create(
                new InetSocketAddress(8080), 0);

        // Show the home page
        server.createContext("/", exchange -> {
            String html = Files.readString(
                    new File("index.html").toPath());

            StringBuilder rows = new StringBuilder();

            synchronized (lock) {
                for (Employee e : employees) {
                    rows.append("<tr>")
                            .append("<td>").append(escape(e.getId())).append("</td>")
                            .append("<td>").append(escape(e.getName())).append("</td>")
                            .append("<td>").append(escape(e.getDepartment())).append("</td>")
                            .append("<td>Rs. ").append(e.getSalary()).append("</td>")
                            .append("</tr>");
                }
            }

            if (rows.length() == 0) {
                rows.append("<tr><td colspan='4'>No employees found.</td></tr>");
            }

            html = html.replace("{{MESSAGE}}", escape(message));
            html = html.replace("{{ROWS}}", rows.toString());

            send(exchange, html, "text/html");
        });

        // Add employee
        server.createContext("/add", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                send(exchange, "Use the employee form.", "text/plain");
                return;
            }

            Map<String, String> data = readForm(exchange);

            try {
                String id = data.getOrDefault("id", "").trim();
                String name = data.getOrDefault("name", "").trim();
                String department = data.getOrDefault("department", "").trim();
                double salary = Double.parseDouble(
                        data.getOrDefault("salary", ""));

                if (id.isEmpty() || name.isEmpty() || department.isEmpty()) {
                    throw new IllegalArgumentException(
                            "All employee fields are required.");
                }

                if (!Double.isFinite(salary) || salary < 0) {
                    throw new IllegalArgumentException(
                            "Salary must be a valid, non-negative number.");
                }

                synchronized (lock) {
                    for (Employee e : employees) {
                        if (e.getId().equalsIgnoreCase(id)) {
                            throw new IllegalArgumentException(
                                    "Employee ID already exists.");
                        }
                    }

                    employees.add(new Employee(id, name, department, salary));
                }

                saveEmployees();
                message = "Employee added successfully!";

            } catch (NumberFormatException e) {
                message = "Error: Enter a valid salary.";
            } catch (IllegalArgumentException e) {
                message = "Error: " + e.getMessage();
            } catch (Exception e) {
                message = "Error: Could not add employee.";
            }

            redirect(exchange);
        });

        // Delete employee
        server.createContext("/delete", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                send(exchange, "Use the delete form.", "text/plain");
                return;
            }

            Map<String, String> data = readForm(exchange);
            String id = data.getOrDefault("id", "").trim();

            try {
                boolean found = false;

                synchronized (lock) {
                    Iterator<Employee> iterator = employees.iterator();

                    while (iterator.hasNext()) {
                        Employee e = iterator.next();

                        if (e.getId().equalsIgnoreCase(id)) {
                            iterator.remove();
                            found = true;
                            break;
                        }
                    }
                }

                if (!found) {
                    throw new Exception("Employee ID not found: " + id);
                }

                saveEmployees();
                message = "Employee deleted successfully!";

            } catch (Exception e) {
                message = "Error: " + e.getMessage();
            }

            redirect(exchange);
        });

        // Serve the CSS file
        server.createContext("/style.css", exchange -> {
            try {
                String css = Files.readString(new File("style.css").toPath());
                send(exchange, css, "text/css");
            } catch (IOException e) {
                send(exchange, "CSS file not found.", "text/plain");
            }
        });

        System.out.println("Server started at http://localhost:8080");
        server.start();
    }

    // Read form data sent by the browser
    static Map<String, String> readForm(HttpExchange exchange)
            throws IOException {

        String body = new String(
                exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);

        Map<String, String> data = new HashMap<>();

        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);

            if (parts.length == 2) {
                data.put(
                        URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                );
            }
        }

        return data;
    }

    // Save employee records in a CSV file
    static void saveEmployees() {
        synchronized (lock) {
            try (BufferedWriter writer =
                         new BufferedWriter(new FileWriter(FILE_NAME))) {

                for (Employee e : employees) {
                    writer.write(e.toCsv());
                    writer.newLine();
                }

            } catch (IOException e) {
                System.out.println("File saving error: " + e.getMessage());
            }
        }
    }

    // Load employee records from a CSV file
    static void loadEmployees() {
        File file = new File(FILE_NAME);

        if (!file.exists()) {
            return;
        }

        synchronized (lock) {
            try (BufferedReader reader =
                         new BufferedReader(new FileReader(file))) {

                String line;

                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",", -1);

                    if (parts.length == 4) {
                        try {
                            double salary = Double.parseDouble(parts[3]);

                            employees.add(new Employee(
                                    parts[0], parts[1],
                                    parts[2], salary));
                        } catch (NumberFormatException e) {
                            System.out.println("Skipping invalid employee record.");
                        }
                    }
                }

            } catch (IOException e) {
                System.out.println("File reading error: " + e.getMessage());
            }
        }
    }

    // Prevent employee text from being treated as HTML
    static String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    // Send a response to the browser
    static void send(HttpExchange exchange, String content, String type)
            throws IOException {

        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type", type + "; charset=UTF-8");

        exchange.sendResponseHeaders(200, bytes.length);

        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    // Return to the home page after a form submission
    static void redirect(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Location", "/");
        exchange.sendResponseHeaders(303, -1);
        exchange.close();
    }
}
