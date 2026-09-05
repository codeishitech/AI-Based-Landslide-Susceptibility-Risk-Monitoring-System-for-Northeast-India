"""
Extract the full GSI-style landslide inventory table out of the PDF into a CSV.

The PDF (data/raw/landslides/landslide_report.pdf) is 904 pages / ~300MB and a
naive single-pass extraction can be slow / memory-heavy, so this processes it
in page chunks and appends to the output CSV incrementally (safe to re-run —
it recreates the output file each run).

Run:
    pip install pdfplumber --break-system-packages
    python src/data/extract_gsi_pdf.py

Output:
    data/interim/gsi_full_raw.csv   (all-India, unfiltered — 36,071 records as
                                      of the current landslide_report.pdf)
"""
import csv
import os
import time

import pdfplumber

PDF_PATH = os.path.join(os.path.dirname(__file__), "..", "..", "data", "raw",
                         "landslides", "landslide_report.pdf")
OUT_PATH = os.path.join(os.path.dirname(__file__), "..", "..", "data", "interim",
                         "gsi_full_raw.csv")
CHUNK_SIZE = 200  # pages per chunk — keeps memory bounded on very large PDFs


def extract_chunk(pdf, start, end, header_written):
    rows_out = []
    header = None
    for i in range(start, min(end, len(pdf.pages))):
        tables = pdf.pages[i].extract_tables()
        if not tables:
            continue
        for row in tables[0]:
            if not row:
                continue
            if row[0] and row[0].strip() == "Sl.No.":
                header = header or row
                continue
            if row[0] and row[0].strip().isdigit():
                rows_out.append(row)
    return header, rows_out


def main():
    if os.path.exists(OUT_PATH):
        os.remove(OUT_PATH)
    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)

    t0 = time.time()
    total_rows = 0
    header_written = False
    with pdfplumber.open(PDF_PATH) as pdf:
        n_pages = len(pdf.pages)
        for start in range(0, n_pages, CHUNK_SIZE):
            end = start + CHUNK_SIZE
            header, rows = extract_chunk(pdf, start, end, header_written)
            with open(OUT_PATH, "a", newline="", encoding="utf-8") as f:
                w = csv.writer(f)
                if not header_written and header:
                    w.writerow(header)
                    header_written = True
                w.writerows(rows)
            total_rows += len(rows)
            print(f"pages {start}-{min(end, n_pages)}/{n_pages}  "
                  f"+{len(rows)} rows  total={total_rows}  "
                  f"elapsed={time.time()-t0:.1f}s", flush=True)

    print(f"\nDone. {total_rows} total records written to {OUT_PATH}")


if __name__ == "__main__":
    main()
