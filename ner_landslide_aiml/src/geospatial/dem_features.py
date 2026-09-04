"""
STEP 2.4 — DEM feature extraction (elevation, slope, aspect, curvature,
terrain ruggedness index) for every cleaned NER landslide event.

Auto-discovers every raster in data/raw/dem/ (.tif/.tiff/.hgt) at import time,
so dropping additional SRTM tiles into that folder later requires NO code
change — just re-run this script.

For each point:
  - find the tile whose bounds contain it (if none, mark unavailable — no
    fabrication)
  - read a small window (5x5 cells) around the point so slope/aspect/
    curvature/TRI can be computed from real neighboring elevation values
  - elevation: bilinear-interpolated value at the exact point
  - slope/aspect: Horn's method (standard, used by GDAL/QGIS) on the 3x3
    cells centered on the point's nearest cell
  - curvature: mean profile curvature via second-derivative of the 3x3 window
  - TRI (terrain ruggedness index): mean absolute elevation difference to the
    8 surrounding cells

Input : data/processed/ner_landslides_clean.csv
Output: data/processed/ner_landslides_with_dem.csv

Run:
    pip install rasterio --break-system-packages
    python src/geospatial/dem_features.py
"""
import glob
import os
import sys

import numpy as np
import pandas as pd
import rasterio
from rasterio.windows import Window

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "configs"))
import config as cfg


def discover_tiles(dem_dir):
    paths = []
    for ext in ("*.tif", "*.tiff", "*.hgt"):
        paths.extend(glob.glob(os.path.join(dem_dir, ext)))
    tiles = []
    for p in paths:
        try:
            src = rasterio.open(p)
        except Exception as e:
            print(f"WARNING: could not open {p}: {e}")
            continue
        tiles.append(src)
    return tiles


def find_tile(tiles, lat, lon):
    for src in tiles:
        b = src.bounds
        if b.left <= lon <= b.right and b.bottom <= lat <= b.top:
            return src
    return None


def read_window(src, lat, lon, half=2):
    row, col = src.index(lon, lat)
    size = half * 2 + 1
    row0, col0 = row - half, col - half
    if row0 < 0 or col0 < 0 or row0 + size > src.height or col0 + size > src.width:
        return None  # too close to tile edge for a full window
    win = Window(col0, row0, size, size)
    arr = src.read(1, window=win).astype(float)
    nodata = src.nodata if src.nodata is not None else cfg.DEM_NODATA
    if np.any(arr == nodata):
        return None
    return arr, row, col, row0, col0


def bilinear_elevation(src, lat, lon, nodata):
    """Bilinear interpolation using the 4 surrounding cell centers."""
    row_f, col_f = src.index(lon, lat, op=float)
    r0, c0 = int(np.floor(row_f)), int(np.floor(col_f))
    if r0 < 0 or c0 < 0 or r0 + 1 >= src.height or c0 + 1 >= src.width:
        return None
    win = Window(c0, r0, 2, 2)
    block = src.read(1, window=win).astype(float)
    if np.any(block == nodata):
        return None
    dr, dc = row_f - r0, col_f - c0
    top = block[0, 0] * (1 - dc) + block[0, 1] * dc
    bot = block[1, 0] * (1 - dc) + block[1, 1] * dc
    return float(top * (1 - dr) + bot * dr)


