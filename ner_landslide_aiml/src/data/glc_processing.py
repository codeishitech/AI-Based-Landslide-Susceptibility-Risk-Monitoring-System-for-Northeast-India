"""
Secondary landslide source: NASA Global Landslide Catalog (GLC) CSV.
GSI PDF inventory remains PRIMARY. GLC supplements it — NOT a replacement.

Filters GLC to the 8 NER states (normalizing diacritic spelling variants seen
in the raw file: 'Nāgāland' -> 'Nagaland', 'Arunāchal Pradesh' -> 'Arunachal
Pradesh', 'Meghālaya' -> 'Meghalaya'), parses event_date, validates coords.

Output: data/interim/glc_ner_clean.csv

Run:
    python src/data/glc_processing.py
"""
import os
import sys
import unicodedata

import pandas as pd

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "configs"))
import config as cfg


def strip_diacritics(s):
    if not isinstance(s, str):
        return s
    return "".join(c for c in unicodedata.normalize("NFD", s) if unicodedata.category(c) != "Mn")


def main():
    df = pd.read_csv(cfg.GLC_CSV_PATH, dtype=str)
    df = df[df["country_name"] == "India"].copy()
    df["state_norm"] = df["admin_division_name"].apply(strip_diacritics).str.strip()

    ner_df = df[df["state_norm"].isin(cfg.NER_STATES)].copy()

    ner_df["latitude"] = pd.to_numeric(ner_df["latitude"], errors="coerce")
    ner_df["longitude"] = pd.to_numeric(ner_df["longitude"], errors="coerce")
    ner_df["coord_valid"] = (
        ner_df["latitude"].between(cfg.NER_BBOX["lat_min"], cfg.NER_BBOX["lat_max"]) &
        ner_df["longitude"].between(cfg.NER_BBOX["lon_min"], cfg.NER_BBOX["lon_max"])
    )

    ner_df["event_date"] = pd.to_datetime(ner_df["event_date"], errors="coerce").dt.date.astype(str)
    ner_df.loc[ner_df["event_date"] == "NaT", "event_date"] = None
    ner_df["date_precision"] = ner_df["event_date"].apply(lambda d: "day" if d else "none")

    out = pd.DataFrame({
        "event_id": "GLC_" + ner_df.index.astype(str),
        "source": "GLC",
        "state": ner_df["state_norm"],
        "district": ner_df.get("admin_division_population", None),  # not a real district field in GLC; left None below
        "slide_name": ner_df.get("event_title", None),
        "nh_sh_location": None,
        "latitude": ner_df["latitude"], "longitude": ner_df["longitude"],
        "coord_valid": ner_df["coord_valid"], "coord_issue": None, "coord_collision": False,
        "event_date": ner_df["event_date"], "date_precision": ner_df["date_precision"],
        "history_raw": ner_df.get("landslide_trigger", None),
        "material_involved": None, "movement_type": ner_df.get("landslide_category", None),
    })
    out["district"] = None  # GLC has no clean district field comparable to GSI's

    os.makedirs(cfg.INTERIM_DIR, exist_ok=True)
    out.to_csv(cfg.GLC_NER_CLEAN_CSV, index=False)

    print(f"GLC India rows: {len(df)}")
    print(f"GLC NER rows (secondary source): {len(out)}")
    print(f"  coord_valid: {int(out['coord_valid'].sum())}")
    print(f"  date_precision=day: {int((out['date_precision']=='day').sum())}")
    print(f"Saved -> {cfg.GLC_NER_CLEAN_CSV}")


if __name__ == "__main__":
    main()
