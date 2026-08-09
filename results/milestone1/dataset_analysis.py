#!/usr/bin/env python3
"""Calcola i risultati essenziali della Milestone 1.

Uso:
    python milestone1_report_results.py avro_metrics_dataset.csv

Output:
    results_milestone1_report/
      - dataset_overview.csv
      - release_summary.csv
      - stability_summary.csv
      - results_summary.txt

Lo script usa come unita di analisi la coppia Release + Normalized_File.
"""

from __future__ import annotations

import argparse
from collections import OrderedDict
from pathlib import Path
import sys

import pandas as pd

REQUIRED_COLUMNS = {"Project", "Release", "Normalized_File", "Buggy"}
VALID_LABELS = {"Yes", "No"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Risultati essenziali della Milestone 1"
    )
    parser.add_argument(
        "csv_file",
        type=Path,
        help="Percorso del dataset CSV della Milestone 1",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("results_milestone1_report"),
        help="Directory di output",
    )
    return parser.parse_args()


def validate_dataset(df: pd.DataFrame) -> None:
    """Interrompe l'analisi se il dataset non rispetta i requisiti minimi."""
    missing_columns = REQUIRED_COLUMNS - set(df.columns)
    if missing_columns:
        raise ValueError(
            "Colonne obbligatorie mancanti: " + ", ".join(sorted(missing_columns))
        )

    if df.empty:
        raise ValueError("Il dataset e vuoto.")

    if df[list(REQUIRED_COLUMNS)].isna().any().any():
        raise ValueError("Sono presenti valori mancanti nelle colonne obbligatorie.")

    labels = set(df["Buggy"].unique())
    invalid_labels = labels - VALID_LABELS
    if invalid_labels:
        raise ValueError(
            "Label Buggy non valide: " + ", ".join(sorted(invalid_labels))
        )

    duplicate_pairs = df.duplicated(["Release", "Normalized_File"]).sum()
    if duplicate_pairs:
        raise ValueError(
            f"Sono presenti {duplicate_pairs} coppie classe-release duplicate."
        )


def calculate_release_summary(df: pd.DataFrame) -> pd.DataFrame:
    """Calcola classi totali, buggy e buggy rate per ogni release."""
    summary = (
        df.groupby("Release", sort=False)
        .agg(
            Total_Classes=("Normalized_File", "nunique"),
            Buggy_Classes=("Buggy", lambda values: int((values == "Yes").sum())),
        )
        .reset_index()
    )

    summary["Non_Buggy_Classes"] = (
        summary["Total_Classes"] - summary["Buggy_Classes"]
    )
    summary["Buggy_Rate_Percent"] = (
        summary["Buggy_Classes"] / summary["Total_Classes"] * 100
    )
    summary["Class_Change"] = summary["Total_Classes"].diff()
    return summary


def calculate_mean_retention(df: pd.DataFrame, release_order: list[str]) -> tuple[float, float]:
    """Calcola retention rate medio e minimo tra release consecutive.

    Retention(A,B) = classi presenti sia in A sia in B / classi presenti in A.
    """
    class_sets: OrderedDict[str, set[str]] = OrderedDict()
    for release in release_order:
        class_sets[release] = set(
            df.loc[df["Release"] == release, "Normalized_File"]
        )

    retention_rates = []
    releases = list(class_sets.keys())
    for previous, current in zip(releases, releases[1:]):
        previous_classes = class_sets[previous]
        current_classes = class_sets[current]
        retained = len(previous_classes & current_classes)
        retention_rates.append(retained / len(previous_classes) * 100)

    return sum(retention_rates) / len(retention_rates), min(retention_rates)


