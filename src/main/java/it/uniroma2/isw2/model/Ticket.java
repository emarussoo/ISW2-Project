package it.uniroma2.isw2.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Ticket {
    private String id; // Es. "AVRO-123"

    // Le tre versioni/date fondamentali per SZZ e Proportion
    private LocalDateTime openingDate;    // OV (Opening Version)
    private LocalDateTime resolutionDate; // FV (Fix Version)
    private LocalDateTime injectedDate;   // IV (Injected Version) - Verrà calcolata

    // Lista dei file modificati nel commit che ha risolto questo ticket
    private List<String> affectedFiles;

    public Ticket(String id, LocalDateTime openingDate, LocalDateTime resolutionDate) {
        this.id = id;
        this.openingDate = openingDate;
        this.resolutionDate = resolutionDate;
        this.affectedFiles = new ArrayList<>();
    }

    // Aggiungi questo metodo di comodo per la lista dei file
    public void addAffectedFile(String fileName) {
        this.affectedFiles.add(fileName);
    }

    // --- GETTER E SETTER ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public LocalDateTime getOpeningDate() { return openingDate; }
    public void setOpeningDate(LocalDateTime openingDate) { this.openingDate = openingDate; }
    public LocalDateTime getResolutionDate() { return resolutionDate; }
    public void setResolutionDate(LocalDateTime resolutionDate) { this.resolutionDate = resolutionDate; }
    public LocalDateTime getInjectedDate() { return injectedDate; }
    public void setInjectedDate(LocalDateTime injectedDate) { this.injectedDate = injectedDate; }
    public List<String> getAffectedFiles() { return affectedFiles; }
    public void setAffectedFiles(List<String> affectedFiles) { this.affectedFiles = affectedFiles; }
}
