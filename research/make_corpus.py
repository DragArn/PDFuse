"""Construit un corpus PDF 'piégé' pour mesurer la fidélité des merges.

Chaque document contient :
  - 2 pages de texte
  - un outline (TOC) à 2 niveaux
  - un lien interne (GoTo) + un lien externe (URI)
  - une annotation Highlight + une Note
  - un champ de formulaire texte (si l'API le permet)
  - des métadonnées
  - un labels de pages (i, ii, ...)
  - une pièce jointe embarquée
Le 4e document est chiffré AES-256.
"""
import os
import pymupdf
import pikepdf

OUT = os.path.join(os.path.dirname(__file__), "corpus")
os.makedirs(OUT, exist_ok=True)


def make_doc(name: str, title: str) -> str:
    doc = pymupdf.open()
    for i in range(2):
        page = doc.new_page()
        page.insert_text((72, 90), f"{title} - page {i + 1}", fontsize=16)
        page.insert_text((72, 120), "Lorem ipsum dolor sit amet, " * 8, fontsize=10)

    # Les 2 pages existent : les liens GoTo peuvent maintenant cibler la page 2.
    page = doc[0]
    page.insert_link({
        "kind": pymupdf.LINK_GOTO, "from": pymupdf.Rect(72, 140, 220, 155),
        "page": 1, "to": pymupdf.Point(72, 90),
    })
    page.insert_link({
        "kind": pymupdf.LINK_URI, "from": pymupdf.Rect(72, 160, 220, 175),
        "uri": "https://example.org/",
    })
    try:
        annot = page.add_highlight_annot(pymupdf.Rect(72, 118, 300, 132))
        annot.set_info(content="highlight note")
        annot.update()
    except Exception as e:
        print("  [!] highlight:", e)
    try:
        page.add_text_annot(pymupdf.Point(320, 118), "sticky note")
    except Exception as e:
        print("  [!] text annot:", e)

    doc.set_toc([
        [1, f"{title} chapter", 1],
        [2, f"{title} section", 2],
    ])
    doc.set_metadata({
        "title": title, "author": "PDFuse research",
        "subject": "capability corpus", "keywords": "merge,outline,forms",
        "creator": "make_corpus.py", "producer": "PyMuPDF",
    })

    # Champ de formulaire (API variable selon versions)
    try:
        page = doc[0]
        w = pymupdf.Widget()
        w.rect = pymupdf.Rect(72, 200, 272, 220)
        w.field_name = "customer_name"
        w.field_type = pymupdf.PDF_WIDGET_TYPE_TEXT
        w.field_value = "ACME"
        page.add_widget(w)
    except Exception as e:
        print("  [!] widget:", e)

    # Pièce jointe
    try:
        doc.embfile_add("readme.txt", b"embedded attachment payload\n",
                        filename="readme.txt", desc="test attachment")
    except Exception as e:
        print("  [!] embfile:", e)

    # Labels de pages
    try:
        doc.set_page_labels([{"startpage": 0, "prefix": "", "style": "r",
                              "firstpagenum": 1}])
    except Exception as e:
        print("  [!] labels:", e)

    path = os.path.join(OUT, name)
    doc.save(path, garbage=3, deflate=True)
    doc.close()
    return path


paths = [
    make_doc("a.pdf", "Alpha"),
    make_doc("b.pdf", "Bravo"),
    make_doc("c.pdf", "Charlie"),
]

# Chiffrement AES-256 du 3e document (user password vide => ouvrable sans mdp)
with pikepdf.open(paths[2]) as pdf:
    pdf.save(os.path.join(OUT, "c_encrypted.pdf"),
             encryption=pikepdf.Encryption(owner="owner-secret", user="", R=6))
print("corpus:", sorted(os.listdir(OUT)))
