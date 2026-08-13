package it.uniroma2.isw2.milestones;

import it.uniroma2.isw2.classification.WekaEvaluator;

public class Milestone2 {

    // Executes the second milestone pipeline by running the Weka classification experiment on the generated dataset.
    public static void main(String[] args) {
        System.out.println("Avvio Milestone 2 - Pipeline di Valutazione Weka");
        System.out.println("Target: Dataset AVRO");

        String datasetPath = "results/milestone1/avro_metrics_dataset.csv";
        String outputPath = "results/milestone2/experiment_raw.csv";

        WekaEvaluator evaluator = new WekaEvaluator();
        try {
            evaluator.runExperiment(datasetPath, outputPath);
            System.out.println("Esperimento completato con successo! Risultati scritti in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Si è verificato un errore durante l'esecuzione dell'esperimento.");
            e.printStackTrace();
        }
    }
}