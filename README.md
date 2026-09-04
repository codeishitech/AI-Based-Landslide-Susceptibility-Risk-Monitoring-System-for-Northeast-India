# AI-Based Landslide Susceptibility & Risk Monitoring System for Northeast India

An AI + GIS based system for analyzing historical landslides and identifying terrain characteristics associated with landslide occurrence across the North Eastern Region (NER) of India.

The system integrates historical landslide inventories with SRTM Digital Elevation Model (DEM) data to create a geospatial feature pipeline for landslide susceptibility analysis and future risk prediction.

## Problem

The North Eastern Region of India is highly vulnerable to landslides due to its mountainous terrain, steep slopes, heavy rainfall, and complex geographical conditions.

Traditional landslide monitoring and assessment can be time-consuming and difficult to scale across large and geographically challenging regions.

This project aims to provide a data-driven approach that can help authorities and researchers analyze historical landslide patterns, identify terrain characteristics associated with landslides, generate machine-learning-ready geospatial features, assess landslide susceptibility, and provide a backend API for future integration with monitoring and visualization systems.

## Proposed Solution

The system combines historical landslide data, Digital Elevation Model data, GIS processing, and Artificial Intelligence/Machine Learning to develop a reusable landslide analysis and prediction pipeline.

The primary landslide source is the GSI Landslide Inventory, which provides historical landslide locations and associated information. The Global Landslide Catalog is used as a secondary source for comparison and supporting analysis.

SRTM DEM data is processed to derive terrain characteristics such as elevation, slope, and aspect at historical landslide locations.

The resulting geospatial features are prepared for machine-learning-based susceptibility analysis and can later be connected to a backend prediction service.

## Study Region

The project focuses on the eight states of Northeast India:

* Arunachal Pradesh
* Assam
* Manipur
* Meghalaya
* Mizoram
* Nagaland
* Sikkim
* Tripura

## System Workflow

GSI Landslide Inventory and Global Landslide Catalog are first collected and validated. The GSI inventory is then filtered to the eight Northeast Indian states.

The SRTM DEM tiles are processed and used to extract terrain characteristics corresponding to landslide locations.

These datasets are transformed into a clean, machine-learning-ready feature dataset.

The AI/ML layer can then analyze the relationship between terrain characteristics and historical landslide occurrence and provide susceptibility or risk scores.

The prediction functionality is exposed through a backend service so that it can later be integrated with a GIS dashboard or other frontend application.

## Data Sources

### GSI Landslide Inventory

The GSI Landslide Inventory is the primary historical landslide dataset used in the project.

It contains historical landslide records and information such as geographic coordinates, location information, state, and historical/date information where available.

The GSI inventory is treated as the primary ground-truth source for the project.

### Global Landslide Catalog

The Global Landslide Catalog is used as a secondary/reference landslide inventory.

It is not blindly merged with the GSI inventory. Source information is preserved so that records from different inventories can be distinguished and compared.

### SRTM Digital Elevation Model

SRTM DEM data is used to derive terrain characteristics associated with historical landslide locations.

The current implementation uses four SRTM tiles covering the available project region.

The DEM is processed to extract features such as:

* Elevation
* Slope
* Aspect
* Curvature, where applicable
* Terrain ruggedness or local relief, where applicable

### Rainfall Data

Rainfall data is currently not included in the baseline system.

The system is designed with an optional rainfall feature layer so that GPM IMERG or another reliable rainfall source can be integrated later.

No synthetic or fabricated rainfall values are used.

## AI/ML Component

The AI/ML component uses geospatial terrain characteristics extracted from the SRTM DEM and historical landslide locations.

The initial feature set includes elevation, slope, and aspect, with additional terrain-derived features added where appropriate.

The system is designed to support machine-learning models such as:

* Logistic Regression
* Random Forest
* XGBoost

Model selection and evaluation will use appropriate metrics rather than relying only on accuracy.

For a real-world geospatial application, spatially aware validation is preferred to avoid spatial data leakage between training and testing regions.

## Geospatial Processing

The geospatial processing pipeline performs DEM validation, coordinate validation, coordinate reference system handling, DEM mosaicing where required, raster sampling, and terrain feature extraction.

Geographic coordinates are handled using WGS84 / EPSG:4326.

NoData values in the DEM are handled explicitly. If a landslide location does not fall within valid DEM coverage, the corresponding terrain features are marked as unavailable rather than assigning artificial values.

## Backend Integration

This project is designed as a backend-first AI and geospatial component.

The AI and geospatial processing modules are separated from the API layer so that the system can be integrated into an existing full-stack application.

The backend architecture contains logical services for GSI inventory processing, Global Landslide Catalog processing, DEM feature extraction, feature engineering, rainfall integration, and prediction.

