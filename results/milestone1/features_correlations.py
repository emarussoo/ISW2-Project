#!/usr/bin/env python3
"""Compute feature correlations with a binary bugginess label.

The script reads the Milestone 1 CSV dataset, converts the Buggy label
to 0/1, and computes for each numeric feature:

1. Spearman rank correlation as the primary association measure.
2. Point-biserial correlation as a complementary sensitivity analysis.
3. Two-sided p-values and Benjamini-Hochberg adjusted p-values.

The generated LaTeX table reports statistically significant Spearman
associations with an absolute coefficient of at least 0.10.

Outputs:
- feature_bugginess_correlations.csv
- feature_bugginess_correlations.tex
"""

from __future__ import annotations

import argparse
import csv
from pathlib import Path
from typing import Iterable

import numpy as np
import pandas as pd
from scipy.stats import pointbiserialr, spearmanr


DEFAULT_LABEL = "Buggy"

DEFAULT_EXCLUDED_COLUMNS = {
    "Project",
    "Release",
    "File",
    "Normalized_File",
    "ClassName",
}

POSITIVE_LABELS = {
    "yes",
    "true",
    "1",
    "buggy",
    "positive",
}

NEGATIVE_LABELS = {
    "no",
    "false",
    "0",
    "clean",
    "negative",
    "not buggy",
}

EXPECTED_FEATURE_COUNT = 21
MINIMUM_REPORTED_SPEARMAN = 0.10


def parse_arguments() -> argparse.Namespace:
    """Parse command-line arguments for the input dataset and output files."""
    parser = argparse.ArgumentParser(
        description=(
            "Compute Spearman and point-biserial correlations between "
            "the Milestone 1 features and the binary bugginess label."
        )
    )
    parser.add_argument(
        "dataset",
        type=Path,
        help="Path to the Milestone 1 CSV file.",
    )
    parser.add_argument(
        "--label",
        default=DEFAULT_LABEL,
        help=f"Name of the binary target column. Default: {DEFAULT_LABEL}",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("."),
        help="Directory for generated CSV and LaTeX files. Default: current directory.",
    )
    parser.add_argument(
        "--alpha",
        type=float,
        default=0.05,
        help="Significance threshold used in the output. Default: 0.05",
    )
    return parser.parse_args()


def detect_separator(path: Path) -> str:
    """Detect whether the CSV file uses a comma or semicolon separator."""
    with path.open("r", encoding="utf-8-sig", newline="") as stream:
        sample = stream.read(8192)
    try:
        return csv.Sniffer().sniff(sample, delimiters=",;").delimiter
    except csv.Error:
        return ","


def encode_binary_label(series: pd.Series) -> pd.Series:
    """Convert common textual or numeric labels to integer values 0 and 1."""
    if series.isna().any():
        raise ValueError("The target column contains missing values.")

    if pd.api.types.is_bool_dtype(series):
        return series.astype(int)

    if pd.api.types.is_numeric_dtype(series):
        numeric = pd.to_numeric(series, errors="raise")
        values = set(numeric.unique())
        if not values.issubset({0, 1}):
            raise ValueError(
                "Numeric target values must be 0/1, "
                f"but found: {sorted(values)}"
            )
        return numeric.astype(int)

    normalized = series.astype(str).str.strip().str.lower()
    unknown = sorted(
        set(normalized.unique()) - POSITIVE_LABELS - NEGATIVE_LABELS
    )
    if unknown:
        raise ValueError(
            "Unsupported target values: "
            + ", ".join(repr(value) for value in unknown)
        )

    return normalized.map(
        lambda value: 1 if value in POSITIVE_LABELS else 0
    ).astype(int)


def benjamini_hochberg(p_values: Iterable[float]) -> np.ndarray:
    """Adjust a family of p-values using Benjamini-Hochberg."""
    values = np.asarray(list(p_values), dtype=float)
    adjusted = np.full(values.shape, np.nan, dtype=float)
    valid_indices = np.where(np.isfinite(values))[0]

    if len(valid_indices) == 0:
        return adjusted

    valid_values = values[valid_indices]
    order = np.argsort(valid_values)
    ranked = valid_values[order]
    count = len(ranked)

    ranked_adjusted = ranked * count / np.arange(1, count + 1)
    ranked_adjusted = np.minimum.accumulate(ranked_adjusted[::-1])[::-1]
    ranked_adjusted = np.clip(ranked_adjusted, 0.0, 1.0)

    restored = np.empty(count, dtype=float)
    restored[order] = ranked_adjusted
    adjusted[valid_indices] = restored
    return adjusted


def classify_direction(coefficient: float) -> str:
    """Describe the direction of a correlation coefficient."""
    if not np.isfinite(coefficient) or np.isclose(coefficient, 0.0):
        return "None"
    return "Positive" if coefficient > 0.0 else "Negative"