def slope_aspect_curvature_tri(window3x3, cellsize_m):
    """window3x3: 3x3 array, [z1..z9] row-major, Horn's method."""
    z1, z2, z3, z4, z5, z6, z7, z8, z9 = window3x3.flatten()
    dzdx = ((z3 + 2 * z6 + z9) - (z1 + 2 * z4 + z7)) / (8 * cellsize_m)
    dzdy = ((z7 + 2 * z8 + z9) - (z1 + 2 * z2 + z3)) / (8 * cellsize_m)
    slope_rad = np.arctan(np.sqrt(dzdx ** 2 + dzdy ** 2))
    slope_deg = np.degrees(slope_rad)

    aspect_rad = np.arctan2(dzdy, -dzdx)
    aspect_deg = np.degrees(aspect_rad)
    aspect_deg = (90.0 - aspect_deg) % 360.0  # convert to compass bearing

    # simple profile curvature proxy: second derivative in x and y
    d2zdx2 = (z4 - 2 * z5 + z6) / (cellsize_m ** 2)
    d2zdy2 = (z2 - 2 * z5 + z8) / (cellsize_m ** 2)
    curvature = float(d2zdx2 + d2zdy2)

    neighbors = np.array([z1, z2, z3, z4, z6, z7, z8, z9])
    tri = float(np.mean(np.abs(neighbors - z5)))

    return float(slope_deg), float(aspect_deg), curvature, tri


def main():
    tiles = discover_tiles(cfg.DEM_DIR)
    print(f"Discovered {len(tiles)} DEM tile(s) in {cfg.DEM_DIR}:")
    for t in tiles:
        print(f"  {os.path.basename(t.name)}  bounds={t.bounds}  res={t.res}")

    df = pd.read_csv(cfg.NER_CLEAN_CSV)
    # only attempt DEM extraction where we have a usable coordinate
    results = {"elevation": [], "slope": [], "aspect": [], "curvature": [],
               "terrain_ruggedness": [], "dem_available": [], "dem_issue": []}

    for _, row in df.iterrows():
        lat, lon, coord_ok = row["latitude"], row["longitude"], row["coord_valid"]
        if not coord_ok or pd.isna(lat) or pd.isna(lon):
            results["elevation"].append(np.nan); results["slope"].append(np.nan)
            results["aspect"].append(np.nan); results["curvature"].append(np.nan)
            results["terrain_ruggedness"].append(np.nan)
            results["dem_available"].append(False); results["dem_issue"].append("invalid_coordinate")
            continue

        src = find_tile(tiles, lat, lon)
        if src is None:
            results["elevation"].append(np.nan); results["slope"].append(np.nan)
            results["aspect"].append(np.nan); results["curvature"].append(np.nan)
            results["terrain_ruggedness"].append(np.nan)
            results["dem_available"].append(False); results["dem_issue"].append("outside_current_dem_coverage")
            continue

        nodata = src.nodata if src.nodata is not None else cfg.DEM_NODATA
        elev = bilinear_elevation(src, lat, lon, nodata)
        win = read_window(src, lat, lon, half=1)  # 3x3 for slope/aspect/curv/TRI

        if elev is None or win is None:
            results["elevation"].append(elev if elev is not None else np.nan)
            results["slope"].append(np.nan); results["aspect"].append(np.nan)
            results["curvature"].append(np.nan); results["terrain_ruggedness"].append(np.nan)
            results["dem_available"].append(elev is not None)
            results["dem_issue"].append("near_tile_edge_or_nodata")
            continue

        arr3x3, *_ = win
        cellsize_m = 30.87  # ~1 arc-second at this latitude band (SRTM1 nominal)
        slope, aspect, curv, tri = slope_aspect_curvature_tri(arr3x3, cellsize_m)

        results["elevation"].append(elev)
        results["slope"].append(slope)
        results["aspect"].append(aspect)
        results["curvature"].append(curv)
        results["terrain_ruggedness"].append(tri)
        results["dem_available"].append(True)
        results["dem_issue"].append(None)

    for col, vals in results.items():
        df[col] = vals

    df.to_csv(cfg.NER_DEM_CSV, index=False)

    n = len(df)
    n_avail = int(df["dem_available"].sum())
    print(f"\nTotal NER records:            {n}")
    print(f"DEM features extracted:       {n_avail} ({n_avail/n:.1%})")
    print(f"DEM unavailable, by reason:")
    print(df.loc[~df["dem_available"], "dem_issue"].value_counts())
    print(f"Saved -> {cfg.NER_DEM_CSV}")

    for t in tiles:
        t.close()


if __name__ == "__main__":
    main()
