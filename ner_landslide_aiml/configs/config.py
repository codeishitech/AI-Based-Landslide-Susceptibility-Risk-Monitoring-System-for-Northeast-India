"""Central configuration — single source of truth for paths and parameters."""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# --- paths ---
RAW_DIR = os.path.join(ROOT, "data", "raw")
GSI_PDF_PATH = os.path.join(RAW_DIR, "gsi", "landslide_report.pdf")
GSI_FULL_RAW_CSV = os.path.join(ROOT, "data", "interim", "gsi_full_raw.csv")
GLC_CSV_PATH = os.path.join(RAW_DIR, "landslides", "Global_Landslide_Catalog_Export_rows.csv")
GLC_NER_CLEAN_CSV = os.path.join(ROOT, "data", "interim", "glc_ner_clean.csv")
DEM_DIR = os.path.join(RAW_DIR, "dem")
RAINFALL_DIR = os.path.join(RAW_DIR, "rainfall")

INTERIM_DIR = os.path.join(ROOT, "data", "interim")
PROCESSED_DIR = os.path.join(ROOT, "data", "processed")
MODELS_DIR = os.path.join(ROOT, "models")
REPORTS_DIR = os.path.join(ROOT, "reports")

NER_CLEAN_CSV = os.path.join(PROCESSED_DIR, "ner_landslides_clean.csv")
NER_DEM_CSV = os.path.join(PROCESSED_DIR, "ner_landslides_with_dem.csv")
NER_DEM_MERGED_CSV = os.path.join(PROCESSED_DIR, "ner_landslides_with_dem_merged.csv")
GLC_DEDUPE_DIST_KM = 1.0  # GLC point within this distance + same year of a GSI point -> treat as same event
ML_DATASET_CSV = os.path.join(PROCESSED_DIR, "ml_dataset.csv")
BEST_MODEL_PATH = os.path.join(MODELS_DIR, "best_model.pkl")

# --- domain parameters ---
NER_STATES = [
    "Arunachal Pradesh", "Assam", "Manipur", "Meghalaya",
    "Mizoram", "Nagaland", "Sikkim", "Tripura",
]
# known variant spellings seen in the raw GSI PDF extraction -> canonical name
STATE_NAME_FIXES = {
    "-arunachal pradesh": "Arunachal Pradesh",
    "meghalaya": "Meghalaya",  # handles 'MEGHALAYA' after .lower()
}

# rough sanity-check bounding box for the NER region (NOT used to fabricate
# missing coordinates — only to flag rows with implausible lat/lon)
NER_BBOX = {"lat_min": 20.0, "lat_max": 30.0, "lon_min": 87.0, "lon_max": 98.0}

DEM_NODATA = -32767

RAINFALL_WINDOWS_HOURS = {"rainfall_6h": 6, "rainfall_24h": 24,
                           "rainfall_72h": 72, "rainfall_7d": 24 * 7}

# negative sampling
NEG_POS_RATIO = 2.0          # negatives per positive (configurable)
NEG_MIN_DIST_KM = 2.0        # min distance from any known positive
NEG_RANDOM_SEED = 42

RISK_LEVELS = ["LOW", "MODERATE", "HIGH", "CRITICAL"]  # thresholds set from
# validation results in Phase 3 — NOT hardcoded arbitrarily; see models/README.