def classify_strength(coefficient: float) -> str:
    """Classify the absolute magnitude using descriptive bands."""
    if not np.isfinite(coefficient):
        return "Not available"

    magnitude = abs(coefficient)
    if magnitude < 0.10:
        return "Negligible"
    if magnitude < 0.30:
        return "Weak"
    if magnitude < 0.50:
        return "Moderate"
    if magnitude < 0.70:
        return "Strong"
    return "Very strong"


def select_numeric_features(
    data: pd.DataFrame,
    label_column: str,
) -> pd.DataFrame:
    """Return numeric features after excluding identifiers and target."""
    excluded = DEFAULT_EXCLUDED_COLUMNS | {label_column}
    candidates = data.drop(
        columns=[column for column in excluded if column in data.columns],
        errors="ignore",
    ).copy()

    numeric = pd.DataFrame(index=data.index)
    ignored = []

    for column in candidates.columns:
        converted = pd.to_numeric(candidates[column], errors="coerce")
        original_non_missing = candidates[column].notna().sum()
        converted_non_missing = converted.notna().sum()

        if (
            original_non_missing == converted_non_missing
            and converted_non_missing > 0
        ):
            numeric[column] = converted
        else:
            ignored.append(column)

    if ignored:
        print("Ignored non-numeric columns: " + ", ".join(ignored))

    if numeric.empty:
        raise ValueError("No numeric feature columns were found in the dataset.")

    return numeric


def compute_correlations(
    features: pd.DataFrame,
    target: pd.Series,
    alpha: float,
) -> pd.DataFrame:
    """Compute coefficients, p-values, and Spearman classifications."""
    rows = []

    for feature_name in features.columns:
        paired = pd.DataFrame(
            {"feature": features[feature_name], "target": target}
        ).dropna()

        values = paired["feature"]
        labels = paired["target"]

        if values.nunique() < 2 or labels.nunique() < 2:
            point_coefficient = np.nan
            point_p_value = np.nan
            spearman_coefficient = np.nan
            spearman_p_value = np.nan
        else:
            point_result = pointbiserialr(labels, values)
            spearman_result = spearmanr(values, labels)

            point_coefficient = float(point_result.statistic)
            point_p_value = float(point_result.pvalue)
            spearman_coefficient = float(spearman_result.statistic)
            spearman_p_value = float(spearman_result.pvalue)

        rows.append(
            {
                "Feature": feature_name,
                "Valid observations": len(paired),
                "Point-biserial r": point_coefficient,
                "Point-biserial p-value": point_p_value,
                "Spearman rho": spearman_coefficient,
                "Spearman p-value": spearman_p_value,
            }
        )

    results = pd.DataFrame(rows)
    results["Point-biserial adjusted p-value"] = benjamini_hochberg(
        results["Point-biserial p-value"]
    )
    results["Spearman adjusted p-value"] = benjamini_hochberg(
        results["Spearman p-value"]
    )
    results["Spearman direction"] = results["Spearman rho"].map(
        classify_direction
    )
    results["Spearman strength"] = results["Spearman rho"].map(
        classify_strength
    )
    results["Spearman statistically significant"] = (
        results["Spearman adjusted p-value"] < alpha
    )
    results["Absolute Spearman rho"] = results["Spearman rho"].abs()

    return results.sort_values(
        by=["Absolute Spearman rho", "Feature"],
        ascending=[False, True],
        na_position="last",
    ).reset_index(drop=True)


def escape_latex(value: str) -> str:
    """Escape special LaTeX characters in feature names and labels."""
    replacements = {
        "\\": r"\textbackslash{}",
        "&": r"\&",
        "%": r"\%",
        "$": r"\$",
        "#": r"\#",
        "_": r"\_",
        "{": r"\{",
        "}": r"\}",
        "~": r"\textasciitilde{}",
        "^": r"\textasciicircum{}",
    }
    return "".join(
        replacements.get(character, character) for character in value
    )


def format_number(value: float, digits: int = 3) -> str:
    """Format a numeric value using an Italian decimal comma."""
    if not np.isfinite(value):
        return "N/A"
    return f"{value:.{digits}f}".replace(".", "{,}")


def format_p_value(value: float) -> str:
    """Format an adjusted p-value for a compact LaTeX table."""
    if not np.isfinite(value):
        return "N/A"
    if value < 0.001:
        return r"$<0{,}001$"
    return format_number(value, digits=3)


def write_latex_table(
    results: pd.DataFrame,
    output_path: Path,
    alpha: float,
) -> None:
    """Write a compact table containing the main Spearman results."""
    display_results = results[
        results["Spearman statistically significant"]
        & (results["Absolute Spearman rho"] >= MINIMUM_REPORTED_SPEARMAN)
    ].copy()

    direction_translation = {
        "Positive": "Positiva",
        "Negative": "Negativa",
        "None": "Nessuna",
    }
    strength_translation = {
        "Negligible": "Trascurabile",
        "Weak": "Debole",
        "Moderate": "Moderata",
        "Strong": "Forte",
        "Very strong": "Molto forte",
        "Not available": "Non disponibile",
    }

    lines = [
        r"% Requires: \usepackage{booktabs}",
        r"\begin{table}[htbp]",
        r"\centering",
        r"\small",
        (
            r"\caption{Associazioni non trascurabili tra le feature "
            r"della Milestone~1 e la bugginess, misurate mediante "
            r"il coefficiente di Spearman. I p-value sono corretti "
            r"con la procedura di Benjamini--Hochberg.}"
        ),
        r"\label{tab:feature-bugginess-correlation}",
        r"\begin{tabular}{lrrll}",
        r"\toprule",
        (
            r"\textbf{Feature} & \textbf{$\rho$} & "
            r"\textbf{$p_{\mathrm{adj}}$} & "
            r"\textbf{Direzione} & \textbf{Intensità} \\"
        ),
        r"\midrule",
    ]

    for _, row in display_results.iterrows():
        lines.append(
            "{} & {} & {} & {} & {} \\\\".format(
                escape_latex(str(row["Feature"])),
                format_number(float(row["Spearman rho"])),
                format_p_value(float(row["Spearman adjusted p-value"])),
                direction_translation[str(row["Spearman direction"])],
                strength_translation[str(row["Spearman strength"])],
            )
        )

    if display_results.empty:
        lines.append(
            r"\multicolumn{5}{c}{Nessuna associazione soddisfa i criteri selezionati.} \\"
        )

    alpha_formatted = str(alpha).replace(".", "{,}")
    threshold_formatted = format_number(
        MINIMUM_REPORTED_SPEARMAN,
        digits=2,
    )

    lines.extend(
        [
            r"\bottomrule",
            r"\end{tabular}",
            r"\vspace{1mm}",
            (
                r"\parbox{0.95\textwidth}{\footnotesize "
                r"Sono riportate soltanto le associazioni con "
                r"$|\rho|\geq "
                + threshold_formatted
                + r"$ e $p_{\mathrm{adj}}<"
                + alpha_formatted
                + r"$. Le soglie di intensità sono utilizzate "
                r"esclusivamente a fini descrittivi.}"
            ),
            r"\end{table}",
        ]
    )

    output_path.write_text(
        "\n".join(lines) + "\n",
        encoding="utf-8",
    )


def main() -> None:
    """Load the dataset, compute correlations, and export the results."""
    args = parse_arguments()

    if not args.dataset.is_file():
        raise FileNotFoundError(f"Dataset not found: {args.dataset}")
    if not 0.0 < args.alpha < 1.0:
        raise ValueError("Alpha must be between 0 and 1.")

    separator = detect_separator(args.dataset)
    data = pd.read_csv(
        args.dataset,
        sep=separator,
        encoding="utf-8-sig",
    )

    if args.label not in data.columns:
        raise ValueError(
            f"Target column '{args.label}' not found. Available columns: "
            + ", ".join(data.columns)
        )

    target = encode_binary_label(data[args.label])
    if target.nunique() != 2:
        raise ValueError("The target column must contain both binary classes.")

    features = select_numeric_features(data, args.label)
    if len(features.columns) != EXPECTED_FEATURE_COUNT:
        raise ValueError(
            f"Expected {EXPECTED_FEATURE_COUNT} numeric features, "
            f"but found {len(features.columns)}: {list(features.columns)}"
        )

    results = compute_correlations(features, target, args.alpha)

    args.output_dir.mkdir(parents=True, exist_ok=True)
    csv_path = args.output_dir / "feature_bugginess_correlations.csv"
    latex_path = args.output_dir / "feature_bugginess_correlations.tex"

    results.to_csv(csv_path, index=False, encoding="utf-8")
    write_latex_table(results, latex_path, args.alpha)

    positive_count = int(
        (results["Spearman direction"] == "Positive").sum()
    )
    negative_count = int(
        (results["Spearman direction"] == "Negative").sum()
    )
    significant_count = int(
        results["Spearman statistically significant"].sum()
    )
    reported_count = int(
        (
            results["Spearman statistically significant"]
            & (results["Absolute Spearman rho"] >= MINIMUM_REPORTED_SPEARMAN)
        ).sum()
    )

    print(f"Rows loaded: {len(data)}")
    print(f"Buggy instances: {int(target.sum())}")
    print(f"Non-buggy instances: {int((1 - target).sum())}")
    print(f"Numeric features analyzed: {len(results)}")
    print(f"Positive Spearman correlations: {positive_count}")
    print(f"Negative Spearman correlations: {negative_count}")
    print(
        "Significant Spearman associations after adjustment: "
        f"{significant_count}"
    )
    print(f"Associations included in LaTeX table: {reported_count}")
    print(f"CSV output: {csv_path}")
    print(f"LaTeX output: {latex_path}")

    preview_columns = [
        "Feature",
        "Spearman rho",
        "Spearman adjusted p-value",
        "Spearman direction",
        "Spearman strength",
        "Point-biserial r",
    ]

    print("\nTop correlations by absolute Spearman coefficient:")
    print(results[preview_columns].head(10).to_string(index=False))


if __name__ == "__main__":
    main()
