"""
Merge primary (GSI) + secondary (GLC) landslide sources into one positive
event pool. GLC events within GLC_DEDUPE_DIST_KM and the same year of an
existing GSI event are treated as the same real-world event (kept once,
source='GSI', flagged also_in_glc=True) rather than double-counted.

Output: data/processed/ner_landslides_with_dem_merged.csv

Run:
    python src/data/merge_sources.py
"""
import os
import sys

import numpy as np
import pandas as pd

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "configs"))
import config as cfg
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "geospatial"))
from dem_features import discover_tiles, find_tile, bilinear_elevation, read_window, slope_aspect_curvature_tri
from negative_sampling import haversine_km


def add_dem_features(df, tiles):
    cols = {"elevation": [], "slope": [], "aspect": [], "curvature": [],
            "terrain_ruggedness": [], "dem_available": [], "dem_issue": []}
    for _, row in df.iterrows():
        lat, lon, ok = row["latitude"], row["longitude"], row["coord_valid"]
        if not ok or pd.isna(lat) or pd.isna(lon):
            cols["elevation"].append(np.nan); cols["slope"].append(np.nan)
            cols["aspect"].append(np.nan); cols["curvature"].append(np.nan)
            cols["terrain_ruggedness"].append(np.nan)
            cols["dem_available"].append(False); cols["dem_issue"].append("invalid_coordinate")
            continue
        src = find_tile(tiles, lat, lon)
        if src is None:
            cols["elevation"].append(np.nan); cols["slope"].append(np.nan)
            cols["aspect"].append(np.nan); cols["curvature"].append(np.nan)
            cols["terrain_ruggedness"].append(np.nan)
            cols["dem_available"].append(False); cols["dem_issue"].append("outside_current_dem_coverage")
            continue
        nodata = src.nodata if src.nodata is not None else cfg.DEM_NODATA
        elev = bilinear_elevation(src, lat, lon, nodata)
        win = read_window(src, lat, lon, half=1)
        if elev is None or win is None:
            cols["elevation"].append(elev if elev is not None else np.nan)
            cols["slope"].append(np.nan); cols["aspect"].append(np.nan)
            cols["curvature"].append(np.nan); cols["terrain_ruggedness"].append(np.nan)
            cols["dem_available"].append(False); cols["dem_issue"].append("near_tile_edge_or_nodata")
            continue
        arr3x3, *_ = win
        slope, aspect, curv, tri = slope_aspect_curvature_tri(arr3x3, 30.87)
        cols["elevation"].append(elev); cols["slope"].append(slope)
        cols["aspect"].append(aspect); cols["curvature"].append(curv)
        cols["terrain_ruggedness"].append(tri)
        cols["dem_available"].append(True); cols["dem_issue"].append(None)
    for k, v in cols.items():
        df[k] = v
    return df


def main():
    gsi = pd.read_csv(cfg.NER_DEM_CSV)
    gsi["source"] = "GSI"

    glc = pd.read_csv(cfg.GLC_NER_CLEAN_CSV)
    tiles = discover_tiles(cfg.DEM_DIR)
    glc = add_dem_features(glc, tiles)
    for t in tiles:
        t.close()

    # dedupe GLC against GSI: same approx location + same year -> same event
    gsi_lat = gsi["latitude"].to_numpy(dtype=float)
    gsi_lon = gsi["longitude"].to_numpy(dtype=float)
    gsi_year = pd.to_datetime(gsi["event_date"], errors="coerce").dt.year.to_numpy()
    valid_gsi = ~np.isnan(gsi_lat) & ~np.isnan(gsi_lon)

    is_dupe = []
    for _, row in glc.iterrows():
        if pd.isna(row["latitude"]) or pd.isna(row["longitude"]):
            is_dupe.append(False); continue
        glc_year = pd.to_datetime(row["event_date"], errors="coerce").year
        d = haversine_km(row["latitude"], row["longitude"], gsi_lat[valid_gsi], gsi_lon[valid_gsi])
        years = gsi_year[valid_gsi]
        close = d <= cfg.GLC_DEDUPE_DIST_KM
        same_year = years == glc_year if not pd.isna(glc_year) else np.zeros_like(close, dtype=bool)
        is_dupe.append(bool(np.any(close & same_year)))

    glc["duplicate_of_gsi"] = is_dupe
    glc_unique = glc[~glc["duplicate_of_gsi"]].copy()

    n_glc_total, n_glc_dupe = len(glc), int(sum(is_dupe))
    print(f"GLC secondary records: {n_glc_total}, matched to existing GSI events (excluded): {n_glc_dupe}, "
          f"new unique GLC events added: {len(glc_unique)}")

    common_cols = ["event_id", "source", "state", "district", "slide_name", "nh_sh_location",
                   "latitude", "longitude", "coord_valid", "coord_issue", "coord_collision",
                   "event_date", "date_precision", "history_raw", "material_involved", "movement_type",
                   "elevation", "slope", "aspect", "curvature", "terrain_ruggedness",
                   "dem_available", "dem_issue"]
    for c in common_cols:
        if c not in gsi.columns:
            gsi[c] = None
        if c not in glc_unique.columns:
            glc_unique[c] = None

    merged = pd.concat([gsi[common_cols], glc_unique[common_cols]], ignore_index=True)
    merged.to_csv(cfg.NER_DEM_MERGED_CSV, index=False)

    print(f"Merged positive pool: {len(merged)} (GSI={int((merged['source']=='GSI').sum())}, "
          f"GLC-unique={int((merged['source']=='GLC').sum())})")
    print(f"DEM available in merged pool: {int(merged['dem_available'].sum())} "
          f"({merged['dem_available'].mean():.1%})")
    print(f"Saved -> {cfg.NER_DEM_MERGED_CSV}")


if __name__ == "__main__":
    main()
