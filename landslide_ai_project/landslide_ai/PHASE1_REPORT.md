# Phase 1 — Dataset Inspection & Compatibility Report

Scope per your instructions: GSI landslide inventory (A), NASA GPM IMERG rainfall (B),
SRTM DEM (C). No other dataset introduced. Full details below — nothing fabricated;
where something couldn't be checked, it's stated as such.

## A. GSI Landslide Inventory — `landslide_report.pdf` — VALIDATED

- Format: PDF, 904 pages, one consistent table per page, extracted in full with
  `pdfplumber` (script: `src/data/extract_gsi_pdf.py`).
- **36,071 records nationwide**, 0 duplicate `Sl.No.` values.
- Columns: `Sl.No., Slide_No, State, District, Slide_Name, NH_SH_Location,
  Latitude, Longitude, Material Involved, Movement Type, History`.
- Lat/Lon: plain decimal degrees. **CRS is not stated in the file** — values are
  consistent with WGS84 (standard for GPS-collected points), but this is an
  assumption, not a confirmed fact from the data itself.
- `History` is the closest thing to an event-date field, and it is free text,
  not a structured date.

### Filtered to the 8 NER states → **11,026 records**

| State | Records |
|---|---|
| Mizoram | 3,487 |
| Nagaland | 1,902 |
| Manipur | 1,631 |
| Arunachal Pradesh | 1,223 |
| Meghalaya | 1,052 |
| Assam | 857 |
| Sikkim | 777 |
| Tripura | 97 |

(State names had a few variants — `-Arunachal Pradesh`, `MEGHALAYA` — normalized
into the counts above.)

### Data quality found in the NER subset
- **Coordinates**: 11,024/11,026 fall inside a sanity-check bounding box
  (lat 20–30°N, lon 87–98°E). 2 problems found and NOT silently fixed:
  one row (Assam/West Karbi Anglong) has a blank longitude; one row
  (Mizoram/Aizawl) has latitude −23.74 instead of a plausible +23.74 (looks
  like a sign entry error in the source PDF).
- **Event dates (`History`) — this is the critical finding:**
  - 6,854 / 11,026 (62%) = `NA`, no date info at all.
  - 2,747 / 11,026 (25%) have only a bare year (e.g. `2015`) — not usable for
    daily rainfall alignment.
  - **Only 1,409 / 11,026 (13%) have a fully parseable exact date.**
  - Of those 1,409 exact dates, **only 255 fall inside your downloaded GPM
    IMERG window (2007-04-01 to 2016-10-31)** — the rest are mostly very
    recent (563 in 2024, 353 in 2025), outside that window.
  - **Practical consequence: as currently scoped (this inventory + this
    rainfall date range), only ~255 NER events can get real rainfall
    features.** The other ~10,770 events can still get DEM/terrain features
    and be used for non-rainfall analysis, but not for the rainfall-based
    ML table Phase 2 asks for, unless the rainfall download is extended to
    cover more years — that's a range decision on the same dataset you
    already chose, not a new dataset, so flagging it rather than silently
    dropping ~97% of events.
- **Coordinate collisions**: 1,053 rows share identical (lat, lon) with at
  least one other row. Sampling shows these are mostly repeat rockfall/slide
  points along the same highway km-marker recorded in different years — 
  plausibly real recurring events, not import duplicates — but worth a
  decision on how to treat them in Step 2.2.

Saved for reuse: `data/interim/gsi_full_raw.csv` (36,071, all-India) and
`data/interim/gsi_ner_raw.csv` (11,026, NER only, unfiltered further — this is
the direct input for Step 2.1/2.2, not yet cleaned).

## B. NASA GPM IMERG rainfall — NOT YET RECEIVED

No rainfall file has been uploaded. The 6 files present in this conversation
are the 4 DEM tiles, the landslide PDF, and (from earlier) the GLC CSV — no
NetCDF/HDF5/GeoTIFF rainfall file. **This blocks Step 2.3 entirely** — I can't
inspect variable names, units, temporal/spatial resolution, or NoData values
for a file that isn't here. Please upload it.

## C. SRTM DEM tiles — VALIDATED (unchanged from before)

All 4 tiles confirmed genuine SRTM1 GeoTIFFs, WGS84 (EPSG:4326), 3601×3601,
~30m resolution, NoData=-32767, elevation in meters:

| Tile | Extent (lat, lon) |
|---|---|
| n27_e088 | 27–28°N, 88–89°E |
| n26_e088 | 26–27°N, 88–89°E |
| n26_e091 | 26–27°N, 91–92°E |
| n25_e094 | 25–26°N, 94–95°E |

These 4 tiles only cover small patches of the NER — most of the 11,026
filtered events will fall outside current DEM coverage until more tiles are
added. Step 2.4 will report exactly how many are affected once run; it won't
guess.

## Compatibility summary

| Pair | Status |
|---|---|
| GSI ↔ DEM | Compatible — both in decimal-degree lat/lon, presumed WGS84 |
| GSI ↔ Rainfall | Cannot assess — rainfall file missing |
| Overall pipeline | **Blocked on rainfall file** for Step 2.3; Steps 2.1, 2.2, 2.4 can proceed now |

## Before Phase 2
1. Please upload the GPM IMERG rainfall file(s).
2. Given only ~255 NER events land inside your current 2007–2016 rainfall
   window, do you want to extend the rainfall download to cover more recent
   years (up to 2025) before I build Step 2.3, or proceed with the ~255-event
   subset for now?

In the meantime I can start **Steps 2.1, 2.2, and 2.4** (filtering, cleaning,
DEM feature extraction), which don't need the rainfall file — want me to go
ahead with those now while you get the rainfall data?
