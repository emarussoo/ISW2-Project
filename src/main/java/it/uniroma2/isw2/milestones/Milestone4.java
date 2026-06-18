package it.uniroma2.isw2.milestones;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class Milestone4 {

    public static void main(String[] args) {

        // 1. Load variables from the .env file
        String projectKey = "";
        String apiToken = "";

        try (FileInputStream fis = new FileInputStream(".env")) {
            Properties env = new Properties();
            env.load(fis);

            projectKey = env.getProperty("SONAR_PROJECT_KEY");
            apiToken = env.getProperty("SONAR_TOKEN");

            // Security check if the .env file is empty or missing keys
            if (projectKey == null || apiToken == null) {
                System.err.println("Error: SONAR_PROJECT_KEY or SONAR_TOKEN missing in the .env file");
                return;
            }
        } catch (Exception e) {
            System.err.println("Error: Unable to read the .env file. Ensure it exists in the project root.");
            return;
        }

        // Filter parameters
        int minLinesOfCode = 150;
        int minSmells = 1;

        try {
            HttpClient httpClient = HttpClient.newHttpClient();
            Gson gson = new Gson();
            String auth = Base64.getEncoder().encodeToString((apiToken + ":").getBytes());

            // Expanded array: [0] Path, [1] Smells, [2] LOC, [3] Exact Key
            List<String[]> foundClasses = new ArrayList<>();
            int page = 1;
            int pageSize = 500;
            int total = 0;

            System.out.println("1. Scanning the project...");

            // Retrieve the component tree to find files and metrics
            do {
                String url = String.format("https://sonarcloud.io/api/measures/component_tree?component=%s&metricKeys=code_smells,ncloc&qualifiers=FIL&ps=%d&p=%d",
                        projectKey, pageSize, page);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Basic " + auth)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);
                JsonArray components = jsonResponse.getAsJsonArray("components");

                for (JsonElement element : components) {
                    JsonObject component = element.getAsJsonObject();
                    String path = component.get("path").getAsString();
                    String exactKey = component.get("key").getAsString();

                    if (path.endsWith(".java")) {
                        JsonArray measures = component.getAsJsonArray("measures");
                        int smells = 0;
                        int ncloc = 0;

                        if (measures != null) {
                            for (JsonElement mElement : measures) {
                                JsonObject measureObj = mElement.getAsJsonObject();
                                String metric = measureObj.get("metric").getAsString();

                                if (measureObj.has("value")) {
                                    int val = Integer.parseInt(measureObj.get("value").getAsString());
                                    if ("code_smells".equals(metric)) smells = val;
                                    if ("ncloc".equals(metric)) ncloc = val;
                                }
                            }
                        }

                        if (ncloc > minLinesOfCode && smells >= minSmells) {
                            foundClasses.add(new String[]{path, String.valueOf(smells), String.valueOf(ncloc), exactKey});
                        }
                    }
                }

                total = jsonResponse.getAsJsonObject("paging").get("total").getAsInt();
                page++;

            } while ((page - 1) * pageSize < total);

            if (foundClasses.isEmpty()) {
                System.out.println("No classes found matching the specified criteria.");
                return;
            }

            // Sort the list in descending order by number of smells
            foundClasses.sort((a, b) -> Integer.parseInt(b[1]) - Integer.parseInt(a[1]));

            // 2. Export the general report with ALL classes
            System.out.println("\n2. Generating complete report: smells_ranking.csv...");
            try (PrintWriter writer = new PrintWriter(new FileWriter("smells_ranking.csv"))) {
                writer.println("Class_Path,Code_Smells,Lines_Of_Code");
                for (String[] data : foundClasses) {
                    writer.printf("\"%s\",%s,%s%n", data[0], data[1], data[2]);
                }
            }

            // 3. Identify the first and last class and export the details
            String[] worstClass = foundClasses.get(0);
            String[] bestClass = foundClasses.get(foundClasses.size() - 1);

            System.out.println("\n3. Extracting details for the extremes:");
            System.out.println("- Worst: " + worstClass[0] + " (" + worstClass[1] + " smells)");
            System.out.println("- Best (above threshold): " + bestClass[0] + " (" + bestClass[1] + " smells)\n");

            exportSmellsDetail(httpClient, gson, auth, worstClass[3], "first_class_smells.csv");

            if (foundClasses.size() > 1) {
                exportSmellsDetail(httpClient, gson, auth, bestClass[3], "last_class_smells.csv");
            }

            System.out.println("\nProcess completed successfully!");

        } catch (Exception e) {
            System.err.println("An error occurred:");
            e.printStackTrace();
        }
    }

    private static void exportSmellsDetail(HttpClient client, Gson gson, String auth, String componentKey, String outputCsvName) throws Exception {
        String encodedComponent = URLEncoder.encode(componentKey, StandardCharsets.UTF_8);
        String url = "https://sonarcloud.io/api/issues/search?componentKeys=" + encodedComponent + "&types=CODE_SMELL&ps=500";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Basic " + auth)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);
        JsonArray issues = jsonResponse.getAsJsonArray("issues");

        int count = issues != null ? issues.size() : 0;
        System.out.printf("  -> Writing %s (%d issues found)...%n", outputCsvName, count);

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputCsvName))) {
            writer.println("Line,Severity,Rule,Message");

            if (issues != null && count > 0) {
                for (JsonElement element : issues) {
                    JsonObject issue = element.getAsJsonObject();

                    String line = issue.has("line") ? issue.get("line").getAsString() : "N/A";
                    String severity = issue.has("severity") ? issue.get("severity").getAsString() : "N/A";
                    String rule = issue.has("rule") ? issue.get("rule").getAsString() : "N/A";
                    String message = issue.has("message") ? issue.get("message").getAsString() : "N/A";

                    message = message.replace("\"", "'");
                    writer.printf("%s,%s,%s,\"%s\"%n", line, severity, rule, message);
                }
            } else {
                writer.println("N/A,N/A,N/A,\"No details returned by the API\"");
            }
        }
    }
}