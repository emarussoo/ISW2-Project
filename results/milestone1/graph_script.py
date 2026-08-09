#!/usr/bin/env python3
"""Genera i grafici essenziali per i Results della Milestone 1.

Uso:
    python milestone1_report_charts.py avro_metrics_dataset.csv

Output predefinito:
    figures_milestone1/
      - classes_per_release.png
      - classes_per_release.pdf
      - buggy_rate_per_release.png
      - buggy_rate_per_release.pdf
      - release_summary_for_charts.csv

I grafici sono calcolati direttamente dal CSV. L'unita di analisi e la
coppia Release + Normalized_File.
"""

from __future__ import annotations

import argparse
from pathlib import Path
import sys

import matplotlib.pyplot as plt
import pandas as pd

REQUIRED_COLUMNS = {"Release", "Normalized_File", "Buggy"}
VALID_LABELS = {"Yes", "No"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Genera i grafici essenziali della Milestone 1"
    )
    parser.add_argument(
        "csv_file",
        type=Path,
        help="Percorso del dataset CSV della Milestone 1",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("figures_milestone1"),
        help="Directory di output (default: figures_milestone1)",
    )
    parser.add_argument(
        "--dpi",
        type=int,
        default=300,
        help="Risoluzione dei file PNG (default: 300 DPI)",
    )
    return parser.parse_args()


def validate_dataset(df: pd.DataFrame) -> None:
    missing_columns = REQUIRED_COLUMNS - set(df.columns)
    if missing_columns:
        raise ValueError(
            "Colonne obbligatorie mancanti: " + ", ".join(sorted(missing_columns))
        )

    if df.empty:
        raise ValueError("Il dataset e vuoto.")

    if df[list(REQUIRED_COLUMNS)].isna().any().any():
        raise ValueError("Sono presenti valori mancanti nelle colonne obbligatorie.")

    invalid_labels = set(df["Buggy"].unique()) - VALID_LABELS
    if invalid_labels:
        raise ValueError(
            "Label Buggy non valide: " + ", ".join(sorted(invalid_labels))
        )

    duplicate_pairs = int(df.duplicated(["Release", "Normalized_File"]).sum())
    if duplicate_pairs:
        raise ValueError(
            f"Sono presenti {duplicate_pairs} coppie classe-release duplicate."
        )


def calculate_release_summary(df: pd.DataFrame) -> pd.DataFrame:
    """Calcola classi totali, classi buggy e buggy rate per release."""
    summary = (
        df.groupby("Release", sort=False)
        .agg(
            Total_Classes=("Normalized_File", "nunique"),
            Buggy_Classes=("Buggy", lambda values: int((values == "Yes").sum())),
        )
        .reset_index()
    )
    summary["Buggy_Rate_Percent"] = (
        summary["Buggy_Classes"] / summary["Total_Classes"] * 100
    )
    return summary


def save_figure(fig: plt.Figure, output_dir: Path, stem: str, dpi: int) -> None:
    """Salva la stessa figura in PNG e PDF."""
    fig.savefig(output_dir / f"{stem}.png", dpi=dpi, bbox_inches="tight")
    fig.savefig(output_dir / f"{stem}.pdf", bbox_inches="tight")
    plt.close(fig)


def plot_classes_per_release(
    summary: pd.DataFrame, output_dir: Path, dpi: int
) -> None:
    """Grafico a linee del numero di classi per release."""
    fig, ax = plt.subplots(figsize=(10, 5.5))
    ax.plot(
        summary["Release"],
        summary["Total_Classes"],
        marker="o",
    )
    ax.set_title("Evoluzione del numero di classi Java di produzione")
    ax.set_xlabel("Release")
    ax.set_ylabel("Numero di classi")
    ax.tick_params(axis="x", rotation=45)

    for x, y in zip(summary["Release"], summary["Total_Classes"]):
        ax.annotate(
            str(int(y)),
            (x, y),
            textcoords="offset points",
            xytext=(0, 6),
            ha="center",
            fontsize=8,
        )

    fig.tight_layout()
    save_figure(fig, output_dir, "classes_per_release", dpi)


def plot_buggy_rate_per_release(
    summary: pd.DataFrame,
    overall_buggy_rate: float,
    output_dir: Path,
    dpi: int,
) -> None:
    """Grafico a barre del buggy rate con media complessiva."""
    fig, ax = plt.subplots(figsize=(10, 5.5))
    bars = ax.bar(summary["Release"], summary["Buggy_Rate_Percent"])
    ax.axhline(
        overall_buggy_rate,
        linestyle="--",
        label=f"Buggy rate complessivo: {overall_buggy_rate:.2f}%",
    )
    ax.set_title("Percentuale di classi buggy per release")
    ax.set_xlabel("Release")
    ax.set_ylabel("Classi buggy (%)")
    ax.tick_params(axis="x", rotation=45)
    ax.legend()

    for bar, value in zip(bars, summary["Buggy_Rate_Percent"]):
        ax.annotate(
            f"{value:.2f}%",
            (bar.get_x() + bar.get_width() / 2, bar.get_height()),
            textcoords="offset points",
            xytext=(0, 4),
            ha="center",
            fontsize=8,
        )

    fig.tight_layout()
    save_figure(fig, output_dir, "buggy_rate_per_release", dpi)


def main() -> int:
    args = parse_args()
    csv_path = args.csv_file.resolve()
    output_dir = args.output_dir.resolve()

    if not csv_path.is_file():
        print(f"ERRORE: file non trovato: {csv_path}", file=sys.stderr)
        return 2

    output_dir.mkdir(parents=True, exist_ok=True)

    df = pd.read_csv(csv_path, dtype=str)
    df.columns = df.columns.str.strip()
    for column in REQUIRED_COLUMNS:
        if column in df.columns:
            df[column] = df[column].str.strip()

    try:
        validate_dataset(df)
    except ValueError as error:
        print(f"ERRORE: {error}", file=sys.stderr)
        return 3

    summary = calculate_release_summary(df)
    overall_buggy_rate = (df["Buggy"] == "Yes").mean() * 100

    summary.to_csv(
        output_dir / "release_summary_for_charts.csv",
        index=False,
        float_format="%.4f",
    )

    plot_classes_per_release(summary, output_dir, args.dpi)
    plot_buggy_rate_per_release(
        summary,
        overall_buggy_rate,
        output_dir,
        args.dpi,
    )

    print("Grafici generati correttamente.")
    print(f"Release analizzate: {len(summary)}")
    print(f"Buggy rate complessivo: {overall_buggy_rate:.2f}%")
    print(f"Directory di output: {output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
