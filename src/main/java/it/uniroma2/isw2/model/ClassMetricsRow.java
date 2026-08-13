package it.uniroma2.isw2.model;

public class ClassMetricsRow {
    // Identifiers
    private String projectName;
    private String releaseId;
    private String className; // Contiene il path originale intero
    private String normalizedClassName; // FQN o namespace pulito (es. org/apache/avro/...)

    // Metrics
    private int sizeLoc;
    private int numberOfRevisions;
    private int numberOfAuthors;
    private int ageInDays;
    private int nFix;

    private int locAdded;
    private int maxLocAdded;
    private int averageLocAdded;

    private int churn;
    private int maxChurn;
    private int averageChurn;

    private int changeSetSize;
    private int maxChangeSet;
    private int averageChangeSet;                // Quante volte è stato buggato in passato

    private int locDeleted;
    private int maxLocDeleted;
    private int averageLocDeleted;

    private long weightedAge;
    private double averageNd;
    private double averageEntropy;

    private int nSmells;

    // Target Label
    private boolean buggy;

    public ClassMetricsRow(String projectName, String releaseId, String className, String normalizedClassName) {
        this.projectName = projectName;
        this.releaseId = releaseId;
        this.className = className;
        this.normalizedClassName = normalizedClassName;
        this.nSmells = 0; // Inizializziamo a zero
        this.buggy = false; // Di default assumiamo che la classe sia sana
    }

    // getters and setters

    public String getProjectName() {
        return projectName;
    }

    public String getReleaseId() {
        return releaseId;
    }

    public String getClassName() {
        return className;
    }

    public String getNormalizedClassName() {
        return normalizedClassName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public void setReleaseId(String releaseId) {
        this.releaseId = releaseId;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public void setNormalizedClassName(String normalizedClassName) {
        this.normalizedClassName = normalizedClassName;
    }

    public int getSizeLoc() {
        return sizeLoc;
    }

    public int getNumberOfRevisions() {
        return numberOfRevisions;
    }

    public int getNumberOfAuthors() {
        return numberOfAuthors;
    }

    public int getAgeInDays() {
        return ageInDays;
    }

    public int getnFix() {
        return nFix;
    }

    public int getLocAdded() {
        return locAdded;
    }

    public int getMaxLocAdded() {
        return maxLocAdded;
    }

    public int getAverageLocAdded() {
        return averageLocAdded;
    }

    public int getChurn() {
        return churn;
    }

    public int getMaxChurn() {
        return maxChurn;
    }

    public int getAverageChurn() {
        return averageChurn;
    }

    public int getChangeSetSize() {
        return changeSetSize;
    }

    public int getMaxChangeSet() {
        return maxChangeSet;
    }

    public int getAverageChangeSet() {
        return averageChangeSet;
    }

    public int getLocDeleted() {
        return locDeleted;
    }

    public int getMaxLocDeleted() {
        return maxLocDeleted;
    }

    public int getAverageLocDeleted() {
        return averageLocDeleted;
    }

    public long getWeightedAge() {
        return weightedAge;
    }

    public double getAverageNd() {
        return averageNd;
    }

    public double getAverageEntropy() {
        return averageEntropy;
    }

    public boolean isBuggy() {
        return buggy;
    }

    public void setSizeLoc(int sizeLoc) {
        this.sizeLoc = sizeLoc;
    }

    public void setNumberOfRevisions(int numberOfRevisions) {
        this.numberOfRevisions = numberOfRevisions;
    }

    public void setNumberOfAuthors(int numberOfAuthors) {
        this.numberOfAuthors = numberOfAuthors;
    }

    public void setAgeInDays(int ageInDays) {
        this.ageInDays = ageInDays;
    }

    public void setnFix(int nFix) {
        this.nFix = nFix;
    }

    public void setLocAdded(int locAdded) {
        this.locAdded = locAdded;
    }

    public void setMaxLocAdded(int maxLocAdded) {
        this.maxLocAdded = maxLocAdded;
    }

    public void setAverageLocAdded(int averageLocAdded) {
        this.averageLocAdded = averageLocAdded;
    }

    public void setChurn(int churn) {
        this.churn = churn;
    }

    public void setMaxChurn(int maxChurn) {
        this.maxChurn = maxChurn;
    }

    public void setAverageChurn(int averageChurn) {
        this.averageChurn = averageChurn;
    }

    public void setChangeSetSize(int changeSetSize) {
        this.changeSetSize = changeSetSize;
    }

    public void setMaxChangeSet(int maxChangeSet) {
        this.maxChangeSet = maxChangeSet;
    }

    public void setAverageChangeSet(int averageChangeSet) {
        this.averageChangeSet = averageChangeSet;
    }

    public void setLocDeleted(int locDeleted) {
        this.locDeleted = locDeleted;
    }

    public void setMaxLocDeleted(int maxLocDeleted) {
        this.maxLocDeleted = maxLocDeleted;
    }

    public void setAverageLocDeleted(int averageLocDeleted) {
        this.averageLocDeleted = averageLocDeleted;
    }

    public void setWeightedAge(long weightedAge) {
        this.weightedAge = weightedAge;
    }

    public void setAverageNd(double averageNd) {
        this.averageNd = averageNd;
    }

    public void setAverageEntropy(double averageEntropy) {
        this.averageEntropy = averageEntropy;
    }

    public void setBuggy(boolean buggy) {
        this.buggy = buggy;
    }

    public int getnSmells() {
        return nSmells;
    }

    public void setnSmells(int nSmells) {
        this.nSmells = nSmells;
    }
}
