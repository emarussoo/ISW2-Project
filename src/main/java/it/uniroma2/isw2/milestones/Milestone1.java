package it.uniroma2.isw2.milestones;

import it.uniroma2.isw2.dataset.git.GitMetricsExtractor;
import it.uniroma2.isw2.dataset.jira.JiraFetcher;
import it.uniroma2.isw2.model.ClassMetricsRow;
import it.uniroma2.isw2.model.Release;
import it.uniroma2.isw2.model.Ticket;
import it.uniroma2.isw2.dataset.szz.BugLabeler;
import it.uniroma2.isw2.dataset.szz.GitBugMapper;
import it.uniroma2.isw2.dataset.szz.VersionLifecycleCalculator;
import it.uniroma2.isw2.utils.CsvExporter;

import java.util.List;

public class Milestone1 {

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

            System.out.println("\nFASE 3 COMPLETATA! Estratte " + partialDataset.size() + " righe di dataset totali.");

            // 4. Fase Labeling: SZZ e Proportion Total
            System.out.println("\n--- FASE 4: Labeling (SZZ e Proportion Total) ---");
            
            // a. Recuperiamo i Bug
            List<Ticket> bugs = JiraFetcher.getBugs(projectName, allReleases);
            
            // b. Applichiamo SZZ per trovare IV, OV e FV
            VersionLifecycleCalculator.assignLifecycleVersions(bugs, allReleases);
            
            // c. Troviamo i file Java modificati dai bug analizzando i commit di Git
            GitBugMapper.mapBugsToFiles(bugs, repoPath);
            
            // d. Etichettiamo il dataset
            BugLabeler.labelDataset(partialDataset, bugs, allReleases);

            // Stampiamo un campione per vedere se funziona
            System.out.println("\nEsempio di una riga del dataset (Tutte le 20 Metriche):");
            if (!partialDataset.isEmpty()) {
                ClassMetricsRow sample = partialDataset.get(0);
                System.out.println("Progetto: " + sample.getProjectName() + " | Release: " + sample.getReleaseId());
                System.out.println("File: " + sample.getClassName());
                System.out.println("Normalized File: " + sample.getNormalizedClassName());
                System.out.println("1.  Size (LOC): " + sample.getSizeLoc());
                System.out.println("2.  Number of Revisions (NR): " + sample.getNumberOfRevisions());
                System.out.println("3.  Number of Authors (NAuth): " + sample.getNumberOfAuthors());
                System.out.println("4.  Number of Fixes (NFix): " + sample.getnFix());
                System.out.println("5.  Age in Days: " + sample.getAgeInDays());
                System.out.println("6.  Weighted Age: " + sample.getWeightedAge());
                System.out.println("7.  LOC Added (Tot): " + sample.getLocAdded());
                System.out.println("8.  Max LOC Added: " + sample.getMaxLocAdded());
                System.out.println("9.  Average LOC Added: " + sample.getAverageLocAdded());
                System.out.println("10. LOC Deleted (Tot): " + sample.getLocDeleted());
                System.out.println("11. Max LOC Deleted: " + sample.getMaxLocDeleted());
                System.out.println("12. Average LOC Deleted: " + sample.getAverageLocDeleted());
                System.out.println("13. Churn (Tot): " + sample.getChurn());
                System.out.println("14. Max Churn: " + sample.getMaxChurn());
                System.out.println("15. Average Churn: " + sample.getAverageChurn());
                System.out.println("16. Change Set Size (Tot): " + sample.getChangeSetSize());
                System.out.println("17. Max Change Set Size: " + sample.getMaxChangeSet());
                System.out.println("18. Average Change Set Size: " + sample.getAverageChangeSet());
                System.out.println("19. Average Number of Modified Directories (ND): " + String.format("%.2f", sample.getAverageNd()));
                System.out.println("20. Average Entropy: " + String.format("%.4f", sample.getAverageEntropy()));
                System.out.println("21. Number of Code Smells (NSmells): " + sample.getnSmells());
                System.out.println("Target (Buggy): " + sample.isBuggy());
            }

            // 5. Fase Esportazione CSV
            String outputCsv = "results/milestone1/avro_metrics_dataset.csv";
            CsvExporter.exportToCsv(partialDataset, outputCsv);
            System.out.println("\n✅ Dataset esportato con successo in: " + outputCsv);

            } catch (Exception e) {
            System.err.println("Errore fatale nell'estrazione: " + e.getMessage());
            e.printStackTrace();
            }    }

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
