package it.uniroma2.isw2.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class PmdAnalyzer {

    /**
     * Esegue PMD sulla directory specificata e restituisce una mappa
     * dove la chiave è il path relativo del file Java e il valore è il numero di code smells.
     *
     * @param targetDir   La cartella da analizzare (es. il repository git allo stato di una release)
     * @param pmdBinPath  Il percorso dell'eseguibile PMD (es. /Users/lele/.../pmd/bin/pmd)
     * @param rulesets    Le regole PMD da applicare (es. "category/java/design.xml,category/java/errorprone.xml")
     * @return Una mappa FilePath -> Numero di Smells
     */
    public static Map<String, Integer> extractSmells(String targetDir, String pmdBinPath, String rulesets) throws Exception {
        Map<String, Integer> smellsMap = new HashMap<>();
        String outputCsvPath = "pmd_report.csv";

        // Costruiamo il comando per eseguire PMD
        // Esempio: pmd check -d /path/to/project -R category/java/design.xml -f csv -r report.csv
        ProcessBuilder pb = new ProcessBuilder(
                pmdBinPath,
                "check",
                "-d", targetDir,
                "-R", rulesets,
                "-f", "csv",
                "-r", outputCsvPath
        );

        // Disabilitiamo l'output su console per non sporcare il log
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Aspettiamo che PMD finisca l'analisi
        int exitCode = process.waitFor();
        if (exitCode != 0 && exitCode != 4) { // 4 è l'exit code di PMD se trova violazioni (che è normale)
            System.err.println("Attenzione: PMD ha terminato con un codice di uscita imprevisto: " + exitCode);
        }

        // Leggiamo il CSV generato
        File csvFile = new File(outputCsvPath);
        if (csvFile.exists()) {
            parsePmdCsv(csvFile, smellsMap, targetDir);
            csvFile.delete(); // Pulizia: cancelliamo il file temporaneo
        }

        return smellsMap;
    }

    /**
     * Legge il CSV di PMD e raggruppa gli smells per file.
     */
    private static void parsePmdCsv(File csvFile, Map<String, Integer> smellsMap, String targetDir) {
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            boolean firstLine = true;
            
            // Il path passato da JGit è del tipo /Users/lele/.../avro
            // Per uniformarlo a quello di GitMetricsExtractor, ci serve la parte relativa.
            String basePath = new File(targetDir).getAbsolutePath();

            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue; // Salta l'header del CSV
                }

                // Il CSV di PMD di solito è strutturato così:
                // Problem,Package,File,Priority,Line,Description,Rule set,Rule
                // Usiamo split limitato a 4 per non rompere descrizioni con virgole
                String[] parts = line.split(",", 4); 
                if (parts.length >= 3) {
                    // La colonna 3 (indice 2) contiene il path assoluto del file con la violazione
                    String absoluteFilePath = parts[2].replace("\"", ""); 
                    
                    // Convertiamo in path relativo per fare match con i dati di GitMetricsExtractor
                    if (absoluteFilePath.startsWith(basePath)) {
                        String relativePath = absoluteFilePath.substring(basePath.length() + 1).replace("\\", "/");
                        
                        // Incrementa il contatore degli smells per questo file
                        smellsMap.put(relativePath, smellsMap.getOrDefault(relativePath, 0) + 1);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Errore durante la lettura del report PMD: " + e.getMessage());
        }
    }
}