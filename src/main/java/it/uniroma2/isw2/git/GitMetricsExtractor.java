package it.uniroma2.isw2.git;

import it.uniroma2.isw2.model.ClassMetricsRow;
import it.uniroma2.isw2.model.Release;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GitMetricsExtractor {

    public static List<ClassMetricsRow> extractMetrics(List<Release> targetReleases, String repoPath) throws Exception {
        List<ClassMetricsRow> dataset = new ArrayList<>();
        String projectDir = repoPath.replace("/.git", "");

        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        Repository repository = builder.setGitDir(new File(repoPath)).readEnvironment().findGitDir().setMustExist(true).build();

        try (Git git = new Git(repository)) {
            List<Ref> tags = git.tagList().call();
            String defaultBranch = repository.getFullBranch();

            for (Release release : targetReleases) {
                System.out.println("\nAnalizzando Release: " + release.getName());

                Ref matchingTag = findMatchingTag(tags, release.getName());
                if (matchingTag == null) continue;

                git.checkout().setName(matchingTag.getName()).call();
                List<Path> javaFiles = findJavaFiles(projectDir);
                System.out.println("Elaborazione di " + javaFiles.size() + " file (Questa operazione può richiedere alcuni minuti)...");

                for (Path filePath : javaFiles) {
                    if (filePath.toString().contains("/test/")) continue;

                    String className = filePath.toString().substring(projectDir.length() + 1).replace("\\", "/");
                    ClassMetricsRow row = new ClassMetricsRow("AVRO", release.getName(), className);

                    row.setSizeLoc((int) countLinesOfCode(filePath));

                    // Richiama il nuovo super-metodo
                    computeHistoricalMetrics(repository, git, filePath, projectDir, row);

                    dataset.add(row);
                }
            }

            if (defaultBranch != null) {
                git.checkout().setName(defaultBranch).call();
            }
        }
        return dataset;
    }

    /**
     * Estrae tutte le metriche evolutive per un singolo file ("From release 0").
     */
    private static void computeHistoricalMetrics(Repository repository, Git git, Path filePath, String projectDir, ClassMetricsRow row) {
        String relativePath = filePath.toString().substring(projectDir.length() + 1).replace("\\", "/");

        try {
            Iterable<RevCommit> logs = git.log().addPath(relativePath).call();

            int nr = 0;
            int nFix = 0;
            int totalLocAdded = 0;
            int maxLocAdded = 0;
            int totalChurn = 0;
            int maxChurn = 0;
            int totalChangeSet = 0;
            int maxChangeSet = 0;
            Set<String> authors = new HashSet<>();

            RevCommit firstCommit = null;
            RevCommit lastCommit = null;

            // Prepariamo lo strumento per calcolare le differenze tra commit
            try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                df.setRepository(repository);
                df.setDiffComparator(RawTextComparator.DEFAULT);
                df.setDetectRenames(true);

                for (RevCommit commit : logs) {
                    nr++;
                    authors.add(commit.getAuthorIdent().getEmailAddress());

                    if (lastCommit == null) lastCommit = commit;
                    firstCommit = commit;

                    // 1. Identificazione Bug Fix (NFix)
                    // Controlliamo se il messaggio parla di fix o nomina un ticket Avro (es. "AVRO-123")
                    String msg = commit.getFullMessage().toLowerCase();
                    if (msg.contains("fix") || msg.contains("bug") || msg.contains("avro-")) {
                        nFix++;
                    }

                    // 2. Calcolo Diff (Change Set, LOC Added, Churn)
                    List<DiffEntry> diffs;
                    if (commit.getParentCount() > 0) {
                        // Ha un padre: compariamo il padre con il commit attuale
                        RevCommit parent = commit.getParent(0);
                        diffs = df.scan(parent.getTree(), commit.getTree());
                    } else {
                        // Primo commit in assoluto (senza padre): compariamo con l'albero vuoto
                        AbstractTreeIterator oldTreeIter = new EmptyTreeIterator();
                        ObjectReader reader = repository.newObjectReader();
                        CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
                        newTreeIter.reset(reader, commit.getTree());
                        diffs = df.scan(oldTreeIter, newTreeIter);
                    }

                    // Change Set Size: Quanti file sono stati toccati insieme in questo commit?
                    int changeSetInCommit = diffs.size();
                    totalChangeSet += changeSetInCommit;
                    if (changeSetInCommit > maxChangeSet) maxChangeSet = changeSetInCommit;

                    // LOC e Churn: Troviamo il nostro specifico file nelle differenze di questo commit
                    for (DiffEntry diff : diffs) {
                        if (diff.getNewPath().equals(relativePath) || diff.getOldPath().equals(relativePath)) {
                            FileHeader fileHeader = df.toFileHeader(diff);
                            int locAddedInCommit = 0;
                            int locDeletedInCommit = 0;

                            for (Edit edit : fileHeader.toEditList()) {
                                locAddedInCommit += edit.getLengthB(); // Righe aggiunte
                                locDeletedInCommit += edit.getLengthA(); // Righe cancellate
                            }

                            int churnInCommit = locAddedInCommit + locDeletedInCommit;

                            totalLocAdded += locAddedInCommit;
                            if (locAddedInCommit > maxLocAdded) maxLocAdded = locAddedInCommit;

                            totalChurn += churnInCommit;
                            if (churnInCommit > maxChurn) maxChurn = churnInCommit;
                        }
                    }
                }
            }

            // Calcolo Age
            long ageInDays = 0;
            if (firstCommit != null && lastCommit != null) {
                Instant firstTime = Instant.ofEpochSecond(firstCommit.getCommitTime());
                Instant lastTime = Instant.ofEpochSecond(lastCommit.getCommitTime());
                ageInDays = ChronoUnit.DAYS.between(firstTime, lastTime);
            }

            // --- SALVATAGGIO DEI DATI NELLA RIGA ---
            row.setNumberOfRevisions(nr);
            row.setNumberOfAuthors(authors.size());
            row.setnFix(nFix);
            row.setAgeInDays((int) ageInDays);

            row.setLocAdded(totalLocAdded);
            row.setMaxLocAdded(maxLocAdded);
            row.setAverageLocAdded(nr > 0 ? totalLocAdded / nr : 0);

            row.setChurn(totalChurn);
            row.setMaxChurn(maxChurn);
            row.setAverageChurn(nr > 0 ? totalChurn / nr : 0);

            row.setChangeSetSize(totalChangeSet);
            row.setMaxChangeSet(maxChangeSet);
            row.setAverageChangeSet(nr > 0 ? totalChangeSet / nr : 0);

        } catch (Exception e) {
            System.err.println("Errore nell'estrazione storica per: " + relativePath);
        }
    }

    private static Ref findMatchingTag(List<Ref> tags, String releaseName) {
        for (Ref tag : tags) {
            if (tag.getName().contains(releaseName)) return tag;
        }
        return null;
    }

    private static List<Path> findJavaFiles(String startPath) throws Exception {
        try (Stream<Path> stream = Files.walk(Paths.get(startPath))) {
            return stream.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
    }

    private static long countLinesOfCode(Path filePath) {
        try (Stream<String> lines = Files.lines(filePath)) {
            return lines.filter(line -> !line.trim().isEmpty()).count();
        } catch (Exception e) { return 0; }
    }
}