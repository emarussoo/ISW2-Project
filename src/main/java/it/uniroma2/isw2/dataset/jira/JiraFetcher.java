package it.uniroma2.isw2.dataset.jira;

import it.uniroma2.isw2.model.Release;
import it.uniroma2.isw2.model.Ticket;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class JiraFetcher {

    // Queries Jira and returns a chronologically ordered list of Release objects.
    public static List<Release> getReleases(String projName) throws IOException, JSONException {
        List<Release> releases = new ArrayList<>();

        String url = "https://issues.apache.org/jira/rest/api/2/project/" + projName;
        System.out.println("Scaricando dati delle release da Jira per: " + projName);

        JSONObject json = readJsonFromUrl(url);
        JSONArray versions = json.getJSONArray("versions");

        for (int i = 0; i < versions.length(); i++) {
            JSONObject versionObj = versions.getJSONObject(i);

            if (versionObj.has("releaseDate") && versionObj.has("name") && versionObj.has("id")) {
                String name = versionObj.getString("name");
                String id = versionObj.getString("id");
                String dateStr = versionObj.getString("releaseDate");

                LocalDate date = LocalDate.parse(dateStr);
                LocalDateTime dateTime = date.atStartOfDay();

                releases.add(new Release(name, id, dateTime));
            }
        }

        releases.sort(Comparator.comparing(Release::getReleaseDate));

        return releases;
    }

    // Retrieves closed and resolved bug tickets, populating their affected versions based on the provided releases.
    public static List<Ticket> getBugs(String projName, List<Release> projectReleases) throws IOException, JSONException {
        List<Ticket> tickets = new ArrayList<>();
        Integer j = 0;
        Integer i = 0;
        Integer total = 1;

        String jql = "project = " + projName + " AND issuetype = Bug AND status in (Closed, Resolved) AND resolution = Fixed ORDER BY key ASC";
        String encodedJql = URLEncoder.encode(jql, StandardCharsets.UTF_8.toString());

        System.out.println("Scaricando ticket Bug da Jira per: " + projName);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

        do {
            j = i + 1000;
            String url = "https://issues.apache.org/jira/rest/api/2/search?jql=" + encodedJql + "&fields=key,resolutiondate,versions,created&startAt=" + i + "&maxResults=" + 1000;
            JSONObject json = readJsonFromUrl(url);

            total = json.getInt("total");
            JSONArray issues = json.getJSONArray("issues");

            for (int k = 0; k < issues.length(); k++) {
                JSONObject issue = issues.getJSONObject(k);
                String key = issue.getString("key");
                JSONObject fields = issue.getJSONObject("fields");

                String createdStr = fields.getString("created");
                String resolutionDateStr = fields.getString("resolutiondate");

                LocalDateTime createdDate = LocalDateTime.parse(createdStr, formatter);
                LocalDateTime resolutionDate = LocalDateTime.parse(resolutionDateStr, formatter);

                Ticket ticket = new Ticket(key, createdDate, resolutionDate);

                if (fields.has("versions")) {
                    JSONArray avArray = fields.getJSONArray("versions");
                    for (int z = 0; z < avArray.length(); z++) {
                        JSONObject avObj = avArray.getJSONObject(z);
                        if (avObj.has("id")) {
                            String avId = avObj.getString("id");
                            Release avRelease = projectReleases.stream()
                                    .filter(r -> r.getId().equals(avId))
                                    .findFirst()
                                    .orElse(null);
                            if (avRelease != null) {
                                ticket.getAffectedVersions().add(avRelease);
                            }
                        }
                    }
                }

                tickets.add(ticket);
            }
            i += 1000;
        } while (i < total);

        System.out.println("Scaricati " + tickets.size() + " ticket con status Closed/Resolved e resolution Fixed.");
        return tickets;
    }

    // Reads and returns a JSON object from the specified URL.
    private static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
        try (InputStream is = new URL(url).openStream();
             BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String jsonText = readAll(rd);
            return new JSONObject(jsonText);
        }
    }

    // Reads all characters from a Reader into a String.
    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }

    // Main method for standalone testing of the class.
    public static void main(String[] args) {
        try {
            List<Release> avroReleases = getReleases("AVRO");
            System.out.println("Trovate " + avroReleases.size() + " release ufficiali.");

            System.out.println("\nPrime 3 release estratte:");
            for (int i = 0; i < Math.min(3, avroReleases.size()); i++) {
                Release r = avroReleases.get(i);
                System.out.println("ID: " + r.getId() + " | Nome: " + r.getName() + " | Data: " + r.getReleaseDate());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}