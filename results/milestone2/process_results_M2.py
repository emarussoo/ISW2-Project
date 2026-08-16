import os
import pandas as pd


def process_results():
    base_dir = os.path.dirname(
        os.path.dirname(os.path.abspath(__file__))
    )

    input_file = os.path.join(
        base_dir,
        "results/milestone2/experiment_raw.csv"
    )

    output_file = os.path.join(
        base_dir,
        "results/milestone2/experiment_processed.csv"
    )

    print(f"Lettura dei dati da: {input_file}")

    try:
        df = pd.read_csv(input_file)
    except FileNotFoundError:
        print(f"Errore: il file {input_file} non esiste.")
        return

    required_columns = [
        "Dataset",
        "Classifier",
        "Balancing",
        "FeatureSelection",
        "Iteration",
        "Fold",
        "Precision",
        "Recall",
        "FMeasure",
        "AUC",
        "Kappa",
        "NPofB20"
    ]

    missing_columns = [
        column
        for column in required_columns
        if column not in df.columns
    ]

    if missing_columns:
        print(
            "Errore: nel file raw mancano le colonne: "
            + ", ".join(missing_columns)
        )
        return


    df["Balancing"] = df["Balancing"].fillna("None")
    df["FeatureSelection"] = (
        df["FeatureSelection"].fillna("None")
    )

    group_cols = [
        "Dataset",
        "Classifier",
        "Balancing",
        "FeatureSelection"
    ]

    metric_cols = [
        "Precision",
        "Recall",
        "FMeasure",
        "AUC",
        "Kappa",
        "NPofB20"
    ]


    summary_df = (
       df.groupby(group_cols, dropna=False)[metric_cols]
       .mean()
       .reset_index()
    )

    summary_df.rename(
        columns={
            "Dataset": "projName",
            "Classifier": "classifier",
            "Balancing": "balancing",
            "FeatureSelection": "featureSelection",
            "Precision": "precision",
            "Recall": "recall",
            "FMeasure": "fMeasure",
            "AUC": "ROC_Area",
            "Kappa": "kappa",
            "NPofB20": "NPofB20"
        },
        inplace=True
    )


    summary_df["sensitivity"] = summary_df["recall"]
    summary_df["TP_Rate"] = summary_df["recall"]

    decimal_columns = [
        "sensitivity",
        "TP_Rate",
        "precision",
        "recall",
        "fMeasure",
        "ROC_Area",
        "kappa",
        "NPofB20"
    ]

    summary_df[decimal_columns] = (
        summary_df[decimal_columns].round(3)
    )

    final_columns = [
        "projName",
        "classifier",
        "balancing",
        "featureSelection",
        "sensitivity",
        "TP_Rate",
        "precision",
        "recall",
        "fMeasure",
        "ROC_Area",
        "kappa",
        "NPofB20"
    ]

    summary_df = summary_df[final_columns]


    expected_configurations = 24
    expected_evaluations_per_configuration = 100
    expected_raw_rows = (
        expected_configurations
        * expected_evaluations_per_configuration
    )

    configuration_sizes = df.groupby(group_cols).size()

    print()
    print("Controlli dell'esperimento")
    print("-------------------------")
    print(f"Righe raw trovate: {len(df)}")
    print(f"Righe raw attese: {expected_raw_rows}")
    print(
        f"Configurazioni trovate: "
        f"{len(configuration_sizes)}"
    )
    print(
        "Valutazioni per configurazione: "
        f"{sorted(configuration_sizes.unique())}"
    )

    if len(df) != expected_raw_rows:
        print(
            "ATTENZIONE: il numero delle righe raw "
            "non è quello atteso."
        )

    if len(configuration_sizes) != expected_configurations:
        print(
            "ATTENZIONE: il numero delle configurazioni "
            "non è quello atteso."
        )

    if not (
        configuration_sizes
        == expected_evaluations_per_configuration
    ).all():
        print(
            "ATTENZIONE: almeno una configurazione non "
            "contiene esattamente 100 valutazioni."
        )

    if df[metric_cols].isna().any().any():
        print(
            "ATTENZIONE: sono presenti valori mancanti "
            "nelle metriche."
        )

    if not df[metric_cols].apply(
        lambda column: column.between(0, 1).all()
    ).all():
        print(
            "ATTENZIONE: almeno una metrica contiene "
            "valori esterni all'intervallo [0, 1]."
        )

    summary_df.to_csv(
        output_file,
        index=False,
        sep=";",
        decimal=","
    )

    print()
    print(
        "Elaborazione completata. "
        f"Risultati salvati in: {output_file}"
    )


if __name__ == "__main__":
    process_results()