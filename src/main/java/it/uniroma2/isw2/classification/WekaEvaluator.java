package it.uniroma2.isw2.classification;

import weka.attributeSelection.InfoGainAttributeEval;
import weka.attributeSelection.Ranker;
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
import weka.filters.MultiFilter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.filters.supervised.instance.Resample;
import weka.filters.supervised.instance.SMOTE;
import weka.filters.supervised.instance.SpreadSubsample;
import weka.filters.unsupervised.attribute.Remove;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class WekaEvaluator {

    private static final String[] CLASSIFIERS = {"RandomForest", "NaiveBayes", "IBk"};
    private static final String[] FEATURE_SELECTION = {"None", "InfoGain"};
    private static final String[] BALANCING = {"None", "Undersampling", "Oversampling", "SMOTE"};

    public void runExperiment(String datasetPath, String outputPath) throws Exception {
        // 1. Load Data
        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(datasetPath));
        Instances data = loader.getDataSet();

        // 2. Preprocessing: Remove IDs (Project, Release, File, Normalized_File)
        // Note: This is safe to do on the whole dataset as it does not cause data leakage.
        Remove remove = new Remove();
        remove.setAttributeIndices("1-4");
        remove.setInputFormat(data);
        data = Filter.useFilter(data, remove);

        // Set the target class (Buggy) which is the last attribute now
        data.setClassIndex(data.numAttributes() - 1);

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
            // Write CSV Header
            writer.println("Dataset,Classifier,Balancing,FeatureSelection,Iteration,Fold,Precision,Recall,AUC,Kappa,NPofB20");

            // Cartesian Product
            for (String clfName : CLASSIFIERS) {
                for (String fsName : FEATURE_SELECTION) {
                    for (String balName : BALANCING) {
                        System.out.printf("Running: %s | %s | %s%n", clfName, fsName, balName);
                        execute10x10Fold(data, clfName, fsName, balName, writer, "AVRO");
                    }
                }
            }
        }
    }

    private void execute10x10Fold(Instances data, String clfName, String fsName, String balName, PrintWriter writer, String datasetName) throws Exception {
        // 10 times 10-fold CV
        for (int i = 0; i < 10; i++) {
            // Shuffle data with a different seed for each iteration
            Instances seedData = new Instances(data);
            seedData.randomize(new Random(i + 1)); // i+1 to avoid seed 0 issues if any
            seedData.stratify(10); // Maintain class distribution in folds

            for (int fold = 0; fold < 10; fold++) {
                Instances train = seedData.trainCV(10, fold);
                Instances test = seedData.testCV(10, fold);

                // Configure base classifier
                Classifier baseClassifier = getBaseClassifier(clfName);

                // Setup FilteredClassifier to prevent DATA LEAKAGE!
                // Filters applied here are computed ONLY on the 'train' set.
                FilteredClassifier fc = new FilteredClassifier();
                fc.setClassifier(baseClassifier);

                MultiFilter multiFilter = new MultiFilter();
                List<Filter> filters = new ArrayList<>();

                // Apply Feature Selection
                if (fsName.equals("InfoGain")) {
                    AttributeSelection filter = new AttributeSelection();
                    InfoGainAttributeEval eval = new InfoGainAttributeEval();
                    Ranker search = new Ranker();
                    search.setThreshold(0.0);
                    // Select Top 50% of features (excluding the class attribute)
                    int numToSelect = (train.numAttributes() - 1) / 2;
                    search.setNumToSelect(numToSelect);
                    filter.setEvaluator(eval);
                    filter.setSearch(search);
                    filters.add(filter);
                }

                // Apply Balancing
                switch (balName) {
                    case "Undersampling":
                        SpreadSubsample spreadSubsample = new SpreadSubsample();
                        spreadSubsample.setDistributionSpread(1.0); // 1:1 ratio
                        filters.add(spreadSubsample);
                        break;
                    case "Oversampling":
                        Resample resample = new Resample();
                        resample.setNoReplacement(false);
                        resample.setBiasToUniformClass(1.0); // uniform distribution
                        
                        // we need to set sample size to match majority class
                        // we can approximate this by doubling the dataset or ensuring uniform class
                        // A simple Resample with bias 1.0 achieves uniformity.
                        int majorityCount = Math.max(getInstancesCountByClass(train, 0), getInstancesCountByClass(train, 1));
                        double percentage = (majorityCount * 2.0 / train.numInstances()) * 100.0;
                        resample.setSampleSizePercent(percentage);
                        filters.add(resample);
                        break;
                    case "SMOTE":
                        SMOTE smote = new SMOTE();
                        filters.add(smote);
                        break;
                    default:
                        // None
                        break;
                }

                if (!filters.isEmpty()) {
                    multiFilter.setFilters(filters.toArray(new Filter[0]));
                    fc.setFilter(multiFilter);
                }

                // Build Classifier (Train phase, filters are built internally here!)
                fc.buildClassifier(train);

                // Evaluate Model on Test Set
                Evaluation eval = new Evaluation(train);
                eval.evaluateModel(fc, test);

                // Extract standard metrics (assuming Buggy = 'Yes' is index 1, usually Yes is index 1 or 0 depending on data)
                // Let's identify the index of the defective class ('Yes')
                int buggyClassIndex = train.classAttribute().indexOfValue("Yes");
                if (buggyClassIndex == -1) buggyClassIndex = 1; // Fallback

                double precision = eval.precision(buggyClassIndex);
                double recall = eval.recall(buggyClassIndex);
                double auc = eval.areaUnderROC(buggyClassIndex);
                double kappa = eval.kappa();

                // Calculate Effort-Aware Metric (NPofB20)
                double npofb20 = calculateNPofB20(fc, test, buggyClassIndex);

                // Scrivi Riga nel CSV
                writer.printf(java.util.Locale.US, "%s,%s,%s,%s,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f%n",
                        datasetName, clfName, balName, fsName, (i + 1), (fold + 1),
                        precision, recall, auc, kappa, npofb20);
            }
        }
    }

    private int getInstancesCountByClass(Instances train, int classValue) {
        int count = 0;
        for (Instance inst : train) {
            if (inst.classValue() == classValue) {
                count++;
            }
        }
        return count;
    }

    private Classifier getBaseClassifier(String clfName) {
        switch (clfName) {
            case "RandomForest": return new RandomForest();
            case "NaiveBayes": return new NaiveBayes();
            case "IBk": return new IBk();
            default: throw new IllegalArgumentException("Unknown classifier: " + clfName);
        }
    }

    private double calculateNPofB20(Classifier fc, Instances test, int buggyClassIndex) throws Exception {
        // Effort-Aware evaluation
        int locIndex = test.attribute("Size_LOC").index();

        List<InstanceScore> scores = new ArrayList<>();
        double totalBugs = 0;
        double totalLoc = 0;

        for (Instance instance : test) {
            double loc = instance.value(locIndex);
            totalLoc += loc;

            if (instance.classValue() == buggyClassIndex) {
                totalBugs++;
            }

            double[] distribution = fc.distributionForInstance(instance);
            double probBuggy = distribution[buggyClassIndex];

            // Defect Density
            double defectDensity = loc > 0 ? probBuggy / loc : 0;
            scores.add(new InstanceScore(instance.classValue() == buggyClassIndex, loc, probBuggy, defectDensity));
        }

        if (totalBugs == 0 || totalLoc == 0) {
            return 0.0;
        }

        // Sort descending by Defect Density
        scores.sort(Comparator.comparingDouble(InstanceScore::getDefectDensity).reversed());

        double inspectedLoc = 0;
        double bugsFound = 0;
        double locLimit = totalLoc * 0.20; // 20% of total LOC

        for (InstanceScore score : scores) {
            if (inspectedLoc + score.getLoc() <= locLimit) {
                inspectedLoc += score.getLoc();
                if (score.isBuggy()) {
                    bugsFound++;
                }
            } else {
                // Fractional inclusion of the last instance to precisely hit 20% LOC
                double remainingLoc = locLimit - inspectedLoc;
                if (score.isBuggy()) {
                    bugsFound += (remainingLoc / score.getLoc());
                }
                break;
            }
        }

        return bugsFound / totalBugs;
    }

    // Helper class for sorting instances based on predicted defect density
    private static class InstanceScore {
        private final boolean buggy;
        private final double loc;
        private final double probability;
        private final double defectDensity;

        public InstanceScore(boolean buggy, double loc, double probability, double defectDensity) {
            this.buggy = buggy;
            this.loc = loc;
            this.probability = probability;
            this.defectDensity = defectDensity;
        }

        public boolean isBuggy() { return buggy; }
        public double getLoc() { return loc; }
        public double getProbability() { return probability; }
        public double getDefectDensity() { return defectDensity; }
    }
}