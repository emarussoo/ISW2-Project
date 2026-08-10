package it.uniroma2.isw2.dataset.szz;

import it.uniroma2.isw2.model.Release;
import it.uniroma2.isw2.model.Ticket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class VersionLifecycleCalculator {

    /**
     * Calcola IV, OV e FV per tutti i ticket. Se IV non è determinabile da Jira (tramite Affected Versions),
     * usa il Proportion Total per stimarla.
     */
    public static void assignLifecycleVersions(List<Ticket> tickets, List<Release> releases) {
        // 1. Assegna OV e FV a ciascun ticket in base alle date
        for (Ticket t : tickets) {
            t.setOpeningVersion(findRelease(t.getOpeningDate(), releases));
            t.setFixedVersion(findRelease(t.getResolutionDate(), releases));
            
            // Determina IV dalle Affected Versions (se presenti)
            if (!t.getAffectedVersions().isEmpty()) {
                // Ordina in modo da prendere la più vecchia
                t.getAffectedVersions().sort(Comparator.comparing(Release::getReleaseDate));
                Release oldestAv = t.getAffectedVersions().get(0);
                
                // Un check di validità: IV non dovrebbe essere successiva a OV (a volte accade per errori in Jira)
                // Se è successiva, la scartiamo per non sfalsare SZZ.
                if (oldestAv.getReleaseDate().isBefore(t.getOpeningDate()) || oldestAv.getReleaseDate().isEqual(t.getOpeningDate())) {
                    t.setInjectedVersion(oldestAv);
                }
            }
        }
        
        // 2. Calcola Proportion Total
        double pTotal = calculateProportionTotal(tickets, releases);
        System.out.println("Proportion Total (P) calcolato: " + pTotal);
        
        // 3. Stima le IV mancanti tramite Proportion
        for (Ticket t : tickets) {
            if (t.getInjectedVersion() == null) {
                Release iv = estimateInjectedVersion(t, pTotal, releases);
                t.setInjectedVersion(iv);
            }
        }
    }
    
    private static Release findRelease(java.time.LocalDateTime date, List<Release> releases) {
        // Trova la prima release successiva alla data fornita.
        // Se non ce n'è nessuna, potremmo restituire l'ultima release.
        for (Release r : releases) {
            if (r.getReleaseDate().isAfter(date) || r.getReleaseDate().isEqual(date)) {
                return r;
            }
        }
        return releases.get(releases.size() - 1);
    }
    
    private static double calculateProportionTotal(List<Ticket> tickets, List<Release> releases) {
        List<Double> proportions = new ArrayList<>();
        
        for (Ticket t : tickets) {
            if (t.getInjectedVersion() != null && t.getOpeningVersion() != null && t.getFixedVersion() != null) {
                int fvIndex = releases.indexOf(t.getFixedVersion());
                int ovIndex = releases.indexOf(t.getOpeningVersion());
                int ivIndex = releases.indexOf(t.getInjectedVersion());
                
                // Previene calcoli se le versioni sono invertite (errore di Jira o FV < IV)
                if (fvIndex > ivIndex && fvIndex >= ovIndex && ovIndex >= ivIndex) {
                    double p;
                    if (fvIndex == ovIndex) {
                        // Se FV == OV, consideriamo P come se fv-ov fosse = 1 per evitare divisioni per zero
                        p = (double) (fvIndex - ivIndex) / 1.0; 
                    } else {
                        p = (double) (fvIndex - ivIndex) / (fvIndex - ovIndex);
                    }
                    proportions.add(p);
                }
            }
        }
        
        // Calcola la media di P
        if (proportions.isEmpty()) return 1.0; // Valore di fallback (es. fallback a OV)
        
        double sum = 0;
        for (double p : proportions) {
            sum += p;
        }
        return sum / proportions.size();
    }
    
    private static Release estimateInjectedVersion(Ticket t, double pTotal, List<Release> releases) {
        int fvIndex = releases.indexOf(t.getFixedVersion());
        int ovIndex = releases.indexOf(t.getOpeningVersion());
        
        if (fvIndex == -1 || ovIndex == -1) {
             return t.getOpeningVersion(); // Fallback
        }
        
        // Formula per Proportion: IV = FV - (FV - OV) * P
        double fvMinusOv = fvIndex - ovIndex;
        // Se OV e FV coincidono nello stesso index, fvMinusOv = 0. Assumiamo 1 come offset minimo di SZZ.
        if (fvMinusOv == 0) fvMinusOv = 1.0;
        
        int ivIndex = (int) Math.round(fvIndex - (fvMinusOv * pTotal));
        
        // Limita l'indice tra 0 e fvIndex (perché IV deve esistere prima di FV)
        if (ivIndex < 0) ivIndex = 0;
        if (ivIndex > fvIndex) ivIndex = fvIndex;
        
        return releases.get(ivIndex);
    }
}