package it.uniroma2.isw2.dataset.git;

import it.uniroma2.isw2.model.ClassMetricsRow;
import it.uniroma2.isw2.model.Release;
import it.uniroma2.isw2.utils.PmdAnalyzer;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Extracts code and bug metrics for all Java files across the specified releases.
public class GitMetricsExtractor {

    // Checks out each release and processes all Java files to compute their metrics.
    public static List<ClassMetricsRow> extractMetrics(List<Release> targetReleases, String repoPath) throws Exception {
        List<ClassMetricsRow> dataset = new ArrayList<>();
        String projectDir = repoPath.replace("/.git", "");

        String pmdBinPath = "/Users/lele/IdeaProjects/ISW2-Project/pmd/bin/pmd";
        // Uses classic rulesets for code quality and design
        String rulesets = "category/java/design.xml,category/java/errorprone.xml,category/java/bestpractices.xml";

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

                // Runs PMD on the entire project at the current release state
                System.out.println("Esecuzione PMD in corso per la ricerca dei Code Smells...");
                Map<String, Integer> smellsMap = PmdAnalyzer.extractSmells(projectDir, pmdBinPath, rulesets);

                for (Path filePath : javaFiles) {
                    if (filePath.toString().contains("/test/")) continue;

                    String className = filePath.toString().substring(projectDir.length() + 1).replace("\\", "/");
                    String normalizedClassName = extractNormalizedClassName(className);
                    ClassMetricsRow row = new ClassMetricsRow("AVRO", release.getName(), className, normalizedClassName);

                    row.setSizeLoc((int) countLinesOfCode(filePath));

                    // Sets the number of smells found by PMD for this file
                    row.setnSmells(smellsMap.getOrDefault(className, 0));

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

    // Computes evolutionary and historical metrics for a specific file using git log.
    private static void computeHistoricalMetrics(Repository repository, Git git, Path filePath, String projectDir, ClassMetricsRow row) {
        String relativePath = filePath.toString().substring(projectDir.length() + 1).replace("\\", "/");

        try (org.eclipse.jgit.revwalk.RevWalk walk = new org.eclipse.jgit.revwalk.RevWalk(repository)) {
            walk.markStart(walk.parseCommit(repository.resolve("HEAD")));
            org.eclipse.jgit.diff.DiffConfig diffConfig = repository.getConfig().get(org.eclipse.jgit.diff.DiffConfig.KEY);
            org.eclipse.jgit.revwalk.FollowFilter followFilter = org.eclipse.jgit.revwalk.FollowFilter.create(relativePath, diffConfig);
            walk.setTreeFilter(followFilter);

            int nr = 0;
            int nFix = 0;
            int totalLocAdded = 0;
            int maxLocAdded = 0;
            int totalLocDeleted = 0;
            int maxLocDeleted = 0;
            int totalChurn = 0;
            int maxChurn = 0;
            int totalChangeSet = 0;
            int maxChangeSet = 0;
            int totalNd = 0;
            double totalEntropy = 0.0;
            Set<String> authors = new HashSet<>();

            RevCommit firstCommit = null;
            RevCommit lastCommit = null;
            String trackedPath = relativePath;

            try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                df.setRepository(repository);
                df.setDiffComparator(RawTextComparator.DEFAULT);
                df.setDetectRenames(true);

                for (RevCommit commit : walk) {
                    nr++;
                    authors.add(commit.getAuthorIdent().getEmailAddress());

                    if (lastCommit == null) lastCommit = commit;
                    firstCommit = commit;

                    if (isBugFix(commit)) {
                        nFix++;
                    }

                    List<DiffEntry> diffs = getCommitDiffs(repository, df, commit);

                    int changeSetInCommit = diffs.size();
                    totalChangeSet += changeSetInCommit;
                    if (changeSetInCommit > maxChangeSet) maxChangeSet = changeSetInCommit;

                    totalNd += computeNd(diffs);

                    CommitMetrics commitMetrics = processCommitDiffs(df, diffs, trackedPath);

                    // Update trackedPath if there is a rename/move in this commit
                    for (DiffEntry diff : diffs) {
                        if (diff.getNewPath().equals(trackedPath) && !diff.getOldPath().equals(trackedPath) && !diff.getOldPath().equals("/dev/null")) {
                            trackedPath = diff.getOldPath();
                            break;
                        }
                    }

                    int churnInCommit = commitMetrics.myLocAdded + commitMetrics.myLocDeleted;

                    totalLocAdded += commitMetrics.myLocAdded;
                    if (commitMetrics.myLocAdded > maxLocAdded) maxLocAdded = commitMetrics.myLocAdded;

                    totalLocDeleted += commitMetrics.myLocDeleted;
                    if (commitMetrics.myLocDeleted > maxLocDeleted) maxLocDeleted = commitMetrics.myLocDeleted;

                    totalChurn += churnInCommit;
                    if (churnInCommit > maxChurn) maxChurn = churnInCommit;

                    totalEntropy += commitMetrics.entropy;
                }
            }

            long ageInDays = computeAgeInDays(firstCommit, lastCommit);

            populateRow(row, nr, authors.size(), nFix, ageInDays, totalLocAdded, maxLocAdded,
                    totalLocDeleted, maxLocDeleted, totalChurn, maxChurn, totalChangeSet,
                    maxChangeSet, totalNd, totalEntropy);

        } catch (Exception e) {
            System.err.println("Errore nell'estrazione storica per: " + relativePath);
        }
    }

    // Determines if a commit is a bug fix based on keywords in its message.
    private static boolean isBugFix(RevCommit commit) {
        String msg = commit.getFullMessage().toLowerCase();
        return msg.contains("fix") || msg.contains("bug") || msg.contains("avro-");
    }

    // Calculates the file differences introduced by a commit compared to its parent.
    private static List<DiffEntry> getCommitDiffs(Repository repository, DiffFormatter df, RevCommit commit) throws Exception {
        if (commit.getParentCount() > 0) {
            RevCommit parent = commit.getParent(0);
            return df.scan(parent.getTree(), commit.getTree());
        } else {
            AbstractTreeIterator oldTreeIter = new EmptyTreeIterator();
            ObjectReader reader = repository.newObjectReader();
            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            newTreeIter.reset(reader, commit.getTree());
            return df.scan(oldTreeIter, newTreeIter);
        }
    }

    // Calculates the Number of Directories (ND) modified in a commit.
    private static int computeNd(List<DiffEntry> diffs) {
        Set<String> directories = new HashSet<>();
        for (DiffEntry d : diffs) {
            String path = d.getNewPath().equals("/dev/null") ? d.getOldPath() : d.getNewPath();
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash > 0) {
                directories.add(path.substring(0, lastSlash));
            } else {
                directories.add("/");
            }
        }
        return directories.size();
    }

