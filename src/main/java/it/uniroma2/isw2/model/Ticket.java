package it.uniroma2.isw2.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Ticket {
    private String id; // Es. "AVRO-123"

    private LocalDateTime openingDate;
    private LocalDateTime resolutionDate;

    // Versioni per SZZ e Proportion
    private Release injectedVersion; // IV
    private Release openingVersion;  // OV
    private Release fixedVersion;    // FV
    private List<Release> affectedVersions; // AV (quelle riportate da Jira)

    // Lista dei file modificati nel commit che ha risolto questo ticket
    private List<String> affectedFiles;

    public Ticket(String id, LocalDateTime openingDate, LocalDateTime resolutionDate) {
        this.id = id;
        this.openingDate = openingDate;
        this.resolutionDate = resolutionDate;
        this.affectedFiles = new ArrayList<>();
        this.affectedVersions = new ArrayList<>();
    }

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
    
    public Release getInjectedVersion() { return injectedVersion; }
    public void setInjectedVersion(Release injectedVersion) { this.injectedVersion = injectedVersion; }
    
    public Release getOpeningVersion() { return openingVersion; }
    public void setOpeningVersion(Release openingVersion) { this.openingVersion = openingVersion; }
    
    public Release getFixedVersion() { return fixedVersion; }
    public void setFixedVersion(Release fixedVersion) { this.fixedVersion = fixedVersion; }
    
    public List<Release> getAffectedVersions() { return affectedVersions; }
    public void setAffectedVersions(List<Release> affectedVersions) { this.affectedVersions = affectedVersions; }

    public List<String> getAffectedFiles() { return affectedFiles; }
    public void setAffectedFiles(List<String> affectedFiles) { this.affectedFiles = affectedFiles; }
}
