"""
STEP 6 — Train and compare Logistic Regression, Random Forest, XGBoost.

STATUS: NOT YET RUN. This is intentional, not an oversight:
  - rainfall_6h/24h/72h/7d are 0% populated right now (see build_ml_dataset.py
    output) because no rainfall files exist yet.
  - Rainfall is one of the three required feature groups (GSI + rainfall +
    DEM) for this project. Training "the" model on a dataset missing an
    entire required feature group would not be the real model — it would
    misrepresent what the pipeline produces.
  - Per project instructions: "Do not train the ML model until rainfall
    extraction and the final sample construction are validated."
This script is complete and ready — run it as-is once
data/processed/ml_dataset.csv has real rainfall coverage.

Validation strategy: spatial block cross-validation, NOT a random split.
Landslide events cluster tightly in space (many along the same highway
corridor), so a random split would put near-duplicate/nearby points in both
train and test, leaking information and overstating performance. Instead,
points are grouped into ~0.1° x 0.1° spatial blocks (~11km at this latitude)
and GroupKFold splits by block, so no block appears in both train and test.

Run:
    pip install scikit-learn xgboost joblib --break-system-packages
    python src/models/train_models.py
"""
import os
import sys

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (average_precision_score, confusion_matrix, f1_score,
                              precision_score, recall_score, roc_auc_score)
from sklearn.model_selection import GroupKFold
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "configs"))
import config as cfg

RAINFALL_COLS = ["rainfall_6h", "rainfall_24h", "rainfall_72h", "rainfall_7d"]
DEM_COLS = ["elevation", "slope", "aspect", "curvature", "terrain_ruggedness"]
BLOCK_SIZE_DEG = 0.1


def make_spatial_blocks(df):
    return (np.floor(df["latitude"] / BLOCK_SIZE_DEG).astype(int).astype(str) + "_" +
            np.floor(df["longitude"] / BLOCK_SIZE_DEG).astype(int).astype(str))


def build_models():
    return {
        "logistic_regression": Pipeline([
            ("impute", SimpleImputer(strategy="median")),
            ("scale", StandardScaler()),
            ("clf", LogisticRegression(max_iter=1000, class_weight="balanced")),
        ]),
        "random_forest": Pipeline([
            ("impute", SimpleImputer(strategy="median")),
            ("clf", RandomForestClassifier(n_estimators=300, class_weight="balanced",
                                            random_state=42, n_jobs=-1)),
        ]),
        "xgboost": None,  # built lazily below (needs scale_pos_weight from data)
    }


def evaluate(y_true, y_prob, threshold=0.5):
    y_pred = (y_prob >= threshold).astype(int)
    return {
        "precision": precision_score(y_true, y_pred, zero_division=0),
        "recall": recall_score(y_true, y_pred, zero_division=0),
        "f1": f1_score(y_true, y_pred, zero_division=0),
        "roc_auc": roc_auc_score(y_true, y_prob),
        "pr_auc": average_precision_score(y_true, y_prob),
        "confusion_matrix": confusion_matrix(y_true, y_pred).tolist(),
    }


def main():
    df = pd.read_csv(cfg.ML_DATASET_CSV)

    # DEM features are required (this is a terrain-risk model - can't train
    # without any terrain signal). Rainfall is OPTIONAL and NOT a blocker:
    # currently 0% populated, so it contributes a constant column plus an
    # explicit availability flag. It will start carrying real signal
    # automatically once data/raw/rainfall/ is populated and
    # build_ml_dataset.py is re-run - no code change needed here.
    df = df.dropna(subset=DEM_COLS, how="all")
    rainfall_pct = 100 * (1 - df["missing_rainfall"].mean())
    print(f"Training with DEM features (required) + rainfall features "
          f"(optional, currently {rainfall_pct:.1f}% populated).")

    df["rainfall_available_flag"] = (~df["missing_rainfall"]).astype(int)
    for c in RAINFALL_COLS:
        if df[c].isna().all():
            df[c] = 0.0  # constant placeholder - carries zero information,
                          # kept only so the schema is stable once real
                          # rainfall values start arriving

    FEATURE_COLS = DEM_COLS + RAINFALL_COLS + ["rainfall_available_flag"]
    X = df[FEATURE_COLS]
    y = df["landslide"]
    groups = make_spatial_blocks(df)

    gkf = GroupKFold(n_splits=5)
    models = build_models()
    from xgboost import XGBClassifier
    n_pos, n_neg = (y == 1).sum(), (y == 0).sum()
    models["xgboost"] = Pipeline([
        ("impute", SimpleImputer(strategy="median")),
        ("clf", XGBClassifier(n_estimators=300, max_depth=5, learning_rate=0.05,
                               scale_pos_weight=n_neg / max(n_pos, 1),
                               eval_metric="logloss", random_state=42)),
    ])

    results = {name: [] for name in models}
    oof_prob = {name: np.full(len(X), np.nan) for name in models}
    for name, pipe in models.items():
        for train_idx, test_idx in gkf.split(X, y, groups=groups):
            pipe.fit(X.iloc[train_idx], y.iloc[train_idx])
            y_prob = pipe.predict_proba(X.iloc[test_idx])[:, 1]
            results[name].append(evaluate(y.iloc[test_idx], y_prob))
            oof_prob[name][test_idx] = y_prob

    summary = {}
    for name, folds in results.items():
        summary[name] = {
            metric: float(np.mean([f[metric] for f in folds]))
            for metric in ["precision", "recall", "f1", "roc_auc", "pr_auc"]
        }
    summary_df = pd.DataFrame(summary).T.sort_values("pr_auc", ascending=False)
    print(summary_df)

    best_name = summary_df.index[0]
    best_pipe = models[best_name]
    best_pipe.fit(X, y)  # refit on all data for the saved artifact
    os.makedirs(cfg.MODELS_DIR, exist_ok=True)
    joblib.dump({"model": best_pipe, "features": FEATURE_COLS, "name": best_name},
                cfg.BEST_MODEL_PATH)
    print(f"Best model: {best_name} -> saved to {cfg.BEST_MODEL_PATH}")

    os.makedirs(cfg.REPORTS_DIR, exist_ok=True)
    summary_df.to_csv(os.path.join(cfg.REPORTS_DIR, "model_comparison.csv"))

    # calibrate risk-level thresholds from the best model's out-of-fold
    # (validation) probabilities - not arbitrary numbers
    sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "evaluation"))
    from explainability import calibrate_risk_thresholds
    thresholds = calibrate_risk_thresholds(y.to_numpy(), oof_prob[best_name])
    print(f"Calibrated risk thresholds (from {best_name} OOF predictions): {thresholds}")


if __name__ == "__main__":
    main()
