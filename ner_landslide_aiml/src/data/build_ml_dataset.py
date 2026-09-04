"""
STEP 5 — Assemble the final ML dataset: positives (real GSI events) + negatives
(sampled), with DEM features (available now) and rainfall features (PENDING —
see src/features/rainfall_extraction.py; will populate automatically once
files are placed in data/raw/rainfall/, no code change needed here).

Output: data/processed/ml_dataset.csv

Run:
    python src/data/build_ml_dataset.py
"""
import os
import sys

import numpy as np
import pandas as pd

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "configs"))
import config as cfg
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "features"))
from rainfall_extraction import extract_rainfall_features

FINAL_COLUMNS = [
    "event_id", "source", "latitude", "longitude", "event_date", "date_precision", "state",
    "rainfall_6h", "rainfall_24h", "rainfall_72h", "rainfall_7d", "rainfall_available",
    "elevation", "slope", "aspect", "curvature", "terrain_ruggedness", "dem_available",
    "landslide",
]


def main():
    pos_path = cfg.NER_DEM_MERGED_CSV if os.path.exists(cfg.NER_DEM_MERGED_CSV) else cfg.NER_DEM_CSV
    pos = pd.read_csv(pos_path)
    if "source" not in pos.columns:
        pos["source"] = "GSI"
    pos["landslide"] = 1

    neg_path = os.path.join(cfg.INTERIM_DIR, "negative_samples.csv")
    neg = pd.read_csv(neg_path)
    neg["source"] = "SAMPLED"

    combined = pd.concat([pos, neg], ignore_index=True, sort=False)

    # rainfall: currently returns all-NaN / rainfall_available=False since no
    # files exist yet in data/raw/rainfall/ — this is expected, not a bug.
    combined = extract_rainfall_features(combined)

    combined["missing_dem"] = ~combined["dem_available"].astype(bool)
    combined["missing_rainfall"] = ~combined["rainfall_available"].astype(bool)

    final = combined[[c for c in FINAL_COLUMNS if c in combined.columns] +
                      ["missing_dem", "missing_rainfall"]]

    os.makedirs(cfg.PROCESSED_DIR, exist_ok=True)
    final.to_csv(cfg.ML_DATASET_CSV, index=False)

    n = len(final)
    print(f"Total rows: {n}  (positives={int((final['landslide']==1).sum())}, "
          f"negatives={int((final['landslide']==0).sum())})")
    print(f"Rows with usable DEM features:      {int((~final['missing_dem']).sum())} "
          f"({(~final['missing_dem']).mean():.1%})")
    print(f"Rows with usable rainfall features:  {int((~final['missing_rainfall']).sum())} "
          f"({(~final['missing_rainfall']).mean():.1%})  <- PENDING, expected 0 until rainfall files are added")
    print(f"Saved -> {cfg.ML_DATASET_CSV}")


if __name__ == "__main__":
    main()
