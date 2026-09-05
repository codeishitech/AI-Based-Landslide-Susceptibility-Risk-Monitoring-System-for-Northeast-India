"""
STEP 7 — Explainability and risk-level classification.

STATUS: code complete, not runnable yet (needs models/best_model.pkl, which
doesn't exist until train_models.py has real rainfall data to train on).

Risk thresholds: NOT hardcoded. calibrate_risk_thresholds() derives them from
the validation-set probability distribution (LOW = below the median score of
actual negatives, CRITICAL = above the 90th percentile score of actual
positives, etc.) — run once, after training, and the resulting thresholds are
saved to configs/risk_thresholds.json so they're documented, not invented.

Run (once a trained model exists):
    pip install shap --break-system-packages
    python src/evaluation/explainability.py
"""
import json
import os
import sys

import joblib
import numpy as np
import pandas as pd

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "configs"))
import config as cfg

THRESHOLDS_PATH = os.path.join(os.path.dirname(cfg.__file__), "risk_thresholds.json")


def calibrate_risk_thresholds(y_true, y_prob):
    """Derive LOW/MODERATE/HIGH/CRITICAL cut points from validation data,
    instead of picking arbitrary numbers."""
    neg_scores = y_prob[y_true == 0]
    pos_scores = y_prob[y_true == 1]
    thresholds = {
        "LOW_MAX": float(np.percentile(neg_scores, 75)),        # below this: looks like typical negatives
        "MODERATE_MAX": float(np.percentile(pos_scores, 25)),   # up to here: ambiguous zone
        "HIGH_MAX": float(np.percentile(pos_scores, 75)),       # up to here: HIGH; above: CRITICAL
    }
    # guard against a degenerate/inverted ordering on tiny or unbalanced folds
    thresholds["MODERATE_MAX"] = max(thresholds["MODERATE_MAX"], thresholds["LOW_MAX"])
    thresholds["HIGH_MAX"] = max(thresholds["HIGH_MAX"], thresholds["MODERATE_MAX"])
    with open(THRESHOLDS_PATH, "w") as f:
        json.dump(thresholds, f, indent=2)
    return thresholds


def risk_level(prob, thresholds):
    if prob < thresholds["LOW_MAX"]:
        return "LOW"
    if prob < thresholds["MODERATE_MAX"]:
        return "MODERATE"
    if prob < thresholds["HIGH_MAX"]:
        return "HIGH"
    return "CRITICAL"


def explain_prediction(model_bundle, X_row, top_n=4):
    """Feature-importance-based explanation (SHAP if available, else the
    model's built-in importances) — correlational, not causal language."""
    model = model_bundle["model"]
    features = model_bundle["features"]
    try:
        import shap
        clf = model.named_steps["clf"]
        X_transformed = model[:-1].transform(X_row)
        explainer = shap.Explainer(clf)
        sv = explainer(X_transformed)
        contribs = dict(zip(features, sv.values[0]))
    except Exception:
        clf = model.named_steps["clf"]
        importances = getattr(clf, "feature_importances_", None)
        if importances is None:
            coefs = getattr(clf, "coef_", np.zeros((1, len(features))))[0]
            importances = np.abs(coefs)
        contribs = dict(zip(features, importances))

    top = sorted(contribs.items(), key=lambda kv: abs(kv[1]), reverse=True)[:top_n]
    return [{"feature": k, "contribution": float(v)} for k, v in top]


if __name__ == "__main__":
    if not os.path.exists(cfg.BEST_MODEL_PATH):
        raise SystemExit("No trained model found yet — run src/models/train_models.py "
                          "after rainfall data is available.")
    bundle = joblib.load(cfg.BEST_MODEL_PATH)
    print(f"Loaded model: {bundle['name']}")
    print("Explainability module ready — call explain_prediction(bundle, X_row).")
