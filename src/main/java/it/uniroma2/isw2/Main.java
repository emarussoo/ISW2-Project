package it.uniroma2.isw2;

import it.uniroma2.isw2.git.GitMetricsExtractor;
import it.uniroma2.isw2.jira.JiraFetcher;
import it.uniroma2.isw2.model.ClassMetricsRow;
import it.uniroma2.isw2.model.Release;

import java.util.List;

public class Main {

    public static void main(String[] args) {
        String projectName = "AVRO";

        try {
            // 1. Fase Jira: Estrazione di tutte le release
            System.out.println("--- FASE 1: Estrazione Release da Jira ---");
            List<Release> allReleases = JiraFetcher.getReleases(projectName);
            System.out.println("Totale release trovate: " + allReleases.size());

            // 2. Fase Filtro: Ignoriamo l'ultimo 66%
            System.out.println("\n--- FASE 2: Filtraggio Storico ---");
            List<Release> targetReleases = filterReleases(allReleases);

            System.out.println("Release da analizzare (" + targetReleases.size() + "):");
            for (Release r : targetReleases) {
                System.out.println("- " + r.getName() + " (Data: " + r.getReleaseDate().toLocalDate() + ")");
            }

            // 3. Fase Git: Estrazione Metriche Base
            System.out.println("\n--- FASE 3: Estrazione Metriche da Git ---");
            String repoPath = "/Users/lele/Desktop/ISW2_project/avro/.git";

            List<ClassMetricsRow> partialDataset = GitMetricsExtractor.extractMetrics(targetReleases, repoPath);

            System.out.println("\n🎉 FASE 3 COMPLETATA! Estratte " + partialDataset.size() + " righe di dataset totali.");

            // Stampiamo un campione per vedere se funziona
            System.out.println("Esempio di una riga del dataset:");
            if (!partialDataset.isEmpty()) {
                ClassMetricsRow sample = partialDataset.get(0);
                System.out.println("Release: " + sample.getReleaseId() + " | File: " + sample.getClassName());
                System.out.println("LOC: " + sample.getSizeLoc() + " | NR: " + sample.getNumberOfRevisions() + " | NAuth: " + sample.getNumberOfAuthors() + " | NFix: " + sample.getnFix());
                System.out.println("LOC Added (Tot/Max/Avg): " + sample.getLocAdded() + "/" + sample.getMaxLocAdded() + "/" + sample.getAverageLocAdded());
                System.out.println("Churn (Tot/Max/Avg): " + sample.getChurn() + "/" + sample.getMaxChurn() + "/" + sample.getAverageChurn());
                System.out.println("ChangeSet (Tot/Max/Avg): " + sample.getChangeSetSize() + "/" + sample.getMaxChangeSet() + "/" + sample.getAverageChangeSet());
            }

        } catch (Exception e) {
            System.err.println("Errore fatale nell'estrazione: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Data una lista cronologica (dalla più vecchia alla più nuova),
     * scarta il 66% finale e restituisce il restante 34%.
     */
    private static List<Release> filterReleases(List<Release> allReleases) {
        if (allReleases == null || allReleases.isEmpty()) {
            return allReleases;
        }

        // Calcoliamo quanti elementi tenere (circa 1/3)
        int elementsToKeep = (int) Math.round(allReleases.size() * 0.34); // Scarta il 66%

        // Nel caso limite in cui teniamo pochissime release, assicuriamoci di averne almeno una
        if (elementsToKeep == 0) {
            elementsToKeep = 1;
        }

        // Restituisce una sotto-lista partendo dall'indice 0 fino al punto di taglio
        return allReleases.subList(0, elementsToKeep);
    }
}
