# Évaluation des librairies PDF open source — projet PDFuse

Date : 2026-11-18
Objet : choisir le moteur de fusion PDF pour un utilitaire GUI.
Méthode : documentation + **benchmark reproductible** (corpus piégé, cf. `research/`).

## 1. Protocole

Corpus généré par `research/make_corpus.py` : 3 PDF de 2 pages contenant chacun

- un outline (TOC) à 2 niveaux,
- 2 liens (1 GoTo interne, 1 URI externe),
- 2 annotations (surlignage + note),
- 1 champ de formulaire AcroForm (`customer_name`),
- 1 pièce jointe embarquée,
- des métadonnées,
- des labels de pages romains (i, ii).

Variante `c_encrypted.pdf` : AES-256 (R6), mot de passe utilisateur vide.
Variante `broken.pdf` : xref/startxref tronqués (PDF récupéré d'un téléchargement interrompu).

Vérification croisée : inventaire PyMuPDF + contrôle structurel qpdf (`pikepdf.check_pdf_syntax`),
afin qu'un lecteur tolérant ne masque pas un fichier invalide.

Versions testées : pypdf 7.1.1, pikepdf 10.18.2 (qpdf), PyMuPDF 2.1, PDFBox 3.0.7,
pdfcpu 0.16.4, pdf-lib 1.17.1, pypdfium2 5.15.2.

## 2. Résultat du merge à froid (6 pages attendues)

| Moteur | Pages | TOC | Liens internes | Annots | Formulaires | Pièces jointes | Labels | Structure |
|---|---:|---:|---|---:|---:|---:|---|---|
| **pikepdf / qpdf** | 6 | 6 | corrects (1,3,5) | 6 | 3 | 0 | 0 | propre |
| **pypdf** | 6 | 6 | corrects (1,3,5) | 6 | 3 | 0 | 0 | propre |
| **PyMuPDF** | 6 | **0** | corrects (1,3,5) | 6 | 3 | 0 | 0 | propre |
| **PDFBox** | 6 | 6 | corrects (1,3,5) | 6 | 3 | 3 (noms en collision) | **3 plages** | warnings réparables |
| **pdfcpu** | 6 | 6* | corrects (1,3,5) | 6 | 3 | 1 | 1 plage | propre |
| **pdf-lib** | 6 | **0** | **cassés (0,0,0)** | 6 | 3 | 0 | 0 | propre |

\* pdfcpu ajoute par défaut un bookmark « wrapper » par fichier (9 entrées) ; `--bookmark-mode preserve` restaure 6 entrées.

Constats :

- **pikepdf 10** : `add_pages_from()` (nouvelle API) préserve formulaires, calques, outlines et liens internes.
  L'ancien `pages.extend()` perd les formulaires — ne pas l'utiliser.
  Les pièces jointes et les labels de pages ne sont pas repris (recopiables via l'API `attachments`).
- **pypdf** : très bon à froid, mais casse sur un xref tronqué.
- **PyMuPDF** : ne fusionne pas les TOC automatiquement (à recopier à la main), perd pièces jointes/labels/metadata.
- **PDFBox** : la meilleure fidélité globale (TOC, labels multi-plages, pièces jointes). qpdf signale
  un xref stream incomplet et un tree non trié, corrigés à la lecture — pas bloquant mais à normaliser en sortie.
- **pdfcpu** : rapide et propre, mais labels/pièces jointes partiellement perdus, pas de rendu.
- **pdf-lib** : inadapté à la fusion documentaire (TOC, labels, attachments perdus, liens internes faux).

## 3. Cas difficiles

| Moteur | Entrée chiffrée AES-256 | Entrée corrompue (xref tronqué) | Rendu / vignettes |
|---|---|---|---|
| pikepdf / qpdf | OK | **OK** (reconstruction xref) | non |
| pypdf | OK (avec `cryptography`) | **Échec** | non |
| PyMuPDF | OK | OK | **oui** |
| PDFBox | OK | OK | **oui** |
| pdfcpu | **refuse** (pré-décrypter) | OK | non |
| pdf-lib | **sortie corrompue** | **Échec** | non |
| pypdfium2 | — (rendu seul) | tolérant | **oui** |

## 4. Performance (10 × 100 pages = 1000 pages)

| Moteur | Temps | Pic RSS | Taille sortie |
|---|---:|---:|---:|
| pdfcpu | 0,08 s | 26 Mo | 214 Ko |
| pdf-lib | 0,33 s (interne) | 88 Mo | 217 Ko |
| PyMuPDF | 0,56 s | 93 Mo | 354 Ko |
| pikepdf | 0,57 s | 93 Mo | 378 Ko |
| pypdf | 0,68 s | 94 Mo | 374 Ko |
| PDFBox | 1,13 s | 127 Mo | 216 Ko |

Aucun goulot d'étranglement à cette échelle. pikepdf ne recompresse pas par défaut
(activer `compress_streams` / object streams pour réduire la taille).

## 5. Licences — point bloquant

| Librairie | Licence | Utilisable dans un logiciel fermé distribué ? |
|---|---|---|
| pypdf | BSD-3-Clause | oui |
| pikepdf (qpdf) | MPL-2.0 | oui (copyleft au fichier, pas au produit) |
| pypdfium2 | BSD-3 / Apache-2.0 | oui |
| PDFBox | Apache-2.0 | oui |
| pdfcpu | Apache-2.0 | oui |
| pdf-lib | MIT | oui |
| **PyMuPDF** | **AGPL-3.0** ou commercial | **non** sans ouvrir le code source |

PySide6 : LGPL-3.0 (liaison dynamique acceptable pour un produit fermé).

## 6. Verdict

### « La plus puissante » au sens large
**PyMuPDF** : rendu, extraction, édition, formulaires, rédaction, OCR, conversion.
Mais **AGPL-3.0** : inutilisable dans un utilitaire distribué fermé sans acheter une licence Artifex.
De plus, son merge ne reprend pas les outlines : même la « plus puissante » doit être complétée.

### « La plus puissante sous licence permissive »
**Apache PDFBox 3.0.7** : la seule à couvrir merge fidèle + labels + pièces jointes + rendu +
formulaires + signature + rédaction + validation PDF/A, en Apache-2.0.

### « Le meilleur moteur de fusion »
**qpdf, via pikepdf** : conçu pour l'opération, reconstruction de fichiers abîmés, AES, rapide,
MPL-2.0. C'est le bon cœur pour un outil dont la fonction principale est le merge.

## 7. Architecture recommandée

**Option A — Python, recommandée si le produit peut être fermé et léger**

- Cœur merge : `pikepdf` (MPL-2.0) + recopie metadata / labels / pièces jointes.
- Aperçu et vignettes : `pypdfium2` (BSD-3/Apache-2.0).
- GUI : `PySide6` (LGPL-3.0).
- Packaging : PyInstaller ou Nuitka.

Toutes les licences sont compatibles avec une distribution propriétaire.

**Option B — Java, si l'on veut une seule librairie tout-en-un**

- `PDFBox 3` (Apache-2.0) pour merge, rendu, formulaires, labels, pièces jointes.
- GUI : JavaFX. Packaging `jpackage`.
- Contrepartie : démarrage JVM, écosystème GUI moins agile, sortie à normaliser (xref stream).

**Option C — Tauri/Rust, si l'on veut un binaire natif très léger**

- `pdfium-render` (bindings PDFium) pour le rendu + `pdfcpu`/`lopdf` pour le merge.
- Plus de travail d'intégration, écosystème PDF moins mûr.

**À éviter pour ce projet** : pdf-lib comme moteur de fusion (fidélité insuffisante, corruption
sur entrées chiffrées), PyMuPDF si le code n'est pas AGPL.

## 8. Décisions à trancher avant le développement

1. Le projet sera-t-il distribué en open source (AGPL acceptable) ou fermé (licences permissives obligatoires) ?
2. Stack visée : Python/PySide6, Java/JavaFX, ou Tauri/Rust ?
3. Périmètre v1 : merge seul, ou merge + réorganisation + suppression/rotation + aperçu + compression ?
4. OS cibles et mode de distribution (installeur, portable, Microsoft Store, etc.) ?
