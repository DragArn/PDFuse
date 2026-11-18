"""Analyse la fidélité des PDF fusionnés : pages, signets, liens, annots,
formulaires, pièces jointes, labels, métadonnées, chiffrement.

Utilise PyMuPDF pour l'inventaire et pikepdf (moteur qpdf) pour la validité
structurelle, afin de ne pas dépendre d'un seul lecteur tolérant aux erreurs.
"""
import glob
import json
import os
import sys

import pikepdf
import pymupdf

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "out")

EXPECTED = {"pages": 6, "toc": 6, "links": 6, "annots": 6,
            "widgets": 3, "embfiles": 3}


def inspect(path):
    r = {"file": os.path.basename(path), "bytes": os.path.getsize(path)}
    try:
        with pikepdf.open(path) as pdf:
            r["qpdf_problems"] = [str(p) for p in pdf.check_pdf_syntax()][:5]
            r["encrypted"] = pdf.is_encrypted
    except Exception as e:
        r["qpdf_problems"] = [f"OPEN_FAIL: {e}"]
    try:
        d = pymupdf.open(path)
        r["pages"] = d.page_count
        r["toc"] = len(d.get_toc())
        r["toc_entries"] = d.get_toc()
        links, annots, widgets, targets = 0, 0, 0, []
        for page in d:
            for l in page.get_links():
                links += 1
                if l.get("kind") == pymupdf.LINK_GOTO:
                    targets.append(l.get("page"))
            annots += len(list(page.annots() or []))
            widgets += len(list(page.widgets() or []))
        r["links"] = links
        r["internal_targets"] = targets
        r["annots"] = annots
        r["widgets"] = widgets
        try:
            r["embfiles"] = len(d.embfile_names())
        except Exception:
            r["embfiles"] = 0
        try:
            r["labels"] = d.get_page_labels()
        except Exception as e:
            r["labels"] = f"ERR {e}"
        m = d.metadata or {}
        r["meta_title"] = m.get("title")
        r["meta_author"] = m.get("author")
        d.close()
    except Exception as e:
        r["read_error"] = f"{type(e).__name__}: {e}"
    return r


if __name__ == "__main__":
    files = sys.argv[1:] or sorted(glob.glob(os.path.join(OUT, "*.pdf")))
    rows = []
    for f in files:
        row = inspect(f)
        row["score"] = {k: (row.get(k) == v) for k, v in EXPECTED.items()}
        row["score"]["valid"] = not row.get("qpdf_problems")
        rows.append(row)
    for row in rows:
        print(json.dumps(row, ensure_ascii=False))
