/**
 * Thin client for the Python risk-model service (src/api/app.py).
 * Node 18+ has global fetch — no extra dependency needed.
 *
 * Usage:
 *   const { getRisk, getCoverage, checkHealth } = require('./landslideClient');
 *   const risk = await getRisk(25.57, 91.88, '2024-07-14');
 */

const BASE_URL = process.env.LANDSLIDE_API_URL || 'http://localhost:8000';

class LandslideApiError extends Error {
  constructor(message, status, detail) {
    super(message);
    this.name = 'LandslideApiError';
    this.status = status;   // e.g. 422 = point outside DEM coverage
    this.detail = detail;
  }
}

async function getRisk(latitude, longitude, date) {
  const res = await fetch(`${BASE_URL}/predict`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ latitude, longitude, date }),
  });

  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new LandslideApiError(
      body.detail || `Risk API request failed (${res.status})`,
      res.status,
      body.detail
    );
  }
  return res.json(); // { risk_probability, risk_level, top_factors, rainfall_data_used }
}

async function getCoverage() {
  const res = await fetch(`${BASE_URL}/coverage`);
  if (!res.ok) throw new LandslideApiError('Failed to fetch DEM coverage', res.status);
  return res.json(); // { dem_tiles: [{ file, bounds: [minLon, minLat, maxLon, maxLat] }] }
}

async function checkHealth() {
  const res = await fetch(`${BASE_URL}/health`);
  return res.json(); // { status, model_loaded, rainfall_data_available }
}

module.exports = { getRisk, getCoverage, checkHealth, LandslideApiError };
