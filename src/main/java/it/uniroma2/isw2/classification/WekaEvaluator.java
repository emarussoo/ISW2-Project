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
import weka.filters.unsupervised.attribute.Standardize;

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

    private static final int NUMBER_OF_REPETITIONS = 10;
    private static final int NUMBER_OF_FOLDS = 10;
    private static final int RANDOM_FOREST_TREES = 100;
    private static final int IBK_NEIGHBORS = 3;
    private static final int DEFAULT_SMOTE_NEIGHBORS = 5;

    // Runs the defect prediction experiment by loading data, preprocessing it, and testing all configurations.
    public void runExperiment(String datasetPath, String outputPath) throws Exception {
        // 1. Load data.
        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(datasetPath));
        Instances data = loader.getDataSet();

        // 2. Remove non-predictive identifiers:
        // Project, Release, File, Normalized_File.
        // This operation does not use the target values and does not cause leakage.
        Remove remove = new Remove();
        remove.setAttributeIndices("1-4");
        remove.setInputFormat(data);
        data = Filter.useFilter(data, remove);

        // The target attribute Buggy is the last remaining attribute.
        data.setClassIndex(data.numAttributes() - 1);
        requireClassValue(data, "Yes");
        requireClassValue(data, "No");

        File outputFile = new File(outputPath);
        File parentDirectory = outputFile.getParentFile();
        if (parentDirectory != null && !parentDirectory.exists()
                && !parentDirectory.mkdirs()) {
            throw new IllegalStateException(
                    "Unable to create output directory: " + parentDirectory);
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            writer.println(
                    "Dataset,Classifier,Balancing,FeatureSelection," +
                            "Iteration,Fold,Precision,Recall,FMeasure,AUC,Kappa,NPofB20");

            for (String clfName : CLASSIFIERS) {
                for (String fsName : FEATURE_SELECTION) {
                    for (String balName : BALANCING) {
                        System.out.printf(
                                "Running: %s | %s | %s%n",
                                clfName, fsName, balName);
                        execute10x10Fold(
                                data, clfName, fsName, balName, writer, "AVRO");
                    }
                }
            }
        }
    }

    // Executes a 10x10-fold cross-validation and writes the evaluation metrics to the output writer.
    private void execute10x10Fold(
            Instances data,
            String clfName,
            String fsName,
            String balName,
            PrintWriter writer,
            String datasetName) throws Exception {

        for (int iteration = 0; iteration < NUMBER_OF_REPETITIONS; iteration++) {
            // Different deterministic partition for each repetition.
            Instances seedData = new Instances(data);
            seedData.randomize(new Random(iteration + 1));
            seedData.stratify(NUMBER_OF_FOLDS);

            for (int fold = 0; fold < NUMBER_OF_FOLDS; fold++) {
                Instances train = seedData.trainCV(NUMBER_OF_FOLDS, fold);
                Instances test = seedData.testCV(NUMBER_OF_FOLDS, fold);

                int experimentSeed = 1000 * (iteration + 1) + fold + 1;
                int buggyClassIndex = requireClassValue(train, "Yes");
                int cleanClassIndex = requireClassValue(train, "No");

                Classifier baseClassifier =
                        getBaseClassifier(clfName, experimentSeed);

                // FilteredClassifier fits every filter exclusively on the
                // current training fold, preventing data leakage.
                FilteredClassifier filteredClassifier = new FilteredClassifier();
                filteredClassifier.setClassifier(baseClassifier);

                List<Filter> filters = new ArrayList<>();

                // Select the top 50% of the predictive attributes.
                // With 21 metrics, this retains the 10 highest-ranked metrics.
                if ("InfoGain".equals(fsName)) {
                    AttributeSelection attributeSelection = new AttributeSelection();
                    InfoGainAttributeEval evaluator = new InfoGainAttributeEval();
                    Ranker ranker = new Ranker();
                    ranker.setThreshold(0.0);
                    int numToSelect = (train.numAttributes() - 1) / 2;
                    ranker.setNumToSelect(numToSelect);
                    attributeSelection.setEvaluator(evaluator);
                    attributeSelection.setSearch(ranker);
                    filters.add(attributeSelection);
                }

                // IBk is distance-based. Standardization prevents attributes
                // with larger numeric scales from dominating the distance.
                if ("IBk".equals(clfName)) {
                    filters.add(new Standardize());
                }

                addBalancingFilter(
                        filters,
                        balName,
                        train,
                        buggyClassIndex,
                        cleanClassIndex,
                        experimentSeed);

                if (!filters.isEmpty()) {
                    MultiFilter multiFilter = new MultiFilter();
                    multiFilter.setFilters(filters.toArray(new Filter[0]));
                    filteredClassifier.setFilter(multiFilter);
                }

                filteredClassifier.buildClassifier(train);

                Evaluation evaluation = new Evaluation(train);
                evaluation.evaluateModel(filteredClassifier, test);

                double precision = evaluation.precision(buggyClassIndex);
                double recall = evaluation.recall(buggyClassIndex);
                double fMeasure = evaluation.fMeasure(buggyClassIndex);
                double auc = evaluation.areaUnderROC(buggyClassIndex);
                double kappa = evaluation.kappa();
                double npofb20 = calculateNPofB20(
                        filteredClassifier, test, buggyClassIndex);

                writer.printf(
                        java.util.Locale.US,
                        "%s,%s,%s,%s,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f%n",
                        datasetName,
                        clfName,
                        balName,
                        fsName,
                        iteration + 1,
                        fold + 1,
                        precision,
                        recall,
                        fMeasure,
                        auc,
                        kappa,
                        npofb20);
            }
        }
    }

    // Configures and adds the specified data balancing filter to the filter list.
    private void addBalancingFilter(
            List<Filter> filters,
            String balName,
            Instances train,
            int buggyClassIndex,
            int cleanClassIndex,
            int experimentSeed) throws Exception {

        int buggyCount = getInstancesCountByClass(train, buggyClassIndex);
        int cleanCount = getInstancesCountByClass(train, cleanClassIndex);
        int minorityCount = Math.min(buggyCount, cleanCount);
        int majorityCount = Math.max(buggyCount, cleanCount);

        switch (balName) {
            case "Undersampling":
                SpreadSubsample undersampling = new SpreadSubsample();
                undersampling.setDistributionSpread(1.0);
                undersampling.setRandomSeed(experimentSeed);
                filters.add(undersampling);
                break;

            case "Oversampling":
                Resample oversampling = new Resample();
                oversampling.setNoReplacement(false);
                oversampling.setBiasToUniformClass(1.0);
                oversampling.setRandomSeed(experimentSeed);

                // Final sample size: approximately twice the majority class,
                // so both classes contain approximately majorityCount items.
                double sampleSizePercentage =
                        (majorityCount * 2.0 / train.numInstances()) * 100.0;
                oversampling.setSampleSizePercent(sampleSizePercentage);
                filters.add(oversampling);
                break;

            case "SMOTE":
                if (minorityCount < 2) {
                    throw new IllegalStateException(
                            "SMOTE requires at least two minority instances; found "
                                    + minorityCount);
                }

                SMOTE smote = new SMOTE();
                smote.setRandomSeed(experimentSeed);
                smote.setNearestNeighbors(
                        Math.min(DEFAULT_SMOTE_NEIGHBORS, minorityCount - 1));

                // Generate enough synthetic minority instances to obtain an
                // approximately balanced 1:1 training distribution.
                double smotePercentage =
                        ((majorityCount - minorityCount) * 100.0) / minorityCount;
                smote.setPercentage(smotePercentage);
                filters.add(smote);
                break;

            case "None":
                break;

            default:
                throw new IllegalArgumentException(
                        "Unknown balancing technique: " + balName);
        }
    }

    // Retrieves the index of the specified class value, ensuring it exists within the dataset.
    private int requireClassValue(Instances data, String value) {
        int index = data.classAttribute().indexOfValue(value);
        if (index < 0) {
            throw new IllegalStateException(
                    "The class attribute does not contain the value '" + value + "'");
        }
        return index;
    }

    // Counts and returns the number of instances matching the given class value index.
    private int getInstancesCountByClass(Instances train, int classValue) {
        int count = 0;
        for (Instance instance : train) {
            if ((int) instance.classValue() == classValue) {
                count++;
            }
        }
        return count;
    }

    // Instantiates and configures the specified base classifier.
    private Classifier getBaseClassifier(String clfName, int experimentSeed) {
        switch (clfName) {
            case "RandomForest":
                RandomForest randomForest = new RandomForest();
                randomForest.setNumIterations(RANDOM_FOREST_TREES);
                randomForest.setSeed(experimentSeed);
                return randomForest;

            case "NaiveBayes":
                return new NaiveBayes();

            case "IBk":
                IBk ibk = new IBk();
                ibk.setKNN(IBK_NEIGHBORS);
                return ibk;

            default:
                throw new IllegalArgumentException(
                        "Unknown classifier: " + clfName);
        }
    }

    // Computes the bugs found when inspecting 20% of the total Lines of Code (NPofB20).
    private double calculateNPofB20(
            Classifier classifier,
            Instances test,
            int buggyClassIndex) throws Exception {

        if (test.attribute("Size_LOC") == null) {
            throw new IllegalStateException(
                    "The dataset does not contain the Size_LOC attribute");
        }
        int locIndex = test.attribute("Size_LOC").index();

        List<InstanceScore> scores = new ArrayList<>();
        double totalBugs = 0.0;
        double totalLoc = 0.0;

        for (Instance instance : test) {
            double loc = instance.value(locIndex);
            totalLoc += loc;

            boolean buggy = (int) instance.classValue() == buggyClassIndex;
            if (buggy) {
                totalBugs++;
            }

            double[] distribution = classifier.distributionForInstance(instance);
            double probabilityBuggy = distribution[buggyClassIndex];
            double defectDensity = loc > 0.0 ? probabilityBuggy / loc : 0.0;

            scores.add(new InstanceScore(buggy, loc, defectDensity));
        }

        if (totalBugs == 0.0 || totalLoc == 0.0) {
            return 0.0;
        }

        scores.sort(
                Comparator.comparingDouble(InstanceScore::getDefectDensity)
                        .reversed());

        double inspectedLoc = 0.0;
        double bugsFound = 0.0;
        double locLimit = totalLoc * 0.20;

        for (InstanceScore score : scores) {
            if (inspectedLoc >= locLimit) {
                break;
            }

            if (inspectedLoc + score.getLoc() <= locLimit) {
                inspectedLoc += score.getLoc();
                if (score.isBuggy()) {
                    bugsFound++;
                }
            } else {
                // Fractional inclusion of the last class to reach exactly
                // 20% of the test-set LOC budget.
                double remainingLoc = locLimit - inspectedLoc;
                if (score.isBuggy() && score.getLoc() > 0.0) {
                    bugsFound += remainingLoc / score.getLoc();
                }
                break;
            }
        }

        return bugsFound / totalBugs;
    }

    // Helper class to store and rank instances based on defect density.
    private static class InstanceScore {
        private final boolean buggy;
        private final double loc;
        private final double defectDensity;

        // Constructs an InstanceScore object.
        InstanceScore(boolean buggy, double loc, double defectDensity) {
            this.buggy = buggy;
            this.loc = loc;
            this.defectDensity = defectDensity;
        }

        // Returns whether the instance is buggy.
        boolean isBuggy() {
            return buggy;
        }

        // Returns the lines of code (LOC) of the instance.
        double getLoc() {
            return loc;
        }

        // Returns the defect density of the instance.
        double getDefectDensity() {
            return defectDensity;
        }
    }
}