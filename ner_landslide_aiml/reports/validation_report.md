# SIH26001 — Pipeline Validation Report

- GSI records (all-India, extracted from PDF): **36071**
- NER records after filtering (Step 2.1): **10675**
  - coord_valid: 10673, flagged invalid (kept): 2
  - date_precision=day: 1370, year-only: 2618, none: 6687
  - coordinate collisions (shared lat/lon, not auto-treated as duplicates): 464
- NER records with DEM features (Step 2.4, GSI only): **2205 (20.7%)** — limited by only 4 SRTM tiles currently present
- Merged positive pool (GSI primary + GLC secondary, deduped): **11003** (GSI=10675, GLC-unique=328); DEM available: 2316 (21.0%)
- Final ML dataset size (Step 5): **33009** (positives=11003, negatives=22006)
- Records with usable rainfall features: **0/33009** — PENDING but NOT a blocker: rainfall is treated as an optional feature group (constant placeholder + availability flag) and will populate automatically once files land in data/raw/rainfall/

## Model comparison (DEM/terrain features only — rainfall not yet populated)

|                     |   precision |    recall |        f1 |   roc_auc |   pr_auc |
|:--------------------|------------:|----------:|----------:|----------:|---------:|
| xgboost             |    0.253054 | 0.854064  | 0.386164  |  0.860197 | 0.315042 |
| random_forest       |    0.355514 | 0.0481822 | 0.0827457 |  0.85175  | 0.287953 |
| logistic_regression |    0.225818 | 0.797605  | 0.347781  |  0.821727 | 0.270007 |

*Best model (highest PR-AUC) selected and saved to models/best_model.pkl. PR-AUC (not accuracy) is the primary metric given class imbalance and the cost of false negatives in landslide risk. These numbers reflect terrain signal only and are expected to change once rainfall is added — re-run this step after rainfall extraction to get the real, final metrics.*

## Known limitations

- Only ~13% of NER GSI records have a fully parseable event date; most of those fall outside the original 2007–2016 rainfall window (extension to 2015–2025 in progress).
- DEM coverage is currently ~20-25% of NER events (only 4 of the ~30+ SRTM tiles needed for full regional coverage).
- The GSI inventory is built around NH/SH road corridors — spatial sampling is not uniform across NER terrain.
- Negative samples are drawn only from DEM-covered patches, and 'no recorded landslide' is not proof of safety (GSI is a record of known events, not an exhaustive census) — real label noise is possible.