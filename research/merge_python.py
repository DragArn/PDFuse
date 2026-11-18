"""Merge le corpus avec pikepdf, pypdf, PyMuPDF ; mesure temps/taille/validité."""
import json
import os
import sys
import time
import traceback

import pikepdf
import pymupdf
from pypdf import PdfWriter

HERE = os.path.dirname(os.path.abspath(__file__))
CORPUS = os.path.join(HERE, "corpus")
OUT = os.path.join(HERE, "out")
os.makedirs(OUT, exist_ok=True)


def run(fn, dst, inputs):
    t0 = time.perf_counter()
    try:
        fn(dst, inputs)
        dt = (time.perf_counter() - t0) * 1000
        size = os.path.getsize(dst)
        problems = []
        try:
            with pikepdf.open(dst) as pdf:
                problems = [str(p) for p in pdf.check_pdf_syntax()]
        except Exception as e:
            problems = [f"OPEN_FAIL: {e}"]
        return {"ok": True, "ms": round(dt, 1), "bytes": size,
                "qpdf_check": problems[:8], "error": None}
    except Exception as e:
        return {"ok": False, "ms": round((time.perf_counter() - t0) * 1000, 1),
                "bytes": None, "qpdf_check": None,
                "error": f"{type(e).__name__}: {e}"}


def merge_pikepdf(dst, inputs):
    out = pikepdf.new()
    for p in inputs:
        with pikepdf.open(p) as src:
            # API dédiée : préserve AcroForm, calques, bookmarks, liens internes
            out.add_pages_from(src)
    out.save(dst, linearize=True)


def merge_pypdf(dst, inputs):
    w = PdfWriter()
    for p in inputs:
        w.append(p)
    with open(dst, "wb") as fh:
        w.write(fh)


def merge_pymupdf(dst, inputs):
    out = pymupdf.open()
    for p in inputs:
        src = pymupdf.open(p)
        if src.needs_pass:
            src.authenticate("")
        out.insert_pdf(src, links=True, annots=True, widgets=True)
        src.close()
    out.save(dst, garbage=3, deflate=True)
    out.close()


if __name__ == "__main__":
    scenario = sys.argv[1] if len(sys.argv) > 1 else "clean"
    names = ["a.pdf", "b.pdf", "c.pdf"] if scenario == "clean" else \
            ["a.pdf", "b.pdf", "c_encrypted.pdf"]
    inputs = [os.path.join(CORPUS, n) for n in names]
    results = {}
    for label, fn in [("pikepdf", merge_pikepdf), ("pypdf", merge_pypdf),
                      ("pymupdf", merge_pymupdf)]:
        dst = os.path.join(OUT, f"{label}_{scenario}.pdf")
        results[label] = run(fn, dst, inputs)
        print(label, results[label])
    with open(os.path.join(OUT, f"results_{scenario}.json"), "w") as fh:
        json.dump(results, fh, indent=2)
