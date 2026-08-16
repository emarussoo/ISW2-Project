package it.uniroma2.isw2.dataset.labeling;

import it.uniroma2.isw2.model.Release;
import it.uniroma2.isw2.model.Ticket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class VersionLifecycleCalculator {

    // Calculates IV, OV, and FV for all tickets, estimating IV using the Proportion method if missing from Jira.
    public static void assignLifecycleVersions(List<Ticket> tickets, List<Release> releases) {
        // Assigns OV and FV to each ticket based on dates
        for (Ticket t : tickets) {
            t.setOpeningVersion(findRelease(t.getOpeningDate(), releases));
            t.setFixedVersion(findRelease(t.getResolutionDate(), releases));

            // Determines IV from Affected Versions if available
            if (!t.getAffectedVersions().isEmpty()) {
                // Sorts to select the oldest version
                t.getAffectedVersions().sort(Comparator.comparing(Release::getReleaseDate));
                Release oldestAv = t.getAffectedVersions().get(0);

                // Validates that IV is not strictly after OV to prevent anomalies
                if (oldestAv.getReleaseDate().isBefore(t.getOpeningDate()) || oldestAv.getReleaseDate().isEqual(t.getOpeningDate())) {
                    t.setInjectedVersion(oldestAv);
                }
            }
        }

        // Computes Proportion Total
        double pTotal = calculateProportionTotal(tickets, releases);
        System.out.println("Proportion Total (P) calcolato: " + pTotal);

        // Estimates missing IVs using Proportion
        for (Ticket t : tickets) {
            if (t.getInjectedVersion() == null) {
                Release iv = estimateInjectedVersion(t, pTotal, releases);
                t.setInjectedVersion(iv);
            }
        }
    }

    // Finds the first release following the given date, or the last release if none match.
    private static Release findRelease(java.time.LocalDateTime date, List<Release> releases) {
        for (Release r : releases) {
            if (r.getReleaseDate().isAfter(date) || r.getReleaseDate().isEqual(date)) {
                return r;
            }
        }
        return releases.get(releases.size() - 1);
    }

    // Calculates the average Proportion (P) across all tickets with valid versions.
    private static double calculateProportionTotal(List<Ticket> tickets, List<Release> releases) {
        List<Double> proportions = new ArrayList<>();

        for (Ticket t : tickets) {
            if (t.getInjectedVersion() != null && t.getOpeningVersion() != null && t.getFixedVersion() != null) {
                int fvIndex = releases.indexOf(t.getFixedVersion());
                int ovIndex = releases.indexOf(t.getOpeningVersion());
                int ivIndex = releases.indexOf(t.getInjectedVersion());

                // Prevents calculations if versions are inverted or invalid
                if (fvIndex > ivIndex && fvIndex >= ovIndex && ovIndex >= ivIndex) {
                    double p;
                    if (fvIndex == ovIndex) {
                        // Assumes an offset of 1 if FV equals OV to avoid division by zero
                        p = (double) (fvIndex - ivIndex) / 1.0;
                    } else {
                        p = (double) (fvIndex - ivIndex) / (fvIndex - ovIndex);
                    }
                    proportions.add(p);
                }
            }
        }

        // Calculates the average of P
        if (proportions.isEmpty()) return 1.0; // Fallback value

        double sum = 0;
        for (double p : proportions) {
            sum += p;
        }
        return sum / proportions.size();
    }

    // Estimates the Injected Version (IV) using the Proportion formula.
    private static Release estimateInjectedVersion(Ticket t, double pTotal, List<Release> releases) {
        int fvIndex = releases.indexOf(t.getFixedVersion());
        int ovIndex = releases.indexOf(t.getOpeningVersion());

        if (fvIndex == -1 || ovIndex == -1) {
            return t.getOpeningVersion(); // Fallback
        }

        // Proportion formula: IV = FV - (FV - OV) * P
        double fvMinusOv = fvIndex - ovIndex;
        // Assumes a minimum offset of 1 if OV and FV coincide
        if (fvMinusOv == 0) fvMinusOv = 1.0;

        int ivIndex = (int) Math.round(fvIndex - (fvMinusOv * pTotal));

        // Constrains the index between 0 and fvIndex
        if (ivIndex < 0) ivIndex = 0;
        if (ivIndex > fvIndex) ivIndex = fvIndex;

        return releases.get(ivIndex);
    }
}