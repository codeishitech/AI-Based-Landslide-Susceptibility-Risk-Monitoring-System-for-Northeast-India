# SIH26001 — NER Landslide Early Warning: ML Pipeline

Baseline uses exactly three datasets: GSI landslide inventory, NASA GPM IMERG
rainfall, SRTM 1-arcsecond DEM. No other dataset is used.

## Current status (see `reports/validation_report.md` for live numbers)

| Step | Status |
|---|---|
| 2.1 Filter GSI to NER | ✅ done — 10,675 clean records |
| 2.2 Clean/validate landslide events | ✅ done |
| 2.4 DEM feature extraction | ✅ done — 2,205 records (20.7%) have DEM coverage from the current 4 tiles |
| 4. Negative sampling | ✅ done — 21,350 negatives generated |
| 5. Final ML table assembly | ✅ done, structurally — `ml_dataset.csv` exists, rainfall columns are NaN |
| 3. Rainfall extraction | ⏳ **PENDING** — code complete, waiting on files in `data/raw/rainfall/` |
| 6. Model training | ⏳ **BLOCKED (intentionally)** — script refuses to run while rainfall coverage is 0% |
| 7. Explainability | ⏳ blocked on step 6 |
| 8. Inference API | ⏳ blocked on step 6 (code complete otherwise) |
| 9. Visualizations | ✅ landslide locations & slope distribution done; rainfall/model plots pending |
| 10. Validation report | ✅ auto-generated, marks pending sections explicitly |

## 1. Install dependencies

```bash
pip install -r requirements.txt --break-system-packages
```

## 2. Place the three datasets

```
data/raw/gsi/landslide_report.pdf        # GSI field-validated inventory (PDF)
data/raw/rainfall/*.nc / *.nc4           # NASA GPM IMERG files (any date range)
data/raw/dem/*.tif                       # SRTM tiles (add more any time, no code change needed)
```

## 3. Extract the GSI table from the PDF (one-time, ~4-5 min for 900 pages)

```bash
python src/data/extract_gsi_pdf.py
```
Output: `data/interim/gsi_full_raw.csv`

## 4. Filter + clean landslide events (Steps 2.1 + 2.2)

```bash
python src/data/gsi_processing.py
```
Output: `data/processed/ner_landslides_clean.csv`

## 5. Extract DEM features (Step 2.4)

```bash
python src/geospatial/dem_features.py
```
Output: `data/processed/ner_landslides_with_dem.csv`

## 6. Generate negative samples (Step 4)

```bash
python src/data/negative_sampling.py
```
Output: `data/interim/negative_samples.csv`

## 7. Build the final ML dataset (Steps 3 + 5 combined)

```bash
python src/data/build_ml_dataset.py
```
Output: `data/processed/ml_dataset.csv`. Rainfall columns populate automatically
once files exist in `data/raw/rainfall/` — no code change needed, just re-run
this step (it calls `src/features/rainfall_extraction.py` internally).

## 8. Train models (only once rainfall coverage exists)

```bash
python src/models/train_models.py
```
Refuses to run (by design) if more than half the dataset is missing rainfall
features. Output: `models/best_model.pkl`, `reports/model_comparison.csv`.

## 9. Evaluate / explain

```bash
python src/evaluation/explainability.py
```

## 10. Run inference

```python
from src.inference.predict import predict_risk
predict_risk(latitude=25.57, longitude=91.88, date="2024-07-14")
```

## 11. Visualizations & validation report

```bash
python src/visualization/plots.py
python src/evaluation/validation_report.py
```

## Backend integration

`src/api/app.py` exposes the model as a REST API — this is the layer your
main backend/frontend actually talks to.

```bash
uvicorn src.api.app:app --host 0.0.0.0 --port 8000
```

- `GET /health` — model + rainfall-data status, for your ops/monitoring
- `POST /predict` — `{"latitude":.., "longitude":.., "date":".."}` → `{risk_probability, risk_level, top_factors, rainfall_data_used}`
- `GET /coverage` — current DEM tile bounding boxes, so the frontend map can
  grey out areas with no coverage instead of letting users click into a 422

**Integration patterns, pick based on your stack:**
- **Backend is Python (Django/Flask/FastAPI):** skip the HTTP hop — `from src.inference.predict import predict_risk` directly in your view/route.
- **Backend is Node/Java/other:** run this as a small internal microservice (as above); your backend calls it like any other internal HTTP service. Nothing here needs to be rewritten in your backend's language.
- **Map/GIS frontend:** call `/coverage` once on load to know which regions are queryable, then `/predict` per point/click, or batch a grid of points for a heatmap overlay (loop `/predict` or add a `/predict_batch` endpoint if you need it — not built yet, ask if useful).
- **Offline/precomputed risk layer:** instead of live queries, run `predict_risk()` over a grid of points across NER and cache results as a GeoJSON/tile layer your backend serves statically — cheaper if the model doesn't change often.

CORS is wide open (`allow_origins=["*"]`) for prototype convenience — restrict
it to your actual frontend origin before any real deployment.

### Node.js backend

Ready-made client + Express route in `integration/node/`:
- `landslideClient.js` — fetch wrapper (`getRisk`, `getCoverage`, `checkHealth`), reads `LANDSLIDE_API_URL` env var (defaults to `http://localhost:8000`)
- `landslideRoutes.js` — Express router, mount with `app.use('/api/landslide', require('./integration/node/landslideRoutes'))`

Run the Python service and your Node app side by side (two processes — the
Python service is not a Node module, it's called over HTTP):
```bash
# terminal 1
uvicorn src.api.app:app --host 0.0.0.0 --port 8000
# terminal 2
LANDSLIDE_API_URL=http://localhost:8000 node your-app.js
```
Frontend then calls your existing Node backend (`POST /api/landslide/risk`),
which proxies to the Python service — the frontend never talks to Python directly.

## Known limitations (see full detail in `reports/validation_report.md`)
- Only ~13% of NER GSI records have a fully parseable event date.
- DEM coverage is ~20% of NER events with only 4 tiles — add more SRTM tiles
  to `data/raw/dem/` to improve this (no code change required).
- GSI inventory is road-corridor-biased (NH/SH), not a uniform regional census.
- Negative sampling reflects "no recorded event," not "confirmed safe."
