package it.uniroma2.isw2.milestones;

import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.trees.RandomForest;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.filters.Filter;
import weka.filters.supervised.instance.SMOTE;
import weka.filters.unsupervised.attribute.Remove;

import java.io.File;
import java.io.IOException;

public class Milestone3 {

    // Executes the third milestone pipeline: performs a What-If analysis by manipulating the 'NSmells' attribute and evaluating the impact.
    public static void main(String[] args) {
        // Step 1: Initial configuration
        // Default path to dataset A obtained from Milestone 1
        String datasetPath = "results/milestone1/avro_metrics_dataset.csv";
        if (args.length > 0) {
            datasetPath = args[0];
        }

        try {
            System.out.println("--- Milestone 3: What-If Analysis ---");
            System.out.println("Caricamento dataset A da: " + datasetPath);

            // Step 2: Dataset Generation
            CSVLoader loader = new CSVLoader();
            loader.setSource(new File(datasetPath));
            Instances datasetA = loader.getDataSet();

            // Sets the "Buggy" column as the class attribute (the last one)
            if (datasetA.classIndex() == -1) {
                datasetA.setClassIndex(datasetA.numAttributes() - 1);
            }

            // Finds the index of the NSmells attribute
            int nSmellsIndex = -1;
            for (int i = 0; i < datasetA.numAttributes(); i++) {
                if (datasetA.attribute(i).name().equals("NSmells")) {
                    nSmellsIndex = i;
                    break;
                }
            }

            if (nSmellsIndex == -1) {
                throw new IllegalStateException("Attributo 'NSmells' non trovato nel dataset!");
            }

            // Initializes datasets C and B+
            Instances datasetC = new Instances(datasetA, 0);
            Instances datasetBPlus = new Instances(datasetA, 0);

            for (int i = 0; i < datasetA.numInstances(); i++) {
                Instance instance = datasetA.instance(i);

                if (instance.isMissing(nSmellsIndex)) {
                    throw new IllegalStateException(
                            "Valore NSmells mancante alla riga " + (i + 1)
                    );
                }

                double numberOfSmells = instance.value(nSmellsIndex);

                if (numberOfSmells < 0.0) {
                    throw new IllegalStateException(
                            "Valore NSmells negativo alla riga " + (i + 1)
                    );
                }

                if (numberOfSmells == 0.0) {
                    datasetC.add(instance);
                } else {
                    datasetBPlus.add(instance);
                }
            }

            // Dataset B: deep copy of B+ with NSmells forced to 0 for the What-If simulation
            Instances datasetB = new Instances(datasetBPlus);
            for (int i = 0; i < datasetB.numInstances(); i++) {
                datasetB.instance(i).setValue(nSmellsIndex, 0.0);
            }

            // Exports datasets C, B+, and B
            System.out.println("Esportazione dei dataset C, B+ e B nella cartella 'results/milestone3'...");
            File outDir = new File("results/milestone3");
            if (!outDir.exists() && !outDir.mkdirs()) {
                throw new IOException(
                        "Impossibile creare la directory: "
                                + outDir.getAbsolutePath()
                );
            }

            weka.core.converters.CSVSaver saver = new weka.core.converters.CSVSaver();

            saver.setInstances(datasetC);
            saver.setFile(new File(outDir, "dataset_C.csv"));
            saver.writeBatch();

            saver.setInstances(datasetBPlus);
            saver.setFile(new File(outDir, "dataset_B_plus.csv"));
            saver.writeBatch();

            saver.setInstances(datasetB);
            saver.setFile(new File(outDir, "dataset_B.csv"));
            saver.writeBatch();

            // Step 3: Training the oracle model (BClassifierA)
            System.out.println("Rimozione delle prime quattro colonne testuali identificative (Project, Release, File, Normalized_File)...");

            Remove remove = new Remove();
            remove.setAttributeIndices("1-4");
            remove.setInputFormat(datasetA);

            // Applies the Remove filter to the four datasets to prevent overfitting
            Instances cleanA = Filter.useFilter(datasetA, remove);
            Instances cleanC = Filter.useFilter(datasetC, remove);
            Instances cleanBPlus = Filter.useFilter(datasetBPlus, remove);
            Instances cleanB = Filter.useFilter(datasetB, remove);

            // Resets the class index
            cleanA.setClassIndex(cleanA.numAttributes() - 1);
            cleanBPlus.setClassIndex(cleanBPlus.numAttributes() - 1);
            cleanB.setClassIndex(cleanB.numAttributes() - 1);
            cleanC.setClassIndex(cleanC.numAttributes() - 1);

            System.out.println("Addestramento del modello oracolo (Random Forest + SMOTE) sull'intero Dataset A...");

            final int modelSeed = 42;

            // Explicitly identifies the classes
            int buggyClassIndex = cleanA.classAttribute().indexOfValue("Yes");
            int cleanClassIndex = cleanA.classAttribute().indexOfValue("No");

            if (buggyClassIndex < 0 || cleanClassIndex < 0) {
                throw new IllegalStateException(
                        "La classe Buggy deve contenere i valori 'Yes' e 'No'."
                );
            }

            int buggyCount = countInstancesByClass(cleanA, buggyClassIndex);
            int cleanCount = countInstancesByClass(cleanA, cleanClassIndex);

            int minorityCount = Math.min(buggyCount, cleanCount);
            int majorityCount = Math.max(buggyCount, cleanCount);

            if (minorityCount < 2) {
                throw new IllegalStateException(
                        "SMOTE richiede almeno due istanze della classe minoritaria."
                );
            }

            // Same Random Forest selected in Milestone 2
            RandomForest randomForest = new RandomForest();
            randomForest.setNumIterations(100);
            randomForest.setSeed(modelSeed);

            // Same dynamic SMOTE used in Milestone 2
            SMOTE smote = new SMOTE();
            smote.setRandomSeed(modelSeed);
            smote.setNearestNeighbors(Math.min(5, minorityCount - 1));

            double smotePercentage =
                    ((majorityCount - minorityCount) * 100.0)
                            / minorityCount;

            smote.setPercentage(smotePercentage);

            FilteredClassifier bClassifierA = new FilteredClassifier();
            bClassifierA.setClassifier(randomForest);
            bClassifierA.setFilter(smote);

            // Trains the winning model on the historical Dataset A
            bClassifierA.buildClassifier(cleanA);

            // Step 4: Prediction
            System.out.println("Valutazione dei dataset e conteggio dei bug predetti...");
            int expectedA = countPredictedBugs(cleanA, bClassifierA);
            int expectedC = countPredictedBugs(cleanC, bClassifierA);
            int expectedBPlus = countPredictedBugs(cleanBPlus, bClassifierA);
            int expectedB = countPredictedBugs(cleanB, bClassifierA);

            int actualA = countActualBugs(datasetA);
            int actualC = countActualBugs(datasetC);
            int actualBPlus = countActualBugs(datasetBPlus);

            // Step 5: Results Output
            System.out.println("\n" + String.format("  %-15s %-15s %-15s %-15s", "Dataset A", "Dataset B+", "Dataset B", "Dataset C"));
            System.out.println("---------------------------------------------------------------------");
            System.out.println(String.format("  %-7s %-7s %-7s %-7s     %-11s %-7s %-7s", "A", "E", "A", "E", "E", "A", "E"));
            System.out.println("---------------------------------------------------------------------");
            System.out.println(String.format(" %-7d %-7d %-7d %-7d    %-11d %-7d %-7d", actualA, expectedA, actualBPlus, expectedBPlus, expectedB, actualC, expectedC));
            System.out.println("---------------------------------------------------------------------");

            // Step 6: What-If reduction calculations

            // Main formulation: actual defects in instances with smells minus expected defects in the scenario where NSmells is set to zero
            int estimatedAvoidedDefects = actualBPlus - expectedB;

            // Direct effect of the manipulation on predictions (B+ and B differ exclusively by the NSmells value)
            int predictedDrop = expectedBPlus - expectedB;

            double dropAmongSmellyClasses =
                    actualBPlus > 0
                            ? estimatedAvoidedDefects * 100.0 / actualBPlus
                            : 0.0;

            double overallReduction =
                    actualA > 0
                            ? estimatedAvoidedDefects * 100.0 / actualA
                            : 0.0;

            double directPredictionReduction =
                    expectedBPlus > 0
                            ? predictedDrop * 100.0 / expectedBPlus
                            : 0.0;

            System.out.println("\nAnalisi What-If conclusa.");

            System.out.printf(
                    java.util.Locale.US,
                    "Difetti potenzialmente evitabili, A(B+) - E(B): %d%n",
                    estimatedAvoidedDefects
            );

            System.out.printf(
                    java.util.Locale.US,
                    "Riduzione rispetto ai difetti reali nelle istanze "
                            + "con smell: %.2f%%%n",
                    dropAmongSmellyClasses
            );

            System.out.printf(
                    java.util.Locale.US,
                    "Riduzione rispetto a tutti i difetti reali "
                            + "del dataset A: %.2f%%%n",
                    overallReduction
            );

            System.out.printf(
                    java.util.Locale.US,
                    "Variazione diretta delle predizioni, "
                            + "E(B+) - E(B): %d, pari al %.2f%%%n",
                    predictedDrop,
                    directPredictionReduction
            );

        } catch (IOException e) {
            System.err.println("Errore durante il caricamento del dataset: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Errore durante l'elaborazione o classificazione: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Counts the number of instances actually labeled as "Yes" (buggy) in the dataset.
    private static int countActualBugs(Instances data) {
        int count = 0;
        int yesIndex = data.classAttribute().indexOfValue("Yes");

        if (yesIndex == -1) {
            throw new IllegalStateException("Valore classe 'Yes' non trovato nell'attributo classe 'Buggy'.");
        }

        for (int i = 0; i < data.numInstances(); i++) {
            if ((int) data.instance(i).classValue() == yesIndex) {
                count++;
            }
        }
        return count;
    }

    // Counts and returns the number of instances matching the specified class index.
    private static int countInstancesByClass(
            Instances data,
            int classIndex) {

        int count = 0;

        for (Instance instance : data) {
            if ((int) instance.classValue() == classIndex) {
                count++;
            }
        }

        return count;
    }

    // Evaluates the dataset using the provided classifier and counts the number of predicted bugs ("Yes").
    private static int countPredictedBugs(Instances data, FilteredClassifier clf) throws Exception {
        int count = 0;
        int yesIndex = data.classAttribute().indexOfValue("Yes");

        if (yesIndex == -1) {
            throw new IllegalStateException("Valore classe 'Yes' non trovato nell'attributo classe 'Buggy'. Assicurati che il dataset abbia i valori corretti.");
        }

        for (int i = 0; i < data.numInstances(); i++) {
            double pred = clf.classifyInstance(data.instance(i));
            if ((int) pred == yesIndex) {
                count++;
            }
        }
        return count;
    }
}