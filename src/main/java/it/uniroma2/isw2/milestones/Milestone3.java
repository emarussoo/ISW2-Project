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

    public static void main(String[] args) {
        // Passo 1: Configurazione iniziale
        // Percorso di default al dataset A ottenuto dalla Milestone 1
        String datasetPath = "results/milestone1/avro_metrics_dataset.csv";
        if (args.length > 0) {
            datasetPath = args[0];
        }

        try {
            System.out.println("--- Milestone 3: What-If Analysis ---");
            System.out.println("Caricamento dataset A da: " + datasetPath);

            // Passo 2: Generazione dei Dataset
            CSVLoader loader = new CSVLoader();
            loader.setSource(new File(datasetPath));
            Instances datasetA = loader.getDataSet();

            // Imposta la colonna "Buggy" come attributo classe (l'ultimo)
            if (datasetA.classIndex() == -1) {
                datasetA.setClassIndex(datasetA.numAttributes() - 1);
            }

            // Trova l'indice dell'attributo NSmells
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

            // Inizializzazione dei dataset C, B+
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

            // Dataset B: deep copy di B+ con forzatura di NSmells a 0 per la simulazione What-If
            Instances datasetB = new Instances(datasetBPlus);
            for (int i = 0; i < datasetB.numInstances(); i++) {
                datasetB.instance(i).setValue(nSmellsIndex, 0.0);
            }

            // Esportazione dei dataset C, B+, B
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

            // Passo 3: Addestramento del modello oracolo (BClassifierA)
            System.out.println("Rimozione delle prime quattro colonne testuali identificative (Project, Release, File, Normalized_File)...");
            
            Remove remove = new Remove();
            remove.setAttributeIndices("1-4");
            remove.setInputFormat(datasetA);

            // Applica il filtro Remove ai quattro dataset per evitare l'overfitting
            Instances cleanA = Filter.useFilter(datasetA, remove);
            Instances cleanC = Filter.useFilter(datasetC, remove);
            Instances cleanBPlus = Filter.useFilter(datasetBPlus, remove);
            Instances cleanB = Filter.useFilter(datasetB, remove);


            //Reimposta class index
            cleanA.setClassIndex(cleanA.numAttributes() - 1);
            cleanBPlus.setClassIndex(cleanBPlus.numAttributes() - 1);
            cleanB.setClassIndex(cleanB.numAttributes() - 1);
            cleanC.setClassIndex(cleanC.numAttributes() - 1);

            System.out.println("Addestramento del modello oracolo (Random Forest + SMOTE) sull'intero Dataset A...");

            final int modelSeed = 42;

            // Individuazione esplicita delle classi.
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

            // Stessa Random Forest selezionata nella Milestone 2.
            RandomForest randomForest = new RandomForest();
            randomForest.setNumIterations(100);
            randomForest.setSeed(modelSeed);

            // Stesso SMOTE dinamico utilizzato nella Milestone 2.
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

            // Addestramento del modello vincente sul Dataset A storico
            bClassifierA.buildClassifier(cleanA);

            // Passo 4: Predizione
            System.out.println("Valutazione dei dataset e conteggio dei bug predetti...");
            int expectedA = countPredictedBugs(cleanA, bClassifierA);
            int expectedC = countPredictedBugs(cleanC, bClassifierA);
            int expectedBPlus = countPredictedBugs(cleanBPlus, bClassifierA);
            int expectedB = countPredictedBugs(cleanB, bClassifierA);

            int actualA = countActualBugs(datasetA);
            int actualC = countActualBugs(datasetC);
            int actualBPlus = countActualBugs(datasetBPlus);

            // Passo 5: Output dei risultati
            System.out.println("\n" + String.format("  %-15s %-15s %-15s %-15s", "Dataset A", "Dataset B+", "Dataset B", "Dataset C"));
            System.out.println("---------------------------------------------------------------------");
            System.out.println(String.format("  %-7s %-7s %-7s %-7s     %-11s %-7s %-7s", "A", "E", "A", "E", "E", "A", "E"));
            System.out.println("---------------------------------------------------------------------");
            System.out.println(String.format(" %-7d %-7d %-7d %-7d    %-11d %-7d %-7d", actualA, expectedA, actualBPlus, expectedBPlus, expectedB, actualC, expectedC));
            System.out.println("---------------------------------------------------------------------");

            // Passo 6: calcolo delle riduzioni What-If.

            // Formulazione principale mostrata nelle slide:
            // difetti reali nelle istanze con smell meno difetti attesi
            // nello scenario in cui NSmells viene impostato a zero.
            int estimatedAvoidedDefects = actualBPlus - expectedB;

            // Effetto diretto della manipolazione sulle predizioni.
            // B+ e B contengono le stesse istanze e differiscono
            // esclusivamente per il valore di NSmells.
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

    /**
     * Conta il numero di istanze effettivamente etichettate come "Yes" in un dataset.
     *
     * @param data Dataset da valutare.
     * @return Numero di bug reali.
     */
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


    /**
     * Valuta le istanze di un dataset con il classificatore passato in input e conta le predizioni "Yes".
     *
     * @param data Dataset da valutare.
     * @param clf  Classificatore oracolo addestrato.
     * @return Numero di istanze etichettate come difettose (buggy = Yes).
     * @throws Exception Se la classificazione fallisce.
     */
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