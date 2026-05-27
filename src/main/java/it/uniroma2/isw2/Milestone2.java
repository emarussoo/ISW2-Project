package it.uniroma2.isw2;

import it.uniroma2.isw2.weka.WekaAnalyzer;

import java.io.File;

public class Milestone2 {

    public static void main(String[] args) {
        String datasetPath = "avro_metrics_dataset.csv";
        
        File f = new File(datasetPath);
        if (!f.exists()) {
            System.err.println("Errore: Il dataset " + datasetPath + " non esiste.");
            System.err.println("Devi prima eseguire la classe Main (Milestone 1) per generare il CSV!");
            return;
        }

        try {
            System.out.println("--- AVVIO MILESTONE 2: DATA MINING CON WEKA ---");
            WekaAnalyzer.runAnalysis(datasetPath);
            System.out.println("\nAnalisi completata con successo!");
        } catch (Exception e) {
            System.err.println("\nErrore durante l'esecuzione dell'analisi Weka:");
            e.printStackTrace();
        }
    }
}