    // Analyzes commit differences to compute LOC added/deleted and commit entropy.
    private static CommitMetrics processCommitDiffs(DiffFormatter df, List<DiffEntry> diffs, String relativePath) throws Exception {
        CommitMetrics metrics = new CommitMetrics();
        int totalLocModifiedInCommit = 0;
        List<Integer> locModifiedPerFile = new ArrayList<>();

        for (DiffEntry diff : diffs) {
            FileHeader fileHeader = df.toFileHeader(diff);
            int locAddedInFile = 0;
            int locDeletedInFile = 0;

            for (Edit edit : fileHeader.toEditList()) {
                locAddedInFile += edit.getLengthB();
                locDeletedInFile += edit.getLengthA();
            }

            int locModifiedInFile = locAddedInFile + locDeletedInFile;
            totalLocModifiedInCommit += locModifiedInFile;
            locModifiedPerFile.add(locModifiedInFile);

            if (diff.getNewPath().equals(relativePath) || diff.getOldPath().equals(relativePath)) {
                metrics.myLocAdded = locAddedInFile;
                metrics.myLocDeleted = locDeletedInFile;
            }
        }

        if (totalLocModifiedInCommit > 0) {
            for (int loc : locModifiedPerFile) {
                if (loc > 0) {
                    double p = (double) loc / totalLocModifiedInCommit;
                    metrics.entropy -= (p * (Math.log(p) / Math.log(2)));
                }
            }
        }
        return metrics;
    }

