/**
 * Example Express route that your frontend calls — it proxies to the Python
 * risk API so your frontend never talks to the Python service directly
 * (keeps LANDSLIDE_API_URL server-side, lets you add auth/caching/logging).
 *
 * Mount in your existing app:
 *   const landslideRoutes = require('./integration/node/landslideRoutes');
 *   app.use('/api/landslide', landslideRoutes);
 *
 * Then your frontend calls: GET/POST /api/landslide/risk
 */
const express = require('express');
const { getRisk, getCoverage, checkHealth, LandslideApiError } = require('./landslideClient');

const router = express.Router();

router.get('/health', async (req, res) => {
  try {
    res.json(await checkHealth());
  } catch (err) {
    res.status(502).json({ error: 'Risk model service unreachable' });
  }
});

router.get('/coverage', async (req, res) => {
  try {
    res.json(await getCoverage());
  } catch (err) {
    res.status(502).json({ error: 'Risk model service unreachable' });
  }
});

router.post('/risk', async (req, res) => {
  const { latitude, longitude, date } = req.body;
  if (latitude == null || longitude == null || !date) {
    return res.status(400).json({ error: 'latitude, longitude and date are required' });
  }
  try {
    const result = await getRisk(latitude, longitude, date);
    res.json(result);
  } catch (err) {
    if (err instanceof LandslideApiError && err.status === 422) {
      // point outside current DEM coverage - a real, expected case, not a server error
      return res.status(422).json({ error: err.detail });
    }
    console.error('Landslide risk lookup failed:', err);
    res.status(502).json({ error: 'Risk model service unreachable' });
  }
});

module.exports = router;
