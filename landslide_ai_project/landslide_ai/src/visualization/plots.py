"""
STEP 9 — Visualizations for the SIH demo. Kept separate from core pipeline
logic. Functions that need rainfall/model outputs check for them and skip
gracefully with a printed note instead of plotting fabricated data.

Run:
    pip install matplotlib --break-system-packages
    python src/visualization/plots.py
"""
import os
import sys

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "configs"))
import config as cfg


def plot_landslide_locations(save_path):
    path = cfg.NER_DEM_MERGED_CSV if os.path.exists(cfg.NER_DEM_MERGED_CSV) else cfg.NER_CLEAN_CSV
    df = pd.read_csv(path)
    df = df[df["coord_valid"]]
    fig, ax = plt.subplots(figsize=(7, 7))
    for source, grp in df.groupby("source" if "source" in df.columns else "state"):
        ax.scatter(grp["longitude"], grp["latitude"], s=4, alpha=0.5, label=source)
    ax.set_xlabel("Longitude"); ax.set_ylabel("Latitude")
    ax.set_title(f"Landslide events in NER — GSI (primary) + GLC (secondary), n={len(df)}")
    ax.legend(markerscale=3, fontsize=7, loc="best")
    fig.tight_layout(); fig.savefig(save_path, dpi=150); plt.close(fig)
    print(f"Saved -> {save_path}")


def plot_slope_distribution(save_path):
    path = cfg.NER_DEM_MERGED_CSV if os.path.exists(cfg.NER_DEM_MERGED_CSV) else cfg.NER_DEM_CSV
    df = pd.read_csv(path)
    df = df[df["dem_available"]]
    if df.empty:
        print("Skipped slope distribution — no DEM-covered records.")
        return
    fig, ax = plt.subplots(figsize=(6, 4))
    ax.hist(df["slope"].dropna(), bins=30, color="#8B5E3C")
    ax.set_xlabel("Slope (degrees)"); ax.set_ylabel("Count")
    ax.set_title(f"Slope at landslide sites with DEM coverage (n={len(df)})")
    fig.tight_layout(); fig.savefig(save_path, dpi=150); plt.close(fig)
    print(f"Saved -> {save_path}")


def plot_feature_importance(save_path):
    import joblib
    if not os.path.exists(cfg.BEST_MODEL_PATH):
        print("Skipped feature importance — no trained model yet."); return
    bundle = joblib.load(cfg.BEST_MODEL_PATH)
    clf = bundle["model"].named_steps["clf"]
    importances = getattr(clf, "feature_importances_", None)
    if importances is None:
        print("Skipped feature importance — model has no feature_importances_."); return
    fig, ax = plt.subplots(figsize=(6, 4))
    order = np.argsort(importances)
    feats = np.array(bundle["features"])[order]
    ax.barh(feats, importances[order])
    ax.set_title(f"Feature importance ({bundle['name']})")
    fig.tight_layout(); fig.savefig(save_path, dpi=150); plt.close(fig)
    print(f"Saved -> {save_path}")


def plot_rainfall_distribution(save_path):
    if not os.path.exists(cfg.ML_DATASET_CSV):
        print("Skipped rainfall distribution — no ML dataset yet."); return
    df = pd.read_csv(cfg.ML_DATASET_CSV)
    if df["missing_rainfall"].all():
        print("Skipped rainfall distribution — PENDING, no rainfall data extracted yet (rainfall is optional, not a blocker, for the rest of the pipeline)."); return
    fig, ax = plt.subplots(figsize=(6, 4))
    ax.hist(df.loc[~df["missing_rainfall"], "rainfall_72h"].dropna(), bins=30)
    ax.set_xlabel("72h antecedent rainfall (mm)"); ax.set_ylabel("Count")
    fig.tight_layout(); fig.savefig(save_path, dpi=150); plt.close(fig)
    print(f"Saved -> {save_path}")


def plot_model_performance(save_path):
    path = os.path.join(cfg.REPORTS_DIR, "model_comparison.csv")
    if not os.path.exists(path):
        print("Skipped model performance plot — PENDING, no trained models yet."); return
    df = pd.read_csv(path, index_col=0)
    fig, ax = plt.subplots(figsize=(7, 4))
    df[["precision", "recall", "f1", "roc_auc", "pr_auc"]].plot(kind="bar", ax=ax)
    ax.set_ylabel("Score"); ax.set_title("Model comparison")
    fig.tight_layout(); fig.savefig(save_path, dpi=150); plt.close(fig)
    print(f"Saved -> {save_path}")


if __name__ == "__main__":
    out_dir = os.path.join(cfg.REPORTS_DIR, "figures")
    os.makedirs(out_dir, exist_ok=True)
    plot_landslide_locations(os.path.join(out_dir, "landslide_locations.png"))
    plot_slope_distribution(os.path.join(out_dir, "slope_distribution.png"))
    plot_rainfall_distribution(os.path.join(out_dir, "rainfall_distribution.png"))
    plot_model_performance(os.path.join(out_dir, "model_performance.png"))
    plot_feature_importance(os.path.join(out_dir, "feature_importance.png"))
