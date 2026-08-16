# ISW2 Project: Software Analytics on Apache Avro

Final project developed for the **Software Engineering II** course at the University of Rome Tor Vergata.

- **Student:** Emanuele Russo
- **Professor:** Davide Falessi
- **System under study:** [Apache Avro](https://avro.apache.org/)
- **Academic year:** 2025-2026

## Project overview

This repository contains a Software Analytics pipeline developed to study four connected topics on Apache Avro:

1. construction of a historical defect dataset at class-release level;
2. evaluation of defect-prediction classifiers;
3. What-If Analysis of a synthetic scenario without code smells;
4. automated refactoring with Microsoft Copilot and validation of the generated variants.

The project combines information from Jira, Git history, PMD, Weka, and SonarCloud. The generated defect labels, statistical associations, predictions, and counterfactual results are empirical estimates for the analyzed Apache Avro history. They must not be interpreted as perfect ground truth or causal evidence.

## Repository structure

```text
ISW2-Project/
├── .idea/                         IntelliJ IDEA project settings
├── .mvn/                          Maven Wrapper configuration
├── docs_refactor/                 C0-C4 refactoring experiment artifacts
│   ├── schema/
│   │   ├── C0/
│   │   ├── C1/
│   │   ├── C2/
│   │   ├── C3/
│   │   └── C4/
│   └── timeconversions/
│       ├── C0/
│       ├── C1/
│       ├── C2/
│       ├── C3/
│       └── C4/
├── pmd/                           Local PMD distribution used by Milestone 1
├── results/                       Generated and processed experimental results
│   ├── milestone1/
│   ├── milestone2/
│   ├── milestone3/
│   └── milestone4/                     Presentation material
├── src/
│   └── main/
│       └── java/
│           └── it/uniroma2/isw2/
│               ├── classification/
│               │   └── WekaEvaluator.java
│               ├── dataset/
│               │   ├── git/
│               │   │   └── GitMetricsExtractor.java
│               │   ├── jira/
│               │   │   └── JiraFetcher.java
│               │   └── labeling/
│               │       ├── BugLabeler.java
│               │       ├── GitBugMapper.java
│               │       └── VersionLifecycleCalculator.java
│               ├── milestones/
│               │   ├── Milestone1.java
│               │   ├── Milestone2.java
│               │   ├── Milestone3.java
│               │   └── Milestone4.java
│               ├── model/
│               │   ├── ClassMetricsRow.java
│               │   ├── Release.java
│               │   └── Ticket.java
│               └── utils/
│                   ├── CsvExporter.java
│                   └── PmdAnalyzer.java
├── .gitignore
├── pom.xml
└── README.md
```

The folders under `docs_refactor/` preserve the experimental material for the original classes and the independently generated C1-C4 variants. The folders under `results/` separate the outputs of the four milestones.

## Main components

### `classification`

`WekaEvaluator.java` loads the Milestone 1 dataset and evaluates all combinations of:

- Random Forest, Naive Bayes, and IBk;
- no feature selection or Information Gain;
- no balancing, undersampling, oversampling, or SMOTE.

The evaluator performs repeated stratified 10-fold cross-validation and computes Precision, Recall, F1-measure, AUC, Cohen's Kappa, and NPofB20 for the positive class `Buggy=Yes`.

### `dataset/git`

`GitMetricsExtractor.java` checks out the Git tag associated with each selected release, identifies Java production files, runs PMD, follows file history through JGit, and computes the static and process metrics used in the dataset.

### `dataset/jira`

`JiraFetcher.java` retrieves:

- project releases and release dates;
- resolved or closed Jira issues of type `Bug` with resolution `Fixed`;
- ticket creation and resolution dates;
- Jira Affected Versions.

### `dataset/labeling`

The labeling package contains the release-level defect reconstruction:

- `VersionLifecycleCalculator.java` assigns OV, FV, and IV;
- `GitBugMapper.java` maps Jira tickets to Java production files modified by associated Git commits;
- `BugLabeler.java` assigns the binary `Buggy` label to class-release instances.

The package name intentionally uses `labeling` rather than `szz`: the implementation does **not** use classical SZZ based on line-level backward search and `git blame`.

### `milestones`

Each milestone has a dedicated entry point:

- `Milestone1.java`: dataset construction and labeling;
- `Milestone2.java`: Weka classifier evaluation;
- `Milestone3.java`: What-If Analysis;
- `Milestone4.java`: SonarCloud-based class selection and diagnostic export for the refactoring experiment.

### `model`

The domain model contains:

- `Release`: Jira release identifier, name, and release date;
- `Ticket`: Jira issue dates, lifecycle versions, Affected Versions, and mapped files;
- `ClassMetricsRow`: identifiers, 21 features, and target label for one class-release instance.

### `utils`

- `CsvExporter.java` exports the Milestone 1 dataset;
- `PmdAnalyzer.java` executes PMD and aggregates violations by Java file.

## Requirements

The project requires:

- Java and Maven versions compatible with `pom.xml`;
- a local clone of Apache Avro, including its `.git` directory;
- PMD 7.24.0;
- Python 3 for result processing and correlation analysis;
- network access to the Apache Jira REST API for Milestone 1;
- network access to SonarCloud for the class-selection phase of Milestone 4;
- a valid SonarCloud project key and token for Milestone 4.

Python packages used by the correlation analysis:

```bash
python3 -m pip install numpy pandas scipy
```

## Local configuration

### Apache Avro repository

Before running Milestone 1, update the local Apache Avro repository path in `Milestone1.java`.

The configured value must point to the repository `.git` directory, for example:

```text
/path/to/avro/.git
```

### PMD

The repository contains a local `pmd/` directory. Verify that the executable path configured in `GitMetricsExtractor.java` points to the local PMD binary used on the current machine.

Milestone 1 uses these PMD Java rule categories:

```text
category/java/design.xml
category/java/errorprone.xml
category/java/bestpractices.xml
```

### SonarCloud credentials

Milestone 4 reads the credentials from a `.env` file placed in the repository root:

```properties
SONAR_PROJECT_KEY=your_project_key
SONAR_TOKEN=your_sonar_token
```

Never commit the `.env` file or access tokens.

## Build

Using the installed Maven executable:

```bash
mvn clean compile
```

If the Maven Wrapper is fully configured, the corresponding command is:

```bash
./mvnw clean compile
```

## Milestone 1: Dataset construction

### Objective

Milestone 1 creates a historical dataset in which each row represents a Java production class observed in one Apache Avro release.

The pipeline:

1. downloads the release timeline from Jira;
2. orders releases chronologically;
3. retains the first 34% of releases for the dataset rows;
4. checks out the matching Git tag for each retained release;
5. identifies Java production files;
6. calculates static and process metrics;
7. runs PMD on every analyzed release snapshot;
8. retrieves fixed Jira bug tickets;
9. reconstructs IV, OV, and FV;
10. maps tickets to files modified by fixing commits;
11. assigns the binary `Buggy` label;
12. exports the dataset to `results/milestone1/`.

Although only the first 34% of releases contributes dataset rows, the complete release and ticket history is retained for labeling. A bug fixed later can therefore show that a class in an earlier retained release was already defective.

### Features

Each class-release row contains four identifiers, 21 numerical features, and one binary target:

```text
Project
Release
File
Normalized_File

Size_LOC
NR
NAuth
NFix
Age_in_Days
Weighted_Age
LOC_Added
Max_LOC_Added
Average_LOC_Added
LOC_Deleted
Max_LOC_Deleted
Average_LOC_Deleted
Churn
Max_Churn
Average_Churn
Change_Set_Size
Max_Change_Set_Size
Average_Change_Set_Size
Average_ND
Average_Entropy
NSmells

Buggy
```

`NFix` is an heuristic process metric based on commit-message patterns. It is separate from the structured Jira and Git procedure used to create the target label.

### Defect lifecycle

The lifecycle uses:

- **OV**, Opening Version;
- **FV**, Fixed Version;
- **IV**, Injected Version.

OV is approximated as the first release on or after the Jira creation date. FV is approximated as the first release on or after the Jira resolution date. The Jira resolution date is therefore a temporal proxy and may not coincide exactly with the release containing the fixing commit.

IV is obtained from:

1. the oldest temporally coherent Jira Affected Version, when available;
2. Proportion Total when no valid Affected Version is available.

A class-release instance is labeled buggy when the class is associated with a mapped ticket and the release belongs to:

```text
[IV, FV)
```

FV is excluded because it is treated as the first release expected to contain the correction.

### Why classical SZZ is not used

Classical SZZ would inspect lines changed by a fixing commit and use `git blame` to search for candidate bug-introducing commits. This project does not implement that procedure.

Apache Avro contains refactorings, renames, file moves, and directory reorganizations that can make line ownership ambiguous. The adopted pipeline therefore separates:

- file identification through Jira keys found in commit messages;
- release-level lifecycle reconstruction through Jira information and Proportion Total.

The resulting labels are retrospective operational estimates rather than perfect ground truth.

### Run

```bash
mvn exec:java \
  -Dexec.mainClass="it.uniroma2.isw2.milestones.Milestone1"
```

Main output:

```text
results/milestone1/avro_metrics_dataset.csv
```

Dataset used in the final report:

- 14 releases;
- 200 distinct Java production classes;
- 1,842 class-release instances;
- 109 buggy instances;
- 1,733 non-buggy instances.

## Feature-bugginess correlation analysis

The script `results/milestone1/feature_correlations.py`, when included in the repository or executed alongside the generated dataset, calculates the association between the 21 features and the binary `Buggy` label.

The analysis uses:

- Spearman correlation as the primary measure;
- point-biserial correlation as a complementary sensitivity analysis;
- Benjamini-Hochberg adjusted p-values for multiple comparisons.

Example execution:

```bash
python3 results/milestone1/feature_correlations.py \
  results/milestone1/avro_metrics_dataset.csv \
  --output-dir results/milestone1
```

Expected outputs:

```text
results/milestone1/feature_bugginess_correlations.csv
results/milestone1/feature_bugginess_correlations.tex
```

The LaTeX output reports statistically significant Spearman associations with `|rho| >= 0.10`. Statistical significance does not imply a strong association or a causal relationship. The same class can occur in multiple releases, so conventional p-values can also be optimistic because of longitudinal dependence.

## Milestone 2: Classifier evaluation

### Experimental design

Milestone 2 evaluates the Cartesian product of:

- classifiers: Random Forest, Naive Bayes, IBk;
- feature selection: none, Information Gain;
- balancing: none, undersampling, oversampling, SMOTE.

The experiment therefore includes:

```text
3 classifiers x 2 feature-selection conditions x 4 balancing conditions
= 24 configurations
```

Each configuration is evaluated with repeated stratified 10-fold cross-validation:

```text
10 repetitions x 10 folds = 100 evaluations per configuration
24 configurations x 100 evaluations = 2,400 raw results
```

Information Gain, standardization, and balancing are included in a Weka `FilteredClassifier`, so each transformation is learned only from the current training fold. The test fold remains unmodified.

### Evaluation metrics

All class-dependent metrics use `Buggy=Yes` as the positive class:

- Precision;
- Recall;
- F1-measure;
- AUC;
- Cohen's Kappa;
- NPofB20.

NPofB20 ranks test instances by the predicted buggy probability divided by LOC and measures the proportion of buggy instances found within a 20% LOC inspection budget.

### Run

```bash
mvn exec:java \
  -Dexec.mainClass="it.uniroma2.isw2.milestones.Milestone2"
```

Main output:

```text
results/milestone2/experiment_raw.csv
```

Then by running the script `results/milestone2/process_results_M2.py` the results list will be converted in a smaller recap of the execution in:

```text
results/milestone2/experiment_processed.csv
```

The configuration selected for Milestone 3 is:

```text
Random Forest + SMOTE + no feature selection
```

This configuration obtained the highest mean AUC and NPofB20 while maintaining a balanced Precision-Recall profile.

## Milestone 3: What-If Analysis

### Objective

Milestone 3 measures how the selected classifier changes its predictions when `NSmells` is set to zero while all other features remain unchanged.

The analysis constructs:

- **A**: the original dataset;
- **B+**: instances with `NSmells > 0`;
- **B**: a copy of B+ with `NSmells = 0`;
- **C**: instances originally having `NSmells = 0`.

The selected Random Forest with SMOTE is trained once on A and applied without retraining to A, B+, B, and C.

This is a model-based counterfactual analysis. It does not modify the source code and does not prove that removing code smells would causally prevent the estimated number of defects.

### Run

```bash
mvn exec:java \
  -Dexec.mainClass="it.uniroma2.isw2.milestones.Milestone3"
```

Generated datasets:

```text
results/milestone3/dataset_B_plus.csv
results/milestone3/dataset_B.csv
results/milestone3/dataset_C.csv
```

Main result reported by the experiment:

```text
Predicted buggy instances in B+: 104
Predicted buggy instances in B:   66
Direct prediction drop:           38
Direct prediction reduction:      36.54%
```

All 109 observed buggy instances belong to B+, whereas C contains no observed buggy instance. This result is specific to the constructed Apache Avro dataset and must not be generalized as a universal relationship between code smells and defects.

## Milestone 4: Automated refactoring

### Automated class selection

`Milestone4.java` queries SonarCloud and retains Java files satisfying:

```text
NCLOC > 150
code smells >= 1
```

The remaining classes are ordered in descending order by smell count. The experiment selects the first and last classes of the filtered ranking:

- `Schema.java`;
- `TimeConversions.java`.

The automated phase exports ranking and diagnostic data used for the Copilot prompts.

### Run the selection phase

```bash
mvn exec:java \
  -Dexec.mainClass="it.uniroma2.isw2.milestones.Milestone4"
```

The exact output placement should follow the configuration used by `Milestone4.java`; final Milestone 4 artifacts are preserved under:

```text
results/milestone4/
docs_refactor/
```

### `docs_refactor/schema`

This directory contains the experimental versions of `Schema.java`:

- `C0`: original baseline;
- `C1`: refactoring generated without test context;
- `C2`: refactoring generated with black-box tests;
- `C3`: refactoring generated with black-box and control-flow tests;
- `C4`: refactoring generated with black-box, control-flow, and mutation-testing context.

### `docs_refactor/timeconversions`

This directory contains the experimental versions of `TimeConversions.java`:

- `C0`: pre-fix baseline;
- `C1`: refactoring generated without test context;
- `C2`: refactoring generated with black-box tests;
- `C3`: refactoring generated with BB+CF context;
- `C4`: refactoring generated with BB+CF and active bug-exposing requirements.

For `TimeConversions.java`, C4 is not a new mutation-testing-guided variant. C4 is a corrective, test-guided variant based on the real bug context and the expected behavior encoded by active bug-exposing tests.

The identified defect concerned nanosecond conversion for instants before the Unix epoch and was later addressed in [Apache Avro pull request #3813](https://github.com/apache/avro/pull/3813).

### Validation of C0-C4

Variant generation, source replacement, test execution, and comparative analysis are experimental stages separate from the class-selection code in `Milestone4.java`.

Each variant is evaluated through:

- compilation in the Apache Avro project;
- the test suites available for the selected class;
- SonarCloud static analysis;
- comparison with C0 for code smells, bugs, dimensions, complexity, duplication, ratings, and remediation effort.

JaCoCo and PIT were used during test-suite development to guide coverage improvement and mutation-based tests. They are not treated as independent final criteria for selecting the best C0-C4 variant in the summarized report results.

Main findings:

- `Schema.java` C3 reduces code smells from 90 to 37 and total issues from 102 to 49;
- `TimeConversions.java` C4 corrects the real defect and improves the Reliability Rating from B to A without increasing the main structural metrics;
- more test context does not produce a monotonic improvement in static quality;
- every generated variant must be compiled, tested, and statically analyzed before acceptance.

## Results directory

The `results/` directory is organized by milestone:

```text
results/
├── milestone1/    Dataset, correlation analysis, and related outputs
├── milestone2/    Raw and processed classifier-evaluation results
├── milestone3/    What-If datasets and analysis outputs
└── milestone4/    SonarCloud ranking, diagnostics, and comparison outputs
```

Generated files should be treated as experiment artifacts. Large, temporary, or machine-specific reports can be excluded from version control when they can be reproduced from the documented pipeline.



