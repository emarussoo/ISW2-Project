package it.uniroma2.isw2.dataset.labeling;

import it.uniroma2.isw2.model.ClassMetricsRow;
import it.uniroma2.isw2.model.Release;
import it.uniroma2.isw2.model.Ticket;

import java.util.List;

public class BugLabeler {

    // Applies the labeling algorithm to the dataset by marking files as 'Buggy' between their injected and fixed versions.
    public static void labelDataset(List<ClassMetricsRow> dataset, List<Ticket> tickets, List<Release> releases) {
        System.out.println("\nInizio etichettatura (Labeling) del dataset...");

        int bugsFound = 0;

        for (Ticket t : tickets) {
            Release iv = t.getInjectedVersion();
            Release fv = t.getFixedVersion();

            if (iv == null || fv == null) {
                continue; // Skips if versions are missing
            }

            int ivIndex = releases.indexOf(iv);
            int fvIndex = releases.indexOf(fv);

            // Ensures indices are valid and in the correct order
            if (ivIndex == -1 || fvIndex == -1 || ivIndex >= fvIndex) {
                continue;
            }

            // For each file modified by this ticket
            for (String affectedFile : t.getAffectedFiles()) {

                // Searches for dataset rows matching this file and the releases between IV and FV
                for (ClassMetricsRow row : dataset) {

                    // Checks if the row belongs to an infected release (IV <= release < FV)
                    int rowIndex = getReleaseIndex(row.getReleaseId(), releases);

                    if (rowIndex >= ivIndex && rowIndex < fvIndex) {

                        // Checks if the file name matches (handles Git paths vs normalized dataset paths)
                        if (affectedFile.endsWith(row.getNormalizedClassName())) {
                            row.setBuggy(true);
                            bugsFound++;
                        }
                    }
                }
            }
        }

        System.out.println("Labeling completato! Trovate " + bugsFound + " istanze 'Buggy' nel dataset.");
    }

    // Retrieves the index of a release given its name or ID.
    private static int getReleaseIndex(String releaseIdOrName, List<Release> releases) {
        for (int i = 0; i < releases.size(); i++) {
            if (releases.get(i).getName().equals(releaseIdOrName)) {
                return i;
            }
        }
        return -1;
    }
}