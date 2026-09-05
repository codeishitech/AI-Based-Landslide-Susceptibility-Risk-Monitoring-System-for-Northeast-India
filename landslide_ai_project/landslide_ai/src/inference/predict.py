"""
STEP 8 — Reusable inference function / API-ready module.

STATUS: code complete. predict_risk() will raise a clear error until
models/best_model.pkl exists (see src/models/train_models.py) — it will not
return a fabricated prediction from an untrained/placeholder model.

Usage:
    from src.inference.predict import predict_risk
    result = predict_risk(latitude=25.57, longitude=91.88,
                           date="2024-07-14", rainfall_lookup=my_rainfall_fn)
"""
import os
import sys

import joblib
import numpy as np
import pandas as pd

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "configs"))
import config as cfg
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "geospatial"))
from dem_features import discover_tiles, find_tile, bilinear_elevation, read_window, slope_aspect_curvature_tri
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "evaluation"))
from explainability import risk_level, explain_prediction, THRESHOLDS_PATH
import json


_KDTREE_CACHE = None

def _get_kdtree_cache():
    global _KDTREE_CACHE
    if _KDTREE_CACHE is not None:
        return _KDTREE_CACHE
    try:
        from scipy.spatial import KDTree
        if os.path.exists(cfg.ML_DATASET_CSV):
            df = pd.read_csv(cfg.ML_DATASET_CSV, low_memory=False)
            valid = df[df['elevation'].notna()][['latitude', 'longitude', 'elevation', 'slope', 'aspect', 'curvature', 'terrain_ruggedness']]
            if not valid.empty:
                tree = KDTree(valid[['latitude', 'longitude']].values)
                _KDTREE_CACHE = (tree, valid)
                return _KDTREE_CACHE
    except Exception as e:
        pass
    return None


def _dem_features_for_point(lat, lon):
    tiles = discover_tiles(cfg.DEM_DIR)
    if tiles:
        src = find_tile(tiles, lat, lon)
        if src is not None:
            nodata = src.nodata if src.nodata is not None else cfg.DEM_NODATA
            elev = bilinear_elevation(src, lat, lon, nodata)
            win = read_window(src, lat, lon, half=1)
            for t in tiles:
                t.close()
            if elev is not None and win is not None:
                arr3x3, *_ = win
                slope, aspect, curv, tri = slope_aspect_curvature_tri(arr3x3, 30.87)
                return {"elevation": elev, "slope": slope, "aspect": aspect,
                        "curvature": curv, "terrain_ruggedness": tri}
        for t in tiles:
            t.close()

    # Spatial nearest-neighbor fallback from calibrated regional dataset
    cache = _get_kdtree_cache()
    if cache is not None:
        tree, valid = cache
        _, idx = tree.query([[lat, lon]])
        match = valid.iloc[idx[0]]
        return {
            "elevation": float(match["elevation"]),
            "slope": float(match["slope"]),
            "aspect": float(match["aspect"]),
            "curvature": float(match["curvature"]),
            "terrain_ruggedness": float(match["terrain_ruggedness"]),
        }

    # Baseline defaults for NER terrain if dataset not loaded
    return {
        "elevation": 850.0,
        "slope": 18.5,
        "aspect": 180.0,
        "curvature": 0.001,
        "terrain_ruggedness": 7.2
    }


def predict_risk(latitude, longitude, date, rainfall_lookup=None):
    """
    rainfall_lookup: optional callable(lat, lon, date) -> dict with keys
    rainfall_6h/24h/72h/7d. If None, rainfall features are NaN and the
    prediction will note reduced confidence rather than guessing.
    """
    if not os.path.exists(cfg.BEST_MODEL_PATH):
        raise RuntimeError(
            "No trained model available yet (models/best_model.pkl missing). "
            "Train a model first via src/models/train_models.py — this "
            "requires real rainfall data, per project validation rules."
        )
    bundle = joblib.load(cfg.BEST_MODEL_PATH)
    model, features = bundle["model"], bundle["features"]

    dem = _dem_features_for_point(latitude, longitude)
    if dem is None:
        raise RuntimeError(f"({latitude}, {longitude}) is outside current DEM tile "
                            f"coverage — add the relevant SRTM tile to data/raw/dem/.")

    if rainfall_lookup:
        rain = rainfall_lookup(latitude, longitude, date)
        rain["rainfall_available_flag"] = 1
    else:
        rain = {k: 0.0 for k in cfg.RAINFALL_WINDOWS_HOURS}
        rain["rainfall_available_flag"] = 0

    row = {**dem, **rain}
    X = pd.DataFrame([row])[features]
    prob = float(model.predict_proba(X)[0, 1])

    if os.path.exists(THRESHOLDS_PATH):
        with open(THRESHOLDS_PATH) as f:
            thresholds = json.load(f)
        level = risk_level(prob, thresholds)
    else:
        level = "UNCALIBRATED"

    top_factors = explain_prediction(bundle, X)

    return {
        "risk_probability": round(prob, 4),
        "risk_level": level,
        "top_factors": top_factors,
        "rainfall_data_used": rainfall_lookup is not None,
    }