def main() -> int:
    args = parse_args()
    csv_path = args.csv_file.resolve()
    output_dir = args.output_dir.resolve()

    if not csv_path.is_file():
        print(f"ERRORE: file non trovato: {csv_path}", file=sys.stderr)
        return 2

    output_dir.mkdir(parents=True, exist_ok=True)

    df = pd.read_csv(csv_path, dtype={"Project": str, "Release": str})
    df.columns = df.columns.str.strip()
    for column in ["Project", "Release", "Normalized_File", "Buggy"]:
        if column in df.columns:
            df[column] = df[column].astype(str).str.strip()

    try:
        validate_dataset(df)
    except ValueError as error:
        print(f"ERRORE: {error}", file=sys.stderr)
        return 3

    release_summary = calculate_release_summary(df)
    release_order = release_summary["Release"].tolist()

    total_instances = len(df)
    total_releases = df["Release"].nunique()
    distinct_classes = df["Normalized_File"].nunique()
    buggy_instances = int((df["Buggy"] == "Yes").sum())
    non_buggy_instances = int((df["Buggy"] == "No").sum())
    overall_buggy_rate = buggy_instances / total_instances * 100

    first_class_count = int(release_summary.iloc[0]["Total_Classes"])
    last_class_count = int(release_summary.iloc[-1]["Total_Classes"])
    overall_class_growth = last_class_count - first_class_count
    overall_class_growth_percent = overall_class_growth / first_class_count * 100

    mean_retention, min_retention = calculate_mean_retention(df, release_order)

    peak_buggy_row = release_summary.loc[
        release_summary["Buggy_Rate_Percent"].idxmax()
    ]
    largest_growth_row = release_summary.loc[
        release_summary["Class_Change"].idxmax()
    ]

    overview = pd.DataFrame(
        [
            ["Release analizzate", total_releases],
            ["Classi distinte", distinct_classes],
            ["Istanze classe-release", total_instances],
            ["Istanze buggy", buggy_instances],
            ["Istanze non buggy", non_buggy_instances],
            ["Buggy rate complessivo (%)", overall_buggy_rate],
        ],
        columns=["Indicator", "Value"],
    )

    stability = pd.DataFrame(
        [
            ["Classi nella prima release", first_class_count],
            ["Classi nell'ultima release", last_class_count],
            ["Crescita complessiva (classi)", overall_class_growth],
            ["Crescita complessiva (%)", overall_class_growth_percent],
            ["Retention rate medio (%)", mean_retention],
            ["Retention rate minimo (%)", min_retention],
            ["Massimo incremento in una release", int(largest_growth_row["Class_Change"])],
            ["Release del massimo incremento", largest_growth_row["Release"]],
            ["Massimo buggy rate (%)", float(peak_buggy_row["Buggy_Rate_Percent"])],
            ["Release con massimo buggy rate", peak_buggy_row["Release"]],
        ],
        columns=["Indicator", "Value"],
    )

    overview.to_csv(output_dir / "dataset_overview.csv", index=False, float_format="%.2f")
    release_summary.to_csv(
        output_dir / "release_summary.csv", index=False, float_format="%.2f"
    )
    stability.to_csv(
        output_dir / "stability_summary.csv", index=False, float_format="%.2f"
    )

    lines = [
        "MILESTONE 1 - RISULTATI ESSENZIALI",
        "===================================",
        f"Release analizzate: {total_releases}",
        f"Classi distinte: {distinct_classes}",
        f"Istanze classe-release: {total_instances}",
        f"Istanze buggy: {buggy_instances}",
        f"Istanze non buggy: {non_buggy_instances}",
        f"Buggy rate complessivo: {overall_buggy_rate:.2f}%",
        "",
        "STABILITA ESSENZIALE",
        f"Classi: {first_class_count} nella prima release, {last_class_count} nell'ultima.",
        f"Crescita complessiva: {overall_class_growth} classi ({overall_class_growth_percent:.2f}%).",
        f"Retention rate medio tra release consecutive: {mean_retention:.2f}%.",
        f"Retention rate minimo: {min_retention:.2f}%.",
        f"Massimo incremento: +{int(largest_growth_row['Class_Change'])} classi nella release {largest_growth_row['Release']}.",
        "",
        "BUGGINESS",
        f"Massimo buggy rate: {peak_buggy_row['Buggy_Rate_Percent']:.2f}% nella release {peak_buggy_row['Release']}.",
    ]

    report_text = "\n".join(lines) + "\n"
    (output_dir / "results_summary.txt").write_text(report_text, encoding="utf-8")

    print(report_text)
    print("CLASSI E BUGGINESS PER RELEASE")
    print(release_summary.to_string(index=False, float_format=lambda value: f"{value:.2f}"))
    print(f"\nFile salvati in: {output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
