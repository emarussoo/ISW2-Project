package it.uniroma2.isw2.weka;

import weka.attributeSelection.CfsSubsetEval;
import weka.attributeSelection.GreedyStepwise;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.trees.RandomForest;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.filters.supervised.instance.SMOTE;
import weka.filters.unsupervised.attribute.Remove;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class WekaAnalyzer {

    // Classe di supporto per calcolare NPofB20
    private static class PredictionResult implements Comparable<PredictionResult> {
        double probYes;
        boolean actualBuggy;
        double loc;

        public PredictionResult(double probYes, boolean actualBuggy, double loc) {
            this.probYes = probYes;
            this.actualBuggy = actualBuggy;
            this.loc = loc;
        }

        @Override
        public int compareTo(PredictionResult o) {
            // Ordine decrescente per probabilità
            return Double.compare(o.probYes, this.probYes);
        }
    }

    public static void runAnalysis(String csvPath) throws Exception {
        System.out.println("\n--- FASE 5: Machine Learning (Weka) ---");
        System.out.println("Caricamento dataset: " + csvPath);

        // 1. Caricamento Dataset
        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(csvPath));
        Instances dataset = loader.getDataSet();

        // Conserviamo l'indice originale di Size_LOC prima di rimuovere le prime 4 colonne
        // Size_LOC è la colonna 5 (indice 4 in Weka 0-based)
        int locIndexOriginal = 4; 
        
        // 2. Pre-processing: Rimuoviamo colonne descrittive (Project, Release, File, Normalized_File)
        // Indici: 1, 2, 3, 4 (1-based in Weka)
        Remove remove = new Remove();
        remove.setAttributeIndices("1,2,3,4");
        remove.setInputFormat(dataset);
        Instances filteredData = Filter.useFilter(dataset, remove);

        // L'indice di Size_LOC nel filteredData diventa 0 (essendo prima in 4)
        int locIndex = 0;

        // Impostiamo l'attributo Class (Target: Buggy) che ora è l'ultimo
        filteredData.setClassIndex(filteredData.numAttributes() - 1);

        // Controlliamo quale indice corrisponde a "Yes"
        int bugClassIndex = filteredData.classAttribute().indexOfValue("Yes");
        if (bugClassIndex == -1) bugClassIndex = filteredData.classAttribute().indexOfValue("true");
        if (bugClassIndex == -1) bugClassIndex = 1;

        // I classificatori da comparare
        Classifier[] classifiers = {
                new RandomForest(),
                new NaiveBayes(),
                new IBk()
        };
        String[] classifierNames = {"RandomForest", "NaiveBayes", "IBk"};

        System.out.println("Inizio 10 times 10-Folds Cross Validation...");
        System.out.println(String.format("%-15s | %-25s | %-10s | %-10s | %-10s | %-10s | %-10s", 
                "Classifier", "Configuration", "Precision", "Recall", "AUC", "Kappa", "NPofB20"));
        System.out.println("-".repeat(105));

        for (int c = 0; c < classifiers.length; c++) {
            Classifier baseClassifier = classifiers[c];
            String name = classifierNames[c];

            // A. Baseline
            evaluateModel(baseClassifier, filteredData, name, "Baseline", bugClassIndex, locIndex);

            // B. Feature Selection
            FilteredClassifier fcFs = new FilteredClassifier();
            fcFs.setClassifier(baseClassifier);
            fcFs.setFilter(getFeatureSelectionFilter(filteredData));
            evaluateModel(fcFs, filteredData, name, "Feature Selection", bugClassIndex, locIndex);

            // C. Balancing (SMOTE)
            FilteredClassifier fcSmote = new FilteredClassifier();
            fcSmote.setClassifier(baseClassifier);
            fcSmote.setFilter(getSmoteFilter(filteredData));
            evaluateModel(fcSmote, filteredData, name, "SMOTE", bugClassIndex, locIndex);

            // D. Feature Selection + SMOTE
            FilteredClassifier fcBoth = new FilteredClassifier();
            fcBoth.setClassifier(baseClassifier);
            weka.filters.MultiFilter multiFilter = new weka.filters.MultiFilter();
            Filter[] filters = {getFeatureSelectionFilter(filteredData), getSmoteFilter(filteredData)};
            multiFilter.setFilters(filters);
            fcBoth.setFilter(multiFilter);
            evaluateModel(fcBoth, filteredData, name, "FS + SMOTE", bugClassIndex, locIndex);
        }
    }

    private static void evaluateModel(Classifier classifier, Instances data, String classifierName, String configName, int bugClassIndex, int locIndex) throws Exception {
        int runs = 10;
        int folds = 10;
        
        double avgPrecision = 0, avgRecall = 0, avgAuc = 0, avgKappa = 0, avgNpofb20 = 0;

        for (int i = 0; i < runs; i++) {
            // Rimescoliamo i dati per questa run
            Instances randomizedData = new Instances(data);
            randomizedData.randomize(new Random(i));
            
            // Dobbiamo anche stratificare
            randomizedData.stratify(folds);

            Evaluation eval = new Evaluation(randomizedData);
            List<PredictionResult> foldPredictions = new ArrayList<>();

            for (int n = 0; n < folds; n++) {
                Instances train = randomizedData.trainCV(folds, n);
                Instances test = randomizedData.testCV(folds, n);

                // Addestriamo il classificatore (con i suoi filtri interni) sul train
                Classifier copiedClassifier = weka.classifiers.AbstractClassifier.makeCopy(classifier);
                copiedClassifier.buildClassifier(train);
                
                // Valutiamo sul test e raccogliamo le previsioni
                eval.evaluateModel(copiedClassifier, test);

                for (int j = 0; j < test.numInstances(); j++) {
                    Instance inst = test.instance(j);
                    double[] distribution = copiedClassifier.distributionForInstance(inst);
                    double probYes = distribution[bugClassIndex];
                    boolean actualBuggy = (inst.classValue() == bugClassIndex);
                    double loc = inst.value(locIndex);
                    
                    foldPredictions.add(new PredictionResult(probYes, actualBuggy, loc));
                }
            }

            // Aggiungiamo le metriche della run (su tutti i 10 fold uniti)
            avgPrecision += eval.precision(bugClassIndex);
            avgRecall += eval.recall(bugClassIndex);
            avgAuc += eval.areaUnderROC(bugClassIndex);
            avgKappa += eval.kappa();
            
            // Calcolo NPofB20 per questa run
            avgNpofb20 += calculateNpofB20(foldPredictions);
        }

        avgPrecision /= runs;
        avgRecall /= runs;
        avgAuc /= runs;
        avgKappa /= runs;
        avgNpofb20 /= runs;

        System.out.println(String.format("%-15s | %-25s | %.4f     | %.4f     | %.4f     | %.4f     | %.4f", 
                classifierName, configName, avgPrecision, avgRecall, avgAuc, avgKappa, avgNpofb20));
    }

    private static double calculateNpofB20(List<PredictionResult> predictions) {
        // Ordiniamo le classi dalla più probabile di essere buggata alla meno probabile
        Collections.sort(predictions);
        
        double totalLoc = 0;
        for (PredictionResult p : predictions) {
            totalLoc += p.loc;
        }
        
        double limitLoc = totalLoc * 0.20; // Vogliamo ispezionare solo il 20% del codice totale
        double inspectedLoc = 0;
        int bugsFound = 0;
        
        for (PredictionResult p : predictions) {
            if (inspectedLoc + p.loc <= limitLoc) {
                inspectedLoc += p.loc;
                if (p.actualBuggy) {
                    bugsFound++;
                }
            } else {
                // Abbiamo raggiunto il limite del 20% di LOC ispezionate
                break;
            }
        }
        return bugsFound;
    }

    private static Filter getFeatureSelectionFilter(Instances data) throws Exception {
        AttributeSelection filter = new AttributeSelection();
        CfsSubsetEval eval = new CfsSubsetEval();
        GreedyStepwise search = new GreedyStepwise();
        search.setSearchBackwards(true);
        filter.setEvaluator(eval);
        filter.setSearch(search);
        filter.setInputFormat(data);
        return filter;
    }

    private static Filter getSmoteFilter(Instances data) throws Exception {
        SMOTE smote = new SMOTE();
        smote.setInputFormat(data);
        return smote;
    }
}