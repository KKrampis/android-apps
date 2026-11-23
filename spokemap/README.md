# SpokeMap

A bike map for New York City and northern New Jersey built only from open data. No account,
no API keys, no analytics.

## What it shows

| Layer | Source | How it loads |
| --- | --- | --- |
| Bike lanes (on by default) | OpenStreetMap through the [Overpass API](https://overpass-api.de) (falls back to `overpass.private.coffee`) | For the area on screen once you zoom in to level 12, in 0.05° grid cells, so panning back is free |
| Citi Bike (on by default) | Citi Bike [GBFS feed](https://gbfs.citibikenyc.com/gbfs/gbfs.json), covering NYC, Jersey City and Hoboken | Every 60 seconds while the app is open |
| NYC DOT network | NYC Open Data "New York City Bike Routes" (`mzxg-pwib`), with the [ArcGIS mirror](https://services9.arcgis.com/hsPsr2XddCBblVhb/arcgis/rest/services/NYC%20Bicycle%20Lanes/FeatureServer) as a fallback | Downloaded whole once, cached for 7 days |
| NJ road stress | NJTPA [Bike Level of Traffic Stress](https://share-open-data-njtpa.hub.arcgis.com/datasets/NJTPA::bike-level-of-traffic-stress) (`gis.njtpa.org/.../BicycleLOSMapX/MapServer/0`), field `FinalLOS` 1 to 5 | For the area on screen at zoom 12 and up |

OpenStreetMap and NYC DOT lanes share one color scheme: deep green for off-street paths and
greenways, bright green for protected lanes, blue for painted lanes, amber for sharrows and
signed routes. NYC DOT lines are dashed so you can turn both on and compare.

Tap a Citi Bike dot for classic bikes, e-bikes and open docks. Tap a lane for its street, type
and source. The bike button jumps to the nearest station with a bike (from your location if you
allowed it, otherwise from the middle of the map).

The base map is [OpenFreeMap](https://openfreemap.org) vector tiles (OpenStreetMap data),
rendered with [MapLibre](https://maplibre.org). It follows the system light or dark theme.

## Privacy

Your location is only used on the phone, for the blue dot and the nearest-bike button. The app
talks directly to the servers listed above and sends them the map area you are looking at.
There is no backend of its own.

## Build

```
./gradlew testReleaseUnitTest assembleRelease
```

The [workflow](../.github/workflows/spokemap.yml) builds the APK on every push that touches
`spokemap/` and publishes a release from `main`. Signing uses the same repository secrets as
the other apps (see the top-level README).
