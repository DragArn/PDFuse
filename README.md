# PDFuse

Backend Java de fusion de plusieurs PDF et images, basé sur Apache PDFBox 3.

> Spécification fonctionnelle : [docs/SPEC_FONCTIONNELLE.md](docs/SPEC_FONCTIONNELLE.md)

## Build

```bash
mvn package
```

Produit `target/pdfuse.jar` (jar exécutable, PDFBox inclus).

## Usage

```bash
java -jar target/pdfuse.jar premier.pdf second.pdf -o sortie.pdf
java -jar target/pdfuse.jar a.pdf photo.jpg scan.png c.pdf -o sortie.pdf
java -jar target/pdfuse.jar rapport.pdf:2-5 annexe.pdf:1,4 -o sortie.pdf
```

Options :

| Option | Effet |
|---|---|
| `-o`, `--output` | fichier de sortie (obligatoire) |
| `-f`, `--force` | écrase la sortie si elle existe |
| `-h`, `--help` | aide |
| `-V`, `--version` | version |

Codes de sortie : `0` succès, `1` erreur, `2` usage incorrect.

## Images

Une entrée dont l'extension est `jpg`, `jpeg`, `png`, `gif`, `bmp`, `tif` ou
`tiff` (insensible à la casse) est convertie en une page PDF avant le merge.
Les listes mixtes PDF + images sont acceptées.

Chaque image occupe une page A4 portrait ou paysage selon son orientation,
centrée avec une marge de 18 pt et ratio conservé. Le JPEG est embarqué sans
recompression ; le PNG reste lossless et conserve sa transparence.

## Extraction de pages

Chaque entrée accepte une plage via `chemin:plage`. Les plages sont 1-based,
inclusives, séparées par des virgules :

```bash
java -jar target/pdfuse.jar rapport.pdf:2-5 annexe.pdf:1,4 -o sortie.pdf
```

- `rapport.pdf:2-5` ne garde que les pages 2 à 5 ;
- `annexe.pdf:1,4` garde les pages 1 et 4, dans l'ordre du document ;
- sans suffixe, tout le document est fusionné.

Les index sont triés et dédupliqués. Une plage hors du document est refusée
avec un message indiquant le nombre de pages réel.

Lorsqu'une extraction réduit un document, les références devenues invalides
sont nettoyées : signets hors plage supprimés, liens internes vers des pages
retirées supprimés, champs de formulaire orphelins retirés. Les labels de pages
du document extrait sont retirés (ils décriraient l'ancienne pagination).
Métadonnées et pièces jointes sont conservées.

## Tests

```bash
mvn test
```

La suite (`src/test/java/com/pdfuse/PdfMergerTest.java`) génère ses propres PDF
via PDFBox : aucun fichier externe n'est requis. Elle couvre l'ordre des pages,
N fichiers, la préservation des signets/labels/formulaires/pièces jointes, le
remapping des liens internes, les PDF chiffrés, la conversion d'images
(orientation de page, extension en majuscules), l'extraction de plages
(ordre, liens/signets/formulaires hors plage), ainsi que les refus (sortie
existante, sortie égale à une entrée, moins de deux entrées, entrée absente,
PDF invalide) et l'absence de fichier temporaire résiduel.

Le rapport détaillé est produit dans `target/surefire-reports/`.

## Ce que le merge préserve

- pages, ordre, taille et rotation ;
- signets (outline) de tous les documents ;
- liens internes (GoTo) remappés vers les bonnes pages ;
- annotations et champs de formulaire, avec renommage sémantique des champs
  homonymes (`customer_name`, `customer_name_2`) ;
- labels de pages (`i, ii`, puis `1, 2`…) ;
- métadonnées du premier document ;
- pièces jointes, avec renommage des collisions (`readme.txt`, `readme_2.txt`).

Les PDF chiffrés avec un mot de passe utilisateur vide sont lus. Les PDF
légèrement corrompus (xref/startxref manquant) sont récupérés par PDFBox.

## Limites connues

- Pas encore d'option pour un mot de passe utilisateur non vide.
- Au moins deux fichiers en entrée (un seul n'a pas de sens pour un merge).
- L'orientation EXIF des photos (tag `Orientation`) n'est pas appliquée :
  une photo de smartphone peut sortir couchée.
- Une extraction retire les labels de pages du document réduit.
- Les destinations nommées (`/Dests`) ne sont pas filtrées ; un lien nommé
  vers une page retirée peut rester inerte.
- Fusionner un PDF signé invalide sa signature : aucun avertissement n'est
  encore émis.
- La sortie utilise des xref streams ; qpdf émet un avertissement bénin
  (« xref entry for the xref stream itself is missing »), validé par
  `pdfcpu validate -m strict` et lisible par PDFium/Acrobat.

## Structure

```
src/main/java/com/pdfuse/
  Main.java           CLI, parsing des arguments, codes de sortie
  MergeController.java traduction des entrées CLI et orchestration
  MergeInput.java     chemin + plage optionnelle
  PageRange.java      parsing/validation des plages 1-based
  PdfMerger.java      cœur : merge, extraction, nettoyage, écriture atomique
  ImageToPdf.java     image -> PDF d'une page (A4, marge, ratio)
  MergeException.java erreur métier
```

L'écriture est atomique : le PDF est produit dans un fichier temporaire du
même répertoire puis déplacé, afin de ne jamais laisser une sortie tronquée.
