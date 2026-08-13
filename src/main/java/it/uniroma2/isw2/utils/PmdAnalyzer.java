package it.uniroma2.isw2.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class PmdAnalyzer {

    // Executes PMD on the specified directory and returns a map linking each relative Java file path to its number of code smells.
    public static Map<String, Integer> extractSmells(String targetDir, String pmdBinPath, String rulesets) throws Exception {
        Map<String, Integer> smellsMap = new HashMap<>();
        String outputCsvPath = "pmd_report.csv";

        // Builds the command to execute PMD
        // Example: pmd check -d /path/to/project -R category/java/design.xml -f csv -r report.csv
        ProcessBuilder pb = new ProcessBuilder(
                pmdBinPath,
                "check",
                "-d", targetDir,
                "-R", rulesets,
                "-f", "csv",
                "-r", outputCsvPath
        );

        // Disables console output to keep the log clean
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Waits for PMD to finish the analysis
        int exitCode = process.waitFor();
        if (exitCode != 0 && exitCode != 4) { // 4 is the PMD exit code when violations are found (expected behavior)
            System.err.println("Attenzione: PMD ha terminato con un codice di uscita imprevisto: " + exitCode);
        }

        // Reads the generated CSV report
        File csvFile = new File(outputCsvPath);
        if (csvFile.exists()) {
            parsePmdCsv(csvFile, smellsMap, targetDir);
            csvFile.delete(); // Cleans up the temporary CSV file
        }

        return smellsMap;
    }

    // Parses the PMD CSV report and aggregates the number of code smells per file.
    private static void parsePmdCsv(File csvFile, Map<String, Integer> smellsMap, String targetDir) {
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            boolean firstLine = true;

            // JGit passes an absolute path; we need the relative part to match GitMetricsExtractor data
            String basePath = new File(targetDir).getAbsolutePath();

            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue; // Skips the CSV header
                }

                // The PMD CSV structure is typically:
                // Problem,Package,File,Priority,Line,Description,Rule set,Rule
                // Uses a split limit of 4 to prevent breaking descriptions containing commas
                String[] parts = line.split(",", 4);
                if (parts.length >= 3) {
                    // Column 3 (index 2) contains the absolute path of the file with the violation
                    String absoluteFilePath = parts[2].replace("\"", "");

                    // Converts to a relative path to match the GitMetricsExtractor data
                    if (absoluteFilePath.startsWith(basePath)) {
                        String relativePath = absoluteFilePath.substring(basePath.length() + 1).replace("\\", "/");

                        // Increments the code smell counter for this file
                        smellsMap.put(relativePath, smellsMap.getOrDefault(relativePath, 0) + 1);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Errore durante la lettura del report PMD: " + e.getMessage());
        }
    }
}