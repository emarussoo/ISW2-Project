package it.uniroma2.isw2.szz;

import it.uniroma2.isw2.model.ClassMetricsRow;
import it.uniroma2.isw2.model.Release;
import it.uniroma2.isw2.model.Ticket;

import java.util.List;

public class BugLabeler {

    /**
     * Applica l'etichettatura SZZ al dataset.
     * Per ogni ticket, i file Java ad esso associati (tramite Git) vengono etichettati
     * come 'Buggy' in tutte le release comprese tra Injected Version (IV) e Fixed Version (FV) - esclusa.
     */
    public static void labelDataset(List<ClassMetricsRow> dataset, List<Ticket> tickets, List<Release> releases) {
        System.out.println("\nInizio etichettatura (Labeling) del dataset con SZZ...");
        
        int bugsFound = 0;
        
        for (Ticket t : tickets) {
            Release iv = t.getInjectedVersion();
            Release fv = t.getFixedVersion();
            
            if (iv == null || fv == null) {
                continue; // Salta se per qualche motivo mancano le versioni
            }
            
            int ivIndex = releases.indexOf(iv);
            int fvIndex = releases.indexOf(fv);
            
            // Assicuriamoci che gli indici siano validi e in ordine corretto
            if (ivIndex == -1 || fvIndex == -1 || ivIndex >= fvIndex) {
                continue;
            }
            
            // Per ogni file modificato da questo ticket
            for (String affectedFile : t.getAffectedFiles()) {
                
                // Cerca le righe nel dataset corrispondenti a questo file e alle release tra IV e FV
                for (ClassMetricsRow row : dataset) {
                    
                    // Controlla se la riga appartiene a una release infetta (IV <= release < FV)
                    int rowIndex = getReleaseIndex(row.getReleaseId(), releases);
                    
                    if (rowIndex >= ivIndex && rowIndex < fvIndex) {
                        
                        // Controlla se il nome del file corrisponde
                        // (Git usa percorsi come src/main/java/... e il nostro dataset normalizza un po'
                        // quindi usiamo contains o equals)
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
    
    private static int getReleaseIndex(String releaseIdOrName, List<Release> releases) {
        for (int i = 0; i < releases.size(); i++) {
            if (releases.get(i).getName().equals(releaseIdOrName)) {
                return i;
            }
        }
        return -1;
    }
}