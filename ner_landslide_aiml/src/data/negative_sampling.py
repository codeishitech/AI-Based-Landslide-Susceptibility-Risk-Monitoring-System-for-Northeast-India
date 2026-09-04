"""
STEP 4 — Negative (non-landslide) sample generation.

Approach (documented, not just implemented):
  - Spatial constraint: candidate points are drawn uniformly at random from
    inside the DEM tiles we actually have coverage for (sampling outside
    covered tiles would be pointless — we couldn't extract terrain features
    for them either). This means negatives are currently confined to the same
    small patches as our positive DEM coverage, NOT the whole NER — a real
    limitation, documented below and in the validation report.
  - Each candidate must be >= NEG_MIN_DIST_KM (config) from every known
    positive event, so we don't accidentally sample right next to a real
    landslide and call it "safe."
  - Temporal assignment: each negative gets a random date drawn from the same
    calendar-day range as the day-precision positive events, so that once
    rainfall is extracted, positives and negatives are drawn from a comparable
    time period rather than negatives all being e.g. "no rainfall ever."
  - Ratio: NEG_POS_RATIO negatives per positive (configurable in config.py).

Known limitations (do not oversell this to the SIH panel as ground truth):
  - "No recorded landslide" is not the same as "provably safe" — the GSI
    inventory is a record of KNOWN events, not an exhaustive census, so some
    negative points may in fact be unrecorded landslide sites (label noise).
  - Negatives are currently confined to DEM-covered patches (~2 of the 4
    tiles' worth of area), which may not be representative of full NER
    terrain diversity until more SRTM tiles are added.

Input : data/processed/ner_landslides_with_dem.csv (for positive locations
        and DEM tile coverage), configs/config.py
Output: appends negatives (with DEM features, rainfall pending) — combined
        into data/processed/ml_dataset.csv by build_ml_dataset.py

Run:
    python src/data/negative_sampling.py
"""
import glob
import os
import sys

import numpy as np
import pandas as pd
import rasterio

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "configs"))
import config as cfg
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "geospatial"))
from dem_features import discover_tiles, find_tile, bilinear_elevation, read_window, slope_aspect_curvature_tri


def haversine_km(lat1, lon1, lat2, lon2):
    R = 6371.0
    p1, p2 = np.radians(lat1), np.radians(lat2)
    dphi = np.radians(lat2 - lat1)
    dlmb = np.radians(lon2 - lon1)
    a = np.sin(dphi / 2) ** 2 + np.cos(p1) * np.cos(p2) * np.sin(dlmb / 2) ** 2
    return 2 * R * np.arcsin(np.sqrt(a))


def main():
    np.random.seed(cfg.NEG_RANDOM_SEED)

    pos_path = cfg.NER_DEM_MERGED_CSV if os.path.exists(cfg.NER_DEM_MERGED_CSV) else cfg.NER_DEM_CSV
    pos = pd.read_csv(pos_path)
    pos_covered = pos[pos["dem_available"]].copy()
    n_pos = len(pos)
    n_target = int(round(n_pos * cfg.NEG_POS_RATIO))

    tiles = discover_tiles(cfg.DEM_DIR)
    if not tiles:
        raise RuntimeError("No DEM tiles found — cannot sample negatives without terrain coverage.")

    # date pool: reuse day-precision positive dates so negatives span a
    # comparable time period once rainfall is joined
    day_dates = pos.loc[pos["date_precision"] == "day", "event_date"].dropna().tolist()

    pos_lat = pos["latitude"].to_numpy(dtype=float)
    pos_lon = pos["longitude"].to_numpy(dtype=float)
    valid_pos_mask = ~np.isnan(pos_lat) & ~np.isnan(pos_lon)
    pos_lat, pos_lon = pos_lat[valid_pos_mask], pos_lon[valid_pos_mask]

    negatives = []
    attempts = 0
    max_attempts = n_target * 200
    while len(negatives) < n_target and attempts < max_attempts:
        attempts += 1
        src = tiles[np.random.randint(len(tiles))]
        b = src.bounds
        lat = np.random.uniform(b.bottom, b.top)
        lon = np.random.uniform(b.left, b.right)

        d = haversine_km(lat, lon, pos_lat, pos_lon)
        if d.min() < cfg.NEG_MIN_DIST_KM:
            continue

        nodata = src.nodata if src.nodata is not None else cfg.DEM_NODATA
        elev = bilinear_elevation(src, lat, lon, nodata)
        win = read_window(src, lat, lon, half=1)
        if elev is None or win is None:
            continue
        arr3x3, *_ = win
        slope, aspect, curv, tri = slope_aspect_curvature_tri(arr3x3, 30.87)

        event_date = np.random.choice(day_dates) if day_dates else None

        negatives.append({
            "event_id": f"NEG_{len(negatives)+1:05d}",
            "state": None, "district": None, "slide_name": None,
            "nh_sh_location": None,
            "latitude": lat, "longitude": lon,
            "coord_valid": True, "coord_issue": None, "coord_collision": False,
            "event_date": event_date,
            "date_precision": "day" if event_date else "none",
            "history_raw": None, "material_involved": None, "movement_type": None,
            "elevation": elev, "slope": slope, "aspect": aspect,
            "curvature": curv, "terrain_ruggedness": tri,
            "dem_available": True, "dem_issue": None,
            "landslide": 0,
        })

    for t in tiles:
        t.close()

    print(f"Target negatives: {n_target}  Generated: {len(negatives)}  (attempts: {attempts})")
    if len(negatives) < n_target:
        print("WARNING: could not reach target ratio within DEM-covered area "
              "and min-distance constraint — increase attempts or relax "
              "NEG_MIN_DIST_KM if this persists.")

    neg_df = pd.DataFrame(negatives)
    out_path = os.path.join(cfg.INTERIM_DIR, "negative_samples.csv")
    neg_df.to_csv(out_path, index=False)
    print(f"Saved -> {out_path}")


if __name__ == "__main__":
    main()
