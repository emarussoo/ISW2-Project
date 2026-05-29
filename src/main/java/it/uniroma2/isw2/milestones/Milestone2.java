package it.uniroma2.isw2.milestones;

import it.uniroma2.isw2.classification.WekaEvaluator;

public class Milestone2 {
    public static void main(String[] args) {
        System.out.println("Starting Milestone 2 - Weka Evaluation Pipeline");
        System.out.println("Target: AVRO Dataset");

        String datasetPath = "results/milestone1/avro_metrics_dataset.csv"; // Make sure this path is correct
        String outputPath = "results/milestone2/experiment_raw.csv";

        WekaEvaluator evaluator = new WekaEvaluator();
        try {
            evaluator.runExperiment(datasetPath, outputPath);
            System.out.println("Experiment completed successfully! Results written to: " + outputPath);
        } catch (Exception e) {
            System.err.println("An error occurred during the experiment execution.");
            e.printStackTrace();
        }
    }
}