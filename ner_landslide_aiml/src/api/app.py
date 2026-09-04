"""
Backend integration layer — exposes src/inference/predict.py as a REST API.

This is the piece your main backend (whatever language/framework it's in)
calls. It does NOT replace your backend — it sits next to it as a small
model-serving service. Your GIS/web backend calls this over HTTP; you never
need to re-implement model loading, DEM lookup, or risk logic elsewhere.

Run:
    pip install fastapi uvicorn pydantic --break-system-packages
    uvicorn src.api.app:app --host 0.0.0.0 --port 8000 --reload

Then:
    GET  http://localhost:8000/health
    POST http://localhost:8000/predict
         {"latitude": 25.57, "longitude": 91.88, "date": "2024-07-14"}
"""
import os
import sys

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", ".."))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "inference"))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "configs"))
import config as cfg
from predict import predict_risk

app = FastAPI(title="NER Landslide Risk API", version="0.1.0")

# CORS: open for prototype/demo; restrict allow_origins to your actual
# frontend domain(s) before any real deployment.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class PredictRequest(BaseModel):
    latitude: float = Field(..., ge=-90, le=90)
    longitude: float = Field(..., ge=-180, le=180)
    date: str = Field(..., description="ISO date, e.g. 2024-07-14")


class PredictResponse(BaseModel):
    risk_probability: float
    risk_level: str
    top_factors: list
    rainfall_data_used: bool


@app.get("/health")
def health():
    return {
        "status": "ok",
        "model_loaded": os.path.exists(cfg.BEST_MODEL_PATH),
        "rainfall_data_available": len(os.listdir(cfg.RAINFALL_DIR)) > 0
        if os.path.isdir(cfg.RAINFALL_DIR) else False,
    }


@app.post("/predict", response_model=PredictResponse)
def predict(req: PredictRequest):
    try:
        result = predict_risk(latitude=req.latitude, longitude=req.longitude, date=req.date)
    except RuntimeError as e:
        # outside DEM coverage / model not trained yet -> a clear 4xx, not a
        # silent fabricated prediction
        raise HTTPException(status_code=422, detail=str(e))
    return result


@app.get("/coverage")
def coverage():
    """Lets the frontend show which DEM tiles are loaded, so it can grey out
    unsupported map areas instead of letting users click into a 422."""
    from rasterio import open as rio_open
    import glob
    tiles = []
    for p in glob.glob(os.path.join(cfg.DEM_DIR, "*.tif")):
        with rio_open(p) as src:
            b = src.bounds
            tiles.append({"file": os.path.basename(p),
                          "bounds": [b.left, b.bottom, b.right, b.top]})
    return {"dem_tiles": tiles}