The prediction service is designed as a reusable component that can receive geographic and environmental features and return a structured risk result.

Potential API endpoints include:

* GET `/api/landslides`
* GET `/api/landslides/{id}`
* POST `/api/features/generate`
* GET `/api/features/status`
* POST `/api/predict`

The exact endpoints can be adapted to the existing backend architecture.

## Example Prediction Response

A prediction endpoint can return a structured response similar to:

```json
{
  "latitude": 27.5,
  "longitude": 93.6,
  "risk_score": 0.78,
  "risk_level": "HIGH",
  "rainfall_available": false,
  "features_used": {
    "elevation": 1240,
    "slope": 31.4,
    "aspect": 178.2
  }
}
```

The values shown above are only an example of the response structure and are not actual model predictions.

## Data Processing Pipeline

The complete processing pipeline follows:

Raw Datasets → Data Extraction → Data Validation → NER Filtering → DEM Processing → Terrain Feature Extraction → Feature Engineering → ML Dataset → Model → Prediction Service → Backend API

The pipeline maintains a separation between raw data, processed data, and machine-learning features.

Expensive operations such as PDF extraction and DEM processing are cached so that the entire dataset does not need to be processed repeatedly.

## Project Structure

```text
backend/
│
├── app/
│   ├── api/
│   ├── services/
│   ├── models/
│   ├── schemas/
│   ├── utils/
│   └── config/
│
├── data/
│   ├── raw/
│   ├── processed/
│   └── features/
│
├── scripts/
│   ├── extract_gsi.py
│   ├── validate_data.py
│   ├── process_dem.py
│   └── generate_features.py
│
├── tests/
│
├── requirements.txt
└── README.md
```

## Key Design Principles

The system follows several important design principles.

The GSI inventory remains the primary landslide source, while the Global Landslide Catalog is treated as a secondary reference source.

Raw datasets are preserved separately from processed datasets.

Missing data is handled explicitly instead of being silently replaced with fabricated values.

Repeated coordinates are not automatically considered duplicate landslide events because multiple landslides can occur at the same location over different periods.

Terrain values are extracted directly from the available SRTM data.

Rainfall remains an optional feature and can be integrated later without redesigning the complete pipeline.

The backend and AI components are modular so that additional data sources and models can be incorporated in the future.

## Expected Impact

The project aims to transform historical landslide records and terrain information into useful geospatial intelligence for Northeast India.

The resulting system can potentially support disaster management authorities, government agencies, researchers, infrastructure planners, and emergency response teams by helping them analyze areas associated with higher landslide susceptibility.

The backend architecture also provides a foundation for integrating the AI model with an interactive GIS dashboard in the future.

## Future Enhancements

The system can be extended with additional environmental and real-time information.

Potential future enhancements include rainfall-triggered landslide analysis, real-time weather integration, satellite imagery, temporal landslide forecasting, real-time risk monitoring, explainable AI, interactive GIS susceptibility maps, field reporting, and emergency-response integration.

Rainfall data can be added through the optional rainfall feature layer once a reliable data source is available.

## Current Limitations

The current prototype has several limitations.

Some GSI records do not contain exact event dates. Therefore, date-dependent environmental features cannot be calculated for every historical event.

The currently available SRTM tiles do not necessarily cover every possible location within the entire Northeast region.

Historical landslide inventories can contain reporting, geographic, and sampling biases.

The absence of rainfall data means that the current baseline does not model rainfall-triggered landslide events.

Model performance must be evaluated using appropriate spatial validation before making real-world deployment claims.

The system should therefore be considered a prototype/research implementation rather than a certified operational early-warning system.

## Project Status

The project is currently under development.

Completed or implemented components include:

* GSI landslide inventory extraction
* NER region filtering
* Landslide data validation
* SRTM DEM validation
* Terrain feature extraction pipeline
* Geospatial feature engineering
* Backend-oriented architecture
* Optional rainfall integration architecture

Planned or ongoing components include:

* Final ML model development
* Spatial model validation
* Backend prediction integration
* GIS visualization
* Optional rainfall integration
* Production deployment

## Technology Stack

### AI / Machine Learning

Python, Pandas, NumPy, Scikit-learn, XGBoost

### Geospatial

GeoPandas, Rasterio, SRTM DEM, GIS processing, coordinate reference systems

### Backend

Python backend, REST APIs, modular service architecture

### Data Sources

GSI Landslide Inventory, Global Landslide Catalog, and SRTM Digital Elevation Model

## Disclaimer

This project is developed as a prototype/research implementation.

Predictions generated by the system should not be treated as official disaster warnings or as a replacement for expert geological assessment.
