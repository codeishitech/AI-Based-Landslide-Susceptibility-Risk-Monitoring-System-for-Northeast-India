"""
STEP 3 — NASA GPM IMERG rainfall feature extraction.

STATUS: PENDING — data/raw/rainfall/ is currently empty. This module is
complete and will run automatically the moment NetCDF/HDF5 GPM IMERG files
are placed there (2007-2016 or the planned 2015-01-01..2025-09-30 extension —
no code change needed either way, it reads whatever time range is present in
the files). Until then, calling extract_rainfall_features() returns all-NaN
features with rainfall_available=False for every row, which is exactly what
build_ml_dataset.py currently stores — nothing is fabricated.

How it will work once files exist:
  1. Auto-detect the precipitation variable by scanning for common GPM IMERG
     names (precipitationCal, precipitation, precip, HQprecipitation) rather
     than assuming one.
  2. Auto-detect the lat/lon/time dimension names (GPM files commonly use
     lat/lon/time, but this doesn't hardcode-assume it).
  3. For a given event (lat, lon, event_datetime), find the nearest grid cell
     and sum precipitation over the STRICTLY PRECEDING window
     (event_datetime - window, event_datetime] — never using timestamps at or
     after the event, so there is no future-leakage.
  4. Events with date_precision != 'day' (no usable timestamp) or falling
     outside the rainfall file's time coverage get rainfall_available=False
     and NaN features — never a fabricated 0 or an interpolated guess.

Run (once rainfall files exist):
    pip install xarray netCDF4 --break-system-packages
    python src/features/rainfall_extraction.py
"""
import glob
import os
import sys

import numpy as np
import pandas as pd

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "configs"))
import config as cfg

CANDIDATE_PRECIP_VARS = ["precipitationCal", "precipitation", "precip", "HQprecipitation", "precipitationUncal"]
CANDIDATE_LAT_DIMS = ["lat", "latitude", "Latitude"]
CANDIDATE_LON_DIMS = ["lon", "longitude", "Longitude"]
CANDIDATE_TIME_DIMS = ["time", "Time", "date"]


def _find_rainfall_files():
    exts = ("*.nc", "*.nc4", "*.hdf5", "*.h5", "*.tif")
    files = []
    for e in exts:
        files.extend(glob.glob(os.path.join(cfg.RAINFALL_DIR, e)))
    return sorted(files)


def _detect_names(ds):
    var = next((v for v in CANDIDATE_PRECIP_VARS if v in ds.variables), None)
    lat_dim = next((d for d in CANDIDATE_LAT_DIMS if d in ds.coords), None)
    lon_dim = next((d for d in CANDIDATE_LON_DIMS if d in ds.coords), None)
    time_dim = next((d for d in CANDIDATE_TIME_DIMS if d in ds.coords), None)
    if not all([var, lat_dim, lon_dim, time_dim]):
        raise ValueError(
            f"Could not auto-detect required fields in rainfall file. "
            f"Found variables={list(ds.variables)}, coords={list(ds.coords)}. "
            f"precip_var={var}, lat={lat_dim}, lon={lon_dim}, time={time_dim}. "
            f"Update CANDIDATE_* lists in this file with the actual names."
        )
    return var, lat_dim, lon_dim, time_dim


def load_rainfall_dataset():
    """Opens all rainfall files as one xarray Dataset (lazy, dask-backed)."""
    files = _find_rainfall_files()
    if not files:
        return None
    import xarray as xr  # only required once rainfall files actually exist
    ds = xr.open_mfdataset(files, combine="by_coords")
    return ds


def compute_event_rainfall(ds, var, lat_dim, lon_dim, time_dim, lat, lon, event_dt):
    """Returns dict of window_name -> mm, using only timestamps <= event_dt
    (no future leakage), or None per window if outside file's time coverage."""
    point = ds[var].sel({lat_dim: lat, lon_dim: lon}, method="nearest")
    out = {}
    for name, hours in cfg.RAINFALL_WINDOWS_HOURS.items():
        window_start = event_dt - pd.Timedelta(hours=hours)
        sel = point.sel({time_dim: slice(window_start, event_dt)})
        if sel[time_dim].size == 0:
            out[name] = np.nan
        else:
            out[name] = float(sel.sum().values)
    return out


def extract_rainfall_features(events_df: pd.DataFrame) -> pd.DataFrame:
    """
    events_df must have columns: latitude, longitude, event_date, date_precision.
    Returns events_df with rainfall_6h/24h/72h/7d + rainfall_available added.
    """
    df = events_df.copy()
    for col in cfg.RAINFALL_WINDOWS_HOURS:
        df[col] = np.nan
    df["rainfall_available"] = False

    ds = load_rainfall_dataset()
    if ds is None:
        print("No rainfall files found in data/raw/rainfall/ — "
              "returning all-NaN rainfall features (rainfall_available=False for all rows).")
        return df

    var, lat_dim, lon_dim, time_dim = _detect_names(ds)
    t_min = pd.to_datetime(ds[time_dim].min().values)
    t_max = pd.to_datetime(ds[time_dim].max().values)
    print(f"Rainfall file time coverage: {t_min} to {t_max}, variable={var}")

    for idx, row in df.iterrows():
        if row["date_precision"] != "day" or pd.isna(row["latitude"]) or pd.isna(row["longitude"]):
            continue  # no usable timestamp -> stays NaN / unavailable, not fabricated
        event_dt = pd.to_datetime(row["event_date"])
        if not (t_min <= event_dt <= t_max):
            continue  # outside rainfall file coverage -> stays NaN
        feats = compute_event_rainfall(ds, var, lat_dim, lon_dim, time_dim,
                                        row["latitude"], row["longitude"], event_dt)
        for k, v in feats.items():
            df.at[idx, k] = v
        df.at[idx, "rainfall_available"] = not any(pd.isna(v) for v in feats.values())

    return df


if __name__ == "__main__":
    if not os.path.exists(cfg.NER_DEM_CSV):
        raise SystemExit("Run src/geospatial/dem_features.py first.")
    events = pd.read_csv(cfg.NER_DEM_CSV)
    out = extract_rainfall_features(events)
    out_path = os.path.join(cfg.INTERIM_DIR, "ner_landslides_with_rainfall.csv")
    out.to_csv(out_path, index=False)
    n_avail = int(out["rainfall_available"].sum())
    print(f"Rainfall features available for {n_avail}/{len(out)} events.")
    print(f"Saved -> {out_path}")
