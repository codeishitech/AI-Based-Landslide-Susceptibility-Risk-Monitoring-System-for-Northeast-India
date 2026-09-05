"""
PHASE 1 — Data inspection for SIH26001 landslide risk pipeline.

Inspects, without assuming anything:
  - the GLC-format landslide CSV  (data/raw/landslides/Global_Landslide_Catalog_Export_rows.csv)
  - the field-validated landslide inventory PDF (data/raw/landslides/landslide_report.pdf)
  - the SRTM DEM tiles (data/raw/dem/*.tif)

Run:
    pip install pdfplumber pypdf pillow --break-system-packages
    python src/data/inspect_data.py

No rainfall inspection is included yet: no NASA GPM IMERG file has been
supplied. See DATA_INVENTORY.md for what's missing before Phase 3 can start.
"""
import csv
import glob
import os
from collections import Counter

from PIL import Image

Image.MAX_IMAGE_PIXELS = None

RAW = os.path.join(os.path.dirname(__file__), "..", "..", "data", "raw")

NER_STATES = {
    "Arunachal Pradesh", "Assam", "Manipur", "Meghalaya",
    "Mizoram", "Nagaland", "Sikkim", "Tripura",
}


def inspect_glc_csv(path):
    print(f"\n=== GLC CSV: {path} ===")
    with open(path, encoding="utf-8") as f:
        reader = csv.DictReader(f)
        header = reader.fieldnames
        print("Columns:", header)
        country_counts = Counter()
        india_admin = Counter()
        n = 0
        for row in reader:
            n += 1
            country_counts[row.get("country_name", "")] += 1
            if row.get("country_name") == "India":
                india_admin[row.get("admin_division_name", "")] += 1
        print("Total rows:", n)
        print("India rows:", country_counts.get("India", 0))
        print("India admin divisions (raw, may include diacritics/variants):")
        for k, v in india_admin.most_common(40):
            print(f"   {k!r}: {v}")


def inspect_pdf_inventory(path, sample_every=15):
    import pdfplumber

    print(f"\n=== PDF inventory: {path} ===")
    with pdfplumber.open(path) as pdf:
        n_pages = len(pdf.pages)
        print("Pages:", n_pages)
        first_table = pdf.pages[0].extract_tables()[0]
        print("Header row:", first_table[1])

        state_counts = Counter()
        for i in range(0, n_pages, sample_every):
            tables = pdf.pages[i].extract_tables()
            if not tables:
                continue
            for row in tables[0]:
                if row and row[0] and row[0].strip().isdigit():
                    state_counts[(row[2] or "").strip()] += 1

        print(f"\nState distribution (sampled every {sample_every} pages, NOT exhaustive):")
        for k, v in sorted(state_counts.items(), key=lambda x: -x[1]):
            flag = "  <== NER" if k in NER_STATES else ""
            print(f"   {v:5d}  {k}{flag}")


def inspect_dem_tiles(dem_dir):
    print(f"\n=== DEM tiles: {dem_dir} ===")
    for path in sorted(glob.glob(os.path.join(dem_dir, "*.tif"))):
        im = Image.open(path)
        tags = im.tag_v2
        pixel_scale = tags.get(33550)
        tiepoint = tags.get(33922)
        nodata = tags.get(42113)
        print(f"{os.path.basename(path)}: size={im.size}, "
              f"pixel_scale={pixel_scale}, upper_left(lon,lat)={tiepoint[3:5] if tiepoint else None}, "
              f"nodata={nodata}")


if __name__ == "__main__":
    csv_path = os.path.join(RAW, "landslides", "Global_Landslide_Catalog_Export_rows.csv")
    pdf_path = os.path.join(RAW, "landslides", "landslide_report.pdf")
    dem_dir = os.path.join(RAW, "dem")

    if os.path.exists(csv_path):
        inspect_glc_csv(csv_path)
    if os.path.exists(pdf_path):
        inspect_pdf_inventory(pdf_path)
    if os.path.isdir(dem_dir):
        inspect_dem_tiles(dem_dir)
