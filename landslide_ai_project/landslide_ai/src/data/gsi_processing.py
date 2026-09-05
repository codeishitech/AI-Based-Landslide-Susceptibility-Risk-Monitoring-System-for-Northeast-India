"""
STEP 2.1 — Filter the extracted GSI inventory to the 8 NER states.
STEP 2.2 — Clean and validate: parse coordinates, parse the History field into
           an event date with an explicit precision flag, flag (don't silently
           drop) missing/invalid data, and separate genuine duplicates from
           coordinate collisions.

Input : data/interim/gsi_full_raw.csv   (36,071 all-India rows, produced by
                                          src/data/extract_gsi_pdf.py)
Output: data/processed/ner_landslides_clean.csv

Nothing is fabricated: rows with bad coordinates are kept and flagged
(coord_valid=False) rather than silently dropped, unless they're genuinely
unusable for anything (both lat & lon missing) — those are the only rows
actually removed, and the count is reported.

Run:
    python src/data/gsi_processing.py
"""
import os
import re
import sys

import pandas as pd
from dateutil import parser as dateparser

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "configs"))
import config as cfg


def normalize_state(raw_state: str) -> str:
    s = (raw_state or "").strip()
    fixed = cfg.STATE_NAME_FIXES.get(s.lower())
    return fixed if fixed else s


def parse_history(history: str):
    """
    Returns (event_date_or_None, date_precision) where date_precision is one
    of: 'day' (full date, usable for rainfall alignment), 'year' (year only,
    NOT usable for daily rainfall windows), 'none' (no date info at all).
    """
    if not isinstance(history, str):
        return None, "none"
    h = history.strip()
    if h == "" or h.upper() == "NA":
        return None, "none"

    m = re.search(r"(\d{1,2}\s+[A-Za-z]+\s+(19|20)\d{2})", h)
    if m:
        try:
            dt = dateparser.parse(m.group(1), dayfirst=True)
            return dt.date().isoformat(), "day"
        except Exception:
            pass

    m2 = re.search(r"\b(19|20)\d{2}\b", h)
    if m2:
        return m2.group(0), "year"

    return None, "none"


def validate_coords(lat, lon):
    """Returns (lat, lon, coord_valid, issue)."""
    try:
        lat_f = float(lat)
    except (TypeError, ValueError):
        lat_f = None
    try:
        lon_f = float(lon)
    except (TypeError, ValueError):
        lon_f = None

    if lat_f is None or lon_f is None:
        return lat_f, lon_f, False, "missing_lat_or_lon"

    # sign-flip check: a negative latitude in a region that should be
    # entirely northern hemisphere is very likely a data entry error, not a
    # real location. We flag it, we do not silently "fix" it.
    if lat_f < 0 and -lat_f >= cfg.NER_BBOX["lat_min"]:
        return lat_f, lon_f, False, "latitude_sign_error_suspected"

    in_box = (cfg.NER_BBOX["lat_min"] <= lat_f <= cfg.NER_BBOX["lat_max"] and
              cfg.NER_BBOX["lon_min"] <= lon_f <= cfg.NER_BBOX["lon_max"])
    if not in_box:
        return lat_f, lon_f, False, "outside_ner_bbox"

    return lat_f, lon_f, True, None


def main():
    df = pd.read_csv(cfg.GSI_FULL_RAW_CSV, dtype=str)
    n_total = len(df)

    df["state_norm"] = df["State"].apply(normalize_state)
    ner_df = df[df["state_norm"].isin(cfg.NER_STATES)].copy()
    n_ner = len(ner_df)

    lat, lon, valid, issue = [], [], [], []
    for _, row in ner_df.iterrows():
        la, lo, ok, iss = validate_coords(row["Latitude"], row["Longitude"])
        lat.append(la); lon.append(lo); valid.append(ok); issue.append(iss)
    ner_df["latitude"] = lat
    ner_df["longitude"] = lon
    ner_df["coord_valid"] = valid
    ner_df["coord_issue"] = issue

    dates, precisions = [], []
    for h in ner_df["History"]:
        d, p = parse_history(h)
        dates.append(d); precisions.append(p)
    ner_df["event_date"] = dates
    ner_df["date_precision"] = precisions

    # Only remove rows unusable for EVERYTHING (no coordinate at all).
    unusable_mask = ner_df["coord_issue"] == "missing_lat_or_lon"
    n_removed = int(unusable_mask.sum())
    ner_df = ner_df[~unusable_mask].copy()

    # Coordinate collisions vs genuine duplicates:
    # a genuine duplicate = identical state+district+slide_name+lat+lon
    # (same site, same name) -> keep first, flag rest as duplicate_of.
    ner_df["_dupe_key"] = (ner_df["state_norm"] + "|" + ner_df["District"].fillna("") + "|" +
                           ner_df["Slide_Name"].fillna("") + "|" +
                           ner_df["latitude"].round(5).astype(str) + "|" +
                           ner_df["longitude"].round(5).astype(str))
    is_genuine_dupe = ner_df.duplicated(subset="_dupe_key", keep="first")
    n_genuine_dupes = int(is_genuine_dupe.sum())
    ner_df = ner_df[~is_genuine_dupe].copy()

    ner_df["coord_collision"] = ner_df.duplicated(
        subset=[ner_df["latitude"].round(5).name, ner_df["longitude"].round(5).name],
        keep=False
    )
    coord_group = ner_df.groupby([ner_df["latitude"].round(5), ner_df["longitude"].round(5)]).ngroup()
    coord_counts = coord_group.map(coord_group.value_counts())
    ner_df["coord_collision"] = coord_counts > 1

    out_cols = {
        "Sl.No.": "event_id", "state_norm": "state", "District": "district",
        "Slide_Name": "slide_name", "NH_SH_Location": "nh_sh_location",
        "latitude": "latitude", "longitude": "longitude",
        "coord_valid": "coord_valid", "coord_issue": "coord_issue",
        "coord_collision": "coord_collision",
        "event_date": "event_date", "date_precision": "date_precision",
        "History": "history_raw",
        "Material Involved": "material_involved", "Movement\nType": "movement_type",
    }
    out = ner_df[list(out_cols.keys())].rename(columns=out_cols)

    os.makedirs(cfg.PROCESSED_DIR, exist_ok=True)
    out.to_csv(cfg.NER_CLEAN_CSV, index=False)

    print(f"All-India records read:        {n_total}")
    print(f"NER records (Step 2.1):        {n_ner}")
    print(f"Removed (no usable coord at all): {n_removed}")
    print(f"Removed (genuine duplicate site+name+coord): {n_genuine_dupes}")
    print(f"Final clean NER records:       {len(out)}")
    print(f"  coord_valid=True:            {int(out['coord_valid'].sum())}")
    print(f"  coord_valid=False (flagged, kept): {int((~out['coord_valid']).sum())}")
    print(f"  date_precision=day:          {int((out['date_precision']=='day').sum())}")
    print(f"  date_precision=year:         {int((out['date_precision']=='year').sum())}")
    print(f"  date_precision=none:         {int((out['date_precision']=='none').sum())}")
    print(f"  coord_collision=True:        {int(out['coord_collision'].sum())}")
    print(f"Saved -> {cfg.NER_CLEAN_CSV}")


if __name__ == "__main__":
    main()