    // Calculates the age of a file in days between its first and last commit.
    private static long computeAgeInDays(RevCommit firstCommit, RevCommit lastCommit) {
        if (firstCommit != null && lastCommit != null) {
            Instant firstTime = Instant.ofEpochSecond(firstCommit.getCommitTime());
            Instant lastTime = Instant.ofEpochSecond(lastCommit.getCommitTime());
            return ChronoUnit.DAYS.between(firstTime, lastTime);
        }
        return 0;
    }

    // Populates the metrics row with totals, maximums, and calculated averages.
    private static void populateRow(ClassMetricsRow row, int nr, int numAuthors, int nFix, long ageInDays,
                                    int totalLocAdded, int maxLocAdded, int totalLocDeleted, int maxLocDeleted,
                                    int totalChurn, int maxChurn, int totalChangeSet, int maxChangeSet,
                                    int totalNd, double totalEntropy) {
        row.setNumberOfRevisions(nr);
        row.setNumberOfAuthors(numAuthors);
        row.setnFix(nFix);
        row.setAgeInDays((int) ageInDays);
        row.setWeightedAge(ageInDays * totalChurn);

        row.setLocAdded(totalLocAdded);
        row.setMaxLocAdded(maxLocAdded);
        row.setAverageLocAdded(nr > 0 ? totalLocAdded / nr : 0);

        row.setLocDeleted(totalLocDeleted);
        row.setMaxLocDeleted(maxLocDeleted);
        row.setAverageLocDeleted(nr > 0 ? totalLocDeleted / nr : 0);

        row.setChurn(totalChurn);
        row.setMaxChurn(maxChurn);
        row.setAverageChurn(nr > 0 ? totalChurn / nr : 0);

        row.setChangeSetSize(totalChangeSet);
        row.setMaxChangeSet(maxChangeSet);
        row.setAverageChangeSet(nr > 0 ? totalChangeSet / nr : 0);

        row.setAverageNd(nr > 0 ? (double) totalNd / nr : 0.0);
        row.setAverageEntropy(nr > 0 ? totalEntropy / nr : 0.0);
    }

    // Finds and returns the Git tag corresponding to the given release name.
    private static Ref findMatchingTag(List<Ref> tags, String releaseName) {
        for (Ref tag : tags) {
            if (tag.getName().contains(releaseName)) return tag;
        }
        return null;
    }

    // Recursively finds all Java files starting from the given directory path.
    private static List<Path> findJavaFiles(String startPath) throws Exception {
        try (Stream<Path> stream = Files.walk(Paths.get(startPath))) {
            return stream.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
    }

    // Counts the non-empty lines of code (Size) for a given file.
    private static long countLinesOfCode(Path filePath) {
        try (Stream<String> lines = Files.lines(filePath)) {
            return lines.filter(line -> !line.trim().isEmpty()).count();
        } catch (Exception e) { return 0; }
    }

    // Extracts the normalized class name by removing release-specific prefixes.
    private static String extractNormalizedClassName(String filePath) {
        // AVRO typically uses "org/apache/avro" as the namespace root for Java code
        String namespaceRoot = "org/apache/avro/";
        int index = filePath.indexOf(namespaceRoot);
        if (index != -1) {
            return filePath.substring(index);
        }
        // If the root is not found, return the original name as fallback
        return filePath;
    }

    // Helper DTO class to store metrics extracted from commit differences.
    private static class CommitMetrics {
        int myLocAdded = 0;
        int myLocDeleted = 0;
        double entropy = 0.0;
    }
}