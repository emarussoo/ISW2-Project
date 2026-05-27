package it.uniroma2.isw2.utils;

import it.uniroma2.isw2.model.ClassMetricsRow;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class CsvExporter {

    /**
     * Esporta la lista di ClassMetricsRow in un file CSV.
     *
     * @param dataset La lista dei dati estratti.
     * @param outputPath Il percorso del file CSV da creare (es. "dataset.csv").
     */
    public static void exportToCsv(List<ClassMetricsRow> dataset, String outputPath) {
        System.out.println("\nEsportazione del dataset in CSV in corso...");
        
        // Definiamo le intestazioni delle colonne (le features)
        String[] headers = {
                "Project", "Release", "File", "Normalized_File",
                "Size_LOC", "NR", "NAuth", "NFix", "Age_in_Days", "Weighted_Age",
                "LOC_Added", "Max_LOC_Added", "Average_LOC_Added",
                "LOC_Deleted", "Max_LOC_Deleted", "Average_LOC_Deleted",
                "Churn", "Max_Churn", "Average_Churn",
                "Change_Set_Size", "Max_Change_Set_Size", "Average_Change_Set_Size",
                "Average_ND", "Average_Entropy", "NSmells",
                "Buggy"
        };

        try (FileWriter out = new FileWriter(outputPath);
             CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.builder().setHeader(headers).build())) {

            for (ClassMetricsRow row : dataset) {
                printer.printRecord(
                        row.getProjectName(),
                        row.getReleaseId(),
                        row.getClassName(),
                        row.getNormalizedClassName(),
                        row.getSizeLoc(),
                        row.getNumberOfRevisions(),
                        row.getNumberOfAuthors(),
                        row.getnFix(),
                        row.getAgeInDays(),
                        row.getWeightedAge(),
                        row.getLocAdded(),
                        row.getMaxLocAdded(),
                        row.getAverageLocAdded(),
                        row.getLocDeleted(),
                        row.getMaxLocDeleted(),
                        row.getAverageLocDeleted(),
                        row.getChurn(),
                        row.getMaxChurn(),
                        row.getAverageChurn(),
                        row.getChangeSetSize(),
                        row.getMaxChangeSet(),
                        row.getAverageChangeSet(),
                        String.format(java.util.Locale.US, "%.4f", row.getAverageNd()),
                        String.format(java.util.Locale.US, "%.4f", row.getAverageEntropy()),
                        row.getnSmells(),
                        row.isBuggy() ? "Yes" : "No" // Stampa "No" di default come richiesto
                );
            }
            System.out.println("Dataset esportato con successo in: " + outputPath);

        } catch (IOException e) {
            System.err.println("Errore durante l'esportazione del CSV: " + e.getMessage());
        }
    }
}