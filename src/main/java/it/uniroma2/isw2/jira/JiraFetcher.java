package it.uniroma2.isw2.jira;

import it.uniroma2.isw2.model.Release;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class JiraFetcher {

    /**
     * Interroga Jira e restituisce una lista ordinata di oggetti Release.
     */
    public static List<Release> getReleases(String projName) throws IOException, JSONException {
        List<Release> releases = new ArrayList<>();

        String url = "https://issues.apache.org/jira/rest/api/2/project/" + projName;
        System.out.println("Scaricando dati delle release da Jira per: " + projName);

        JSONObject json = readJsonFromUrl(url);
        JSONArray versions = json.getJSONArray("versions");

        for (int i = 0; i < versions.length(); i++) {
            JSONObject versionObj = versions.getJSONObject(i);

            // Filtriamo solo le release che hanno una data ufficiale
            if (versionObj.has("releaseDate") && versionObj.has("name") && versionObj.has("id")) {
                String name = versionObj.getString("name");
                String id = versionObj.getString("id");
                String dateStr = versionObj.getString("releaseDate");

                // Convertiamo la stringa in data
                LocalDate date = LocalDate.parse(dateStr);
                LocalDateTime dateTime = date.atStartOfDay();

                // Creiamo il nostro oggetto Model e lo aggiungiamo alla lista
                releases.add(new Release(name, id, dateTime));
            }
        }

        // Ordiniamo la lista cronologicamente dalla release più vecchia alla più recente
        releases.sort(Comparator.comparing(Release::getReleaseDate));

        return releases;
    }

    // --- Metodi di supporto per la lettura dell'URL ---

    private static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
        try (InputStream is = new URL(url).openStream();
             BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String jsonText = readAll(rd);
            return new JSONObject(jsonText);
        }
    }

    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }

    // --- Metodo Main solo per testare questa singola classe ---
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