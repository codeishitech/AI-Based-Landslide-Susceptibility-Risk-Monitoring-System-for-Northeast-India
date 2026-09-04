"""
STEP 10 — Validation report: pulls real numbers from the pipeline outputs
that currently exist. Sections that depend on data not yet available are
explicitly marked PENDING rather than skipped silently or filled with
placeholders.

Run:
    python src/evaluation/validation_report.py
"""
import os
import sys

import pandas as pd

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "configs"))
import config as cfg


def main():
    lines = ["# SIH26001 — Pipeline Validation Report", ""]

    all_india = pd.read_csv(cfg.GSI_FULL_RAW_CSV)
    lines.append(f"- GSI records (all-India, extracted from PDF): **{len(all_india)}**")

    clean = pd.read_csv(cfg.NER_CLEAN_CSV)
    lines.append(f"- NER records after filtering (Step 2.1): **{len(clean)}**")
    lines.append(f"  - coord_valid: {int(clean['coord_valid'].sum())}, "
                 f"flagged invalid (kept): {int((~clean['coord_valid']).sum())}")
    lines.append(f"  - date_precision=day: {int((clean['date_precision']=='day').sum())}, "
                 f"year-only: {int((clean['date_precision']=='year').sum())}, "
                 f"none: {int((clean['date_precision']=='none').sum())}")
    lines.append(f"  - coordinate collisions (shared lat/lon, not auto-treated as duplicates): "
                 f"{int(clean['coord_collision'].sum())}")

    dem = pd.read_csv(cfg.NER_DEM_CSV)
    n_dem = int(dem["dem_available"].sum())
    lines.append(f"- NER records with DEM features (Step 2.4, GSI only): **{n_dem} "
                 f"({n_dem/len(dem):.1%})** — limited by only 4 SRTM tiles currently present")

    if os.path.exists(cfg.NER_DEM_MERGED_CSV):
        merged = pd.read_csv(cfg.NER_DEM_MERGED_CSV)
        n_glc = int((merged["source"] == "GLC").sum())
        n_merged_dem = int(merged["dem_available"].sum())
        lines.append(f"- Merged positive pool (GSI primary + GLC secondary, deduped): "
                     f"**{len(merged)}** (GSI={len(merged)-n_glc}, GLC-unique={n_glc}); "
                     f"DEM available: {n_merged_dem} ({n_merged_dem/len(merged):.1%})")

    if os.path.exists(cfg.ML_DATASET_CSV):
        ml = pd.read_csv(cfg.ML_DATASET_CSV)
        n_rain = int((~ml["missing_rainfall"]).sum())
        lines.append(f"- Final ML dataset size (Step 5): **{len(ml)}** "
                     f"(positives={int((ml['landslide']==1).sum())}, "
                     f"negatives={int((ml['landslide']==0).sum())})")
        lines.append(f"- Records with usable rainfall features: **{n_rain}/{len(ml)}** "
                     f"— PENDING but NOT a blocker: rainfall is treated as an optional "
                     f"feature group (constant placeholder + availability flag) and will "
                     f"populate automatically once files land in data/raw/rainfall/")
    else:
        lines.append("- Final ML dataset: not yet built")

    model_report = os.path.join(cfg.REPORTS_DIR, "model_comparison.csv")
    if os.path.exists(model_report):
        cmp = pd.read_csv(model_report, index_col=0)
        lines.append("\n## Model comparison (DEM/terrain features only — rainfall not yet populated)\n")
        lines.append(cmp.to_markdown())
        lines.append("\n*Best model (highest PR-AUC) selected and saved to models/best_model.pkl. "
                     "PR-AUC (not accuracy) is the primary metric given class imbalance and the "
                     "cost of false negatives in landslide risk. These numbers reflect terrain "
                     "signal only and are expected to change once rainfall is added — re-run "
                     "this step after rainfall extraction to get the real, final metrics.*")
    else:
        lines.append("\n## Model comparison\n\n**PENDING** — not yet trained.")

    lines.append("\n## Known limitations\n")
    lines.append("- Only ~13% of NER GSI records have a fully parseable event date; "
                 "most of those fall outside the original 2007–2016 rainfall window "
                 "(extension to 2015–2025 in progress).")
    lines.append("- DEM coverage is currently ~20-25% of NER events (only 4 of the ~30+ "
                 "SRTM tiles needed for full regional coverage).")
    lines.append("- The GSI inventory is built around NH/SH road corridors — spatial "
                 "sampling is not uniform across NER terrain.")
    lines.append("- Negative samples are drawn only from DEM-covered patches, and 'no "
                 "recorded landslide' is not proof of safety (GSI is a record of known "
                 "events, not an exhaustive census) — real label noise is possible.")

    os.makedirs(cfg.REPORTS_DIR, exist_ok=True)
    out_path = os.path.join(cfg.REPORTS_DIR, "validation_report.md")
    with open(out_path, "w") as f:
        f.write("\n".join(lines))
    print("\n".join(lines))
    print(f"\nSaved -> {out_path}")


if __name__ == "__main__":
    main()
