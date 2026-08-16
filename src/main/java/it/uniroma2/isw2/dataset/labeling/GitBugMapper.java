package it.uniroma2.isw2.dataset.labeling;

import it.uniroma2.isw2.model.Ticket;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.File;
import java.util.List;

public class GitBugMapper {

    // Maps each ticket to the list of Java files modified in the Git repository to resolve it.
    public static void mapBugsToFiles(List<Ticket> tickets, String repoPath) throws Exception {
        System.out.println("\nMapping dei Bug di Jira sui commit di Git...");

        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        Repository repository = builder.setGitDir(new File(repoPath))
                .readEnvironment()
                .findGitDir()
                .setMustExist(true)
                .build();

        try (Git git = new Git(repository);
             DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {

            df.setRepository(repository);
            Iterable<RevCommit> logs = git.log().all().call();

            // Iterates through all commits
            for (RevCommit commit : logs) {
                String msg = commit.getFullMessage();

                // Checks which ticket corresponds to the commit
                for (Ticket t : tickets) {
                    // Checks if the ticket ID is in the message
                    if (msg.contains(t.getId() + " ") || msg.contains(t.getId() + "\n")
                            || msg.contains(t.getId() + ":") || msg.contains(t.getId() + "]")
                            || msg.endsWith(t.getId())) {

                        // Extracts the modified files
                        List<DiffEntry> diffs = getCommitDiffs(repository, df, commit);
                        for (DiffEntry diff : diffs) {
                            String newPath = diff.getNewPath();
                            // Saves only Java source files, ignoring tests
                            if (newPath.endsWith(".java") && !newPath.contains("/test/")) {
                                if (!t.getAffectedFiles().contains(newPath)) {
                                    t.getAffectedFiles().add(newPath);
                                }
                            }
                        }
                    }
                }
            }
        }
        System.out.println("Mapping completato!");
    }

    // Calculates the file differences introduced by a commit compared to its parent.
    private static List<DiffEntry> getCommitDiffs(Repository repository, DiffFormatter df, RevCommit commit) throws Exception {
        if (commit.getParentCount() > 0) {
            RevCommit parent = commit.getParent(0);
            return df.scan(parent.getTree(), commit.getTree());
        } else {
            AbstractTreeIterator oldTreeIter = new EmptyTreeIterator();
            org.eclipse.jgit.lib.ObjectReader reader = repository.newObjectReader();
            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            newTreeIter.reset(reader, commit.getTree());
            return df.scan(oldTreeIter, newTreeIter);
        }
    }
}