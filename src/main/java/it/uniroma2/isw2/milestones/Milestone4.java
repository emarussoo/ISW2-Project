package it.uniroma2.isw2.milestones;

import it.uniroma2.isw2.dataset.jira.JiraFetcher;
import it.uniroma2.isw2.model.Release;
import it.uniroma2.isw2.utils.PmdAnalyzer;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Milestone4 {

    static class ClassStats {
        String path;
        int smells;
        long loc;
        int methods;
        int decisions;

        public ClassStats(String path, int smells, long loc, int methods, int decisions) {
            this.path = path;
            this.smells = smells;
            this.loc = loc;
            this.methods = methods;
            this.decisions = decisions;
        }
    }

    static class ClassMetrics {
        boolean isValidSut;
        int methods;
        int decisions;
    }

    public static void main(String[] args) {
        String projectName = "AVRO";
        String projectDir = "/Users/lele/Desktop/ISW2_project/avro";
        String repoPath = projectDir + "/.git";
        String pmdBinPath = "/Users/lele/IdeaProjects/ISW2-Project/pmd/bin/pmd";
        String rulesets = "category/java/design.xml,category/java/errorprone.xml,category/java/bestpractices.xml";

        System.out.println("--- FASE 0 (Milestone 4): Code Smells Ranking sull'ultima Release ---");
        System.out.println("Criteri di filtro attivi (10 regole rigorose):");
        System.out.println("1. Solo file sotto src/main/java");
        System.out.println("2. Esclusi test, examples, integration-test");
        System.out.println("3. Code Smells > 0");
        System.out.println("4. LOC >= 100");
        System.out.println("5. methodCount >= 3");
        System.out.println("6. decisionCount >= 1");
        System.out.println("7. Esclusi Exception.java, Error.java, package-info.java, module-info.java");
        System.out.println("8-10. Escluse interfacce pure, enum banali, classi di sole costanti");

        try {
            // 1. Recupero le release da Jira
            System.out.println("\nRecupero le release di " + projectName + " da Jira...");
            List<Release> allReleases = JiraFetcher.getReleases(projectName);
            if (allReleases.isEmpty()) {
                System.err.println("Nessuna release trovata per il progetto " + projectName);
                return;
            }
            
            // Prendiamo l'ultima release in ordine cronologico
            Release latestRelease = allReleases.get(allReleases.size() - 1);
            System.out.println("L'ultima release trovata è: " + latestRelease.getName());

            // 2. Checkout della release usando JGit
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            Repository repository = builder.setGitDir(new File(repoPath))
                    .readEnvironment()
                    .findGitDir()
                    .setMustExist(true)
                    .build();

            String defaultBranch;
            try (Git git = new Git(repository)) {
                List<Ref> tags = git.tagList().call();
                defaultBranch = repository.getFullBranch();

                Ref matchingTag = findMatchingTag(tags, latestRelease.getName());
                if (matchingTag == null) {
                    System.err.println("Tag non trovato per la release: " + latestRelease.getName());
                    return;
                }

                System.out.println("Effettuo il checkout del tag: " + matchingTag.getName());
                git.checkout().setName(matchingTag.getName()).call();
                
                // 3. Esecuzione PMD
                System.out.println("Esecuzione PMD sul progetto in corso...");
                Map<String, Integer> smellsMap = PmdAnalyzer.extractSmells(projectDir, pmdBinPath, rulesets);

                // Filtriamo e raccogliamo i risultati in una lista
                List<ClassStats> sortedSmells = new ArrayList<>();
                for (Map.Entry<String, Integer> entry : smellsMap.entrySet()) {
                    String path = entry.getKey();
                    int smells = entry.getValue();
                    String lowerPath = path.toLowerCase();
                    
                    // Normalizziamo i separatori di percorso
                    String normalizedPath = lowerPath.replace("\\", "/");

                    // 1. Solo file sotto src/main/java
                    boolean isSrcMainJava = normalizedPath.contains("src/main/java");

                    // 2. Esclusi test, examples, integration-test
                    boolean isTest = normalizedPath.contains("src/test") ||
                                     normalizedPath.contains("/test/") || 
                                     normalizedPath.contains("/testing/") || 
                                     normalizedPath.endsWith("test.java") || 
                                     normalizedPath.endsWith("tests.java") ||
                                     normalizedPath.contains("doc/examples") ||
                                     normalizedPath.contains("integration-test");

                    // 7. Esclusi Exception.java, Error.java, package-info.java, module-info.java
                    boolean isExcludedName = normalizedPath.endsWith("exception.java") ||
                                             normalizedPath.endsWith("error.java") ||
                                             normalizedPath.endsWith("package-info.java") ||
                                             normalizedPath.endsWith("module-info.java");

                    // 3. Code Smells > 0 (implicito nel ciclo se filtriamo > 0)
                    if (isSrcMainJava && !isTest && !isExcludedName && smells > 0) {
                        String fullPath = projectDir + "/" + path;
                        long loc = countLinesOfCode(fullPath);
                        
                        // 4. LOC >= 100
                        if (loc >= 100) {
                            ClassMetrics metrics = analyzeClass(fullPath);
                            
                            // 5, 6, 8, 9, 10
                            if (metrics.isValidSut && metrics.methods >= 3 && metrics.decisions >= 1) {
                                sortedSmells.add(new ClassStats(path, smells, loc, metrics.methods, metrics.decisions));
                            }
                        }
                    }
                }

                // Ordiniamo la lista in ordine decrescente per numero di code smells
                sortedSmells.sort((e1, e2) -> Integer.compare(e2.smells, e1.smells));

                System.out.println("\n--- Top 20 Classi (SUT) filtrate (" + latestRelease.getName() + ") ---");
                if (sortedSmells.isEmpty()) {
                    System.out.println("Nessun code smell trovato o nessuna classe corrispondente ai criteri.");
                } else {
                    for (int i = 0; i < Math.min(20, sortedSmells.size()); i++) {
                        ClassStats entry = sortedSmells.get(i);
                        System.out.println((i + 1) + ". " + entry.path + " -> " + entry.smells + 
                                           " smells (LOC: " + entry.loc + ", Methods: " + entry.methods + 
                                           ", Decisions: " + entry.decisions + ")");
                    }
                }

                // Esportazione in CSV
                File outDir = new File("results/milestone4");
                if (!outDir.exists()) {
                    outDir.mkdirs();
                }

                String csvPath = "results/milestone4/class_smells_ranking.csv";
                try (PrintWriter pw = new PrintWriter(new File(csvPath))) {
                    pw.println("Class,Code Smells,LOC,Methods,Decisions");
                    for (ClassStats entry : sortedSmells) {
                        pw.println(entry.path + "," + entry.smells + "," + entry.loc + "," + entry.methods + "," + entry.decisions);
                    }
                    System.out.println("\n✅ Classifica filtrata esportata in: " + csvPath);
                }

                if (!sortedSmells.isEmpty()) {
                    ClassStats firstClass = sortedSmells.get(0);
                    ClassStats lastClass = sortedSmells.get(sortedSmells.size() - 1);

                    System.out.println("\n--- Dettaglio Smells Prima e Ultima Classe ---");
                    System.out.println("Prima classe: " + firstClass.path);
                    System.out.println("Ultima classe: " + lastClass.path);

                    extractSmellsForClass(projectDir + "/" + firstClass.path, pmdBinPath, rulesets, "results/milestone4/first_class_smells.csv");
                    extractSmellsForClass(projectDir + "/" + lastClass.path, pmdBinPath, rulesets, "results/milestone4/last_class_smells.csv");
                }

                // Ripristiniamo il branch originale
                if (defaultBranch != null) {
                    System.out.println("Ripristino il branch originale: " + defaultBranch);
                    git.checkout().setName(defaultBranch).call();
                }
            }

        } catch (Exception e) {
            System.err.println("Errore durante l'esecuzione: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Ref findMatchingTag(List<Ref> tags, String releaseName) {
        for (Ref tag : tags) {
            if (tag.getName().contains(releaseName)) return tag;
        }
        return null;
    }

    private static long countLinesOfCode(String filePath) {
        try (Stream<String> lines = Files.lines(Paths.get(filePath))) {
            return lines.filter(line -> !line.trim().isEmpty()).count();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Analizza il contenuto della classe per calcolare methodCount, decisionCount
     * e validare le condizioni (interfacce, enum banali, classi sole costanti).
     */
    private static ClassMetrics analyzeClass(String filePath) {
        ClassMetrics metrics = new ClassMetrics();
        metrics.isValidSut = false;
        metrics.methods = 0;
        metrics.decisions = 0;

        try {
            String content = new String(Files.readAllBytes(Paths.get(filePath)));
            // Rimuoviamo i commenti per evitare falsi positivi
            String cleanContent = content.replaceAll("//.*", "");
            cleanContent = cleanContent.replaceAll("(?s)/\\*.*?\\*/", "");

            Matcher mClass = Pattern.compile("\\bclass\\b").matcher(cleanContent);
            Matcher mInterface = Pattern.compile("\\binterface\\b").matcher(cleanContent);
            Matcher mEnum = Pattern.compile("\\benum\\b").matcher(cleanContent);

            boolean hasClass = mClass.find();
            boolean hasInterface = mInterface.find();
            boolean hasEnum = mEnum.find();

            // 8. Scarta interfacce pure
            if (hasInterface && !hasClass && !hasEnum) {
                return metrics;
            }

            // 9. Scarta enum banali
            if (hasEnum && !hasClass) {
                if (!cleanContent.contains(";")) {
                    return metrics;
                }
            }

            // 5. Conta metodi (ignorando costrutti di controllo come if, for, ecc.)
            Matcher mMethod = Pattern.compile("\\b(?!if|for|while|switch|catch)(\\w+)\\s*\\([^{;]*\\)\\s*(?:\\{|throws)").matcher(cleanContent);
            while (mMethod.find()) {
                metrics.methods++;
            }

            // 10. Scarta classi di sole costanti (se è una class ma non ha metodi)
            if (hasClass && metrics.methods == 0) {
                return metrics;
            }

            // 6. Conta decisioni (McCabe euristico)
            Matcher mDecision = Pattern.compile("\\b(if|for|while|case|catch)\\b|\\?").matcher(cleanContent);
            while (mDecision.find()) {
                metrics.decisions++;
            }

            metrics.isValidSut = true;
        } catch (Exception e) {
            // Ignoriamo in caso di fallimento della lettura (SUT non valido)
        }
        return metrics;
    }

    private static void extractSmellsForClass(String fullPath, String pmdBinPath, String rulesets, String outputCsvPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    pmdBinPath,
                    "check",
                    "-d", fullPath,
                    "-R", rulesets,
                    "-f", "csv",
                    "-r", outputCsvPath
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.waitFor();
            System.out.println("Esportato report PMD in: " + outputCsvPath);
        } catch (Exception e) {
            System.err.println("Errore durante l'estrazione degli smell per " + fullPath + ": " + e.getMessage());
        }
    }
}