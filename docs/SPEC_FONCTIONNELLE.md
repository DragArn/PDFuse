# Spécification fonctionnelle — PDFuse

| Élément | Valeur |
|---|---|
| Document | Spécification fonctionnelle |
| Version | 1.0 |
| Date | 2026-11-18 |
| S'applique à | PDFuse 0.3.0 |
| Référence d'usage | [README.md](../README.md) |
| Statut | Base de référence pour la v0.3.0 |

## 1. Objet

PDFuse est un utilitaire destiné à **fusionner des documents PDF**. Il accepte
également des **images**, converties en pages PDF avant fusion.

La cible produit est un utilitaire graphique. L'incrément livré en 0.3.0 est le
**backend batch** qui servira de moteur à cette GUI.

Ce document décrit le comportement attendu (« quoi »), pas l'implémentation
(« comment »). Le README reste la référence pour le build, l'installation et
les exemples d'usage.

### Hiérarchie documentaire

- `README.md` : construction, syntaxe CLI, exemples, limites connues.
- `docs/SPEC_FONCTIONNELLE.md` (ce document) : comportement attendu et
  critères d'acceptation.
- `src/test/java/com/pdfuse/PdfMergerTest.java` : vérité exécutable du
  comportement.

En cas de divergence, le comportement observé et les tests font foi ; le README
et ce document doivent alors être corrigés ensemble.

## 2. Périmètre

### 2.1 Inclus dans la v0.3.0

- exécution en ligne de commande (batch, scriptable) ;
- fusion de 2 à N entrées ;
- entrées PDF et images mélangées ;
- écriture d'un PDF unique ;
- préservation de la structure documentaire des PDF ;
- gestion explicite des erreurs et codes de sortie ;
- suite de tests exécutable via Maven.

### 2.2 Exclu de la v0.3.0

- interface graphique ;
- réorganisation, rotation, suppression ou extraction de pages ;
- mots de passe utilisateur non vides ;
- OCR, signature numérique, rédaction de contenu ;
- compression ou optimisation paramétrable ;
- correction de l'orientation EXIF ;
- formats WebP et HEIC ;
- accès réseau.

## 3. Acteurs et cas d'usage

**Acteur principal** : utilisateur batch (script shell, CI, ou futur appel
interne depuis la GUI).

| ID | Cas d'usage | Résultat attendu |
|---|---|---|
| CU-01 | Fusionner plusieurs PDF | Un PDF unique, pages dans l'ordre des entrées |
| CU-02 | Fusionner PDF et images | Chaque image devient une page du PDF final |
| CU-03 | Remplacer une sortie existante | Écrasement uniquement avec `--force` |
| CU-04 | Fusionner un PDF chiffré (mot de passe utilisateur vide) | Fusion réussie |
| CU-05 | Diagnostiquer un échec | Message sur `stderr` et code de sortie non nul |

## 4. Exigences fonctionnelles

| ID | Exigence | Priorité | Critère de vérification |
|---|---|---|---|
| FR-01 | Accepter entre 2 et N entrées | Must | 2 et 3 entrées fusionnées ; aucune limite codée en dur |
| FR-02 | Conserver l'ordre des entrées | Must | Page 1 du fichier 1 avant page 1 du fichier 2 |
| FR-03 | Écrire dans un fichier unique désigné par `-o` | Must | Sortie absente du disque sans `-o` |
| FR-04 | Détecter les images par leur extension, sans tenir compte de la casse | Must | `photo.JPG` reconnu, `doc.pdf` non |
| FR-05 | Convertir chaque image en une page PDF | Must | Une image = une page, orientation adaptée |
| FR-06 | Préserver la structure des PDF | Must | Signets, liens, annotations, formulaires, labels |
| FR-07 | Lever les collisions de champs de formulaire | Must | `customer_name`, `customer_name_2`, `_3` |
| FR-08 | Lever les collisions de pièces jointes | Must | `readme.txt`, `readme_2.txt`, `_3` |
| FR-09 | Lire les PDF chiffrés à mot de passe utilisateur vide | Must | Fusion sans demande de mot de passe |
| FR-10 | Récupérer les PDF légèrement corrompus | Should | xref/startxref tronqué : fusion réussie |
| FR-11 | Écrire de façon atomique | Must | Aucune sortie partielle en cas d'échec |
| FR-12 | Écraser la sortie uniquement avec `--force` | Must | Refus sans `--force`, fichier existant intact |
| FR-13 | Interdire d'écraser une entrée | Must | `-o` égal à une entrée : refus |
| FR-14 | Fournir `--help` et `--version` | Must | Code de sortie 0, texte affiché |
| FR-15 | Fournir des codes de sortie stables | Must | `0` succès, `1` erreur, `2` usage |
| FR-16 | Produire des messages d'erreur actionnables | Must | Chemin et cause identifiables |

### FR-01 — Nombre d'entrées

Le programme exige **au moins deux entrées**. Une seule entrée est refusée :
un merge à une entrée n'a pas de sens fonctionnel.

### FR-02 — Ordre

L'ordre des pages du PDF produit est exactement l'ordre des entrées sur la
ligne de commande, puis l'ordre des pages à l'intérieur de chaque entrée.

### FR-03 — Sortie

La sortie est obligatoire et fournie par `-o` / `--output`. Le répertoire
parent doit exister. Le programme ne crée pas de répertoire.

### FR-04 — Détection des images

La détection se fait par **extension**, insensible à la casse :

```
jpg, jpeg, png, gif, bmp, tif, tiff
```

Toute autre extension est traitée comme un PDF. La conversion réelle vérifie
ensuite le contenu binaire (signature) pour choisir le décodeur.

### FR-05 — Conversion d'image

Chaque image produit **une page A4** :

- portrait si `largeur <= hauteur`, paysage sinon ;
- image centrée, ratio conservé ;
- marge de 18 pt sur les quatre côtés ;
- fond blanc au rendu ;
- transparence PNG préservée via un masque alpha.

Le JPEG est embarqué sans recompression (DCTDecode). Le PNG est encodé en
Flate, sans perte.

### FR-06 — Fidélité documentaire

Pour chaque PDF fusionné, le programme préserve :

- l'ordre, la taille et la rotation des pages ;
- les signets (outline) ;
- les liens internes `GoTo`, remappés vers la page absolue correcte ;
- les annotations ;
- les champs de formulaire (AcroForm) ;
- les labels de pages, décalés du nombre de pages déjà présentes ;
- les métadonnées du **premier document PDF** rencontré ;
- les pièces jointes.

### FR-07 — Champs de formulaire homonymes

Lorsqu'un champ du document entrant porte un nom déjà présent, il est renommé
avec un suffixe numérique avant fusion :

```
customer_name → customer_name_2 → customer_name_3
```

Le suffixe est choisi pour rester unique. Les widgets associés restent liés au
champ renommé.

### FR-08 — Pièces jointes homonymes

Les clés de l'arbre `/EmbeddedFiles` sont rendues uniques en conservant
l'extension :

```
readme.txt → readme_2.txt → readme_3.txt
```

Le nom déclaré dans la spécification de fichier (`/F`, `/UF`) est mis à jour.

### FR-09 — PDF chiffrés

Un PDF chiffré avec un mot de passe utilisateur **vide** est ouvert
automatiquement. Un mot de passe utilisateur non vide n'est pas encore géré :
la fusion échoue avec un message explicite.

### FR-10 — PDF dégradés

Un PDF dont le `xref` ou le `startxref` est tronqué est récupéré par PDFBox.
Les cas gravement corrompus échouent sans produire de sortie.

### FR-11 — Écriture atomique

Le PDF est d'abord écrit dans un fichier temporaire du répertoire de sortie,
puis déplacé vers le nom final. En cas d'échec :

- aucune sortie partielle n'est visible ;
- aucun fichier `.pdfuse-*.tmp` ne subsiste.

Les permissions du fichier final suivent l'`umask` du processus.

### FR-12 — Écrasement

Sans `--force`, une sortie existante provoque un refus et le fichier existant
n'est pas modifié. Avec `--force`, il est remplacé atomiquement.

### FR-13 — Protection des entrées

La sortie ne peut jamais être l'une des entrées, même avec `--force`.

### FR-14 / FR-15 / FR-16 — Interface et erreurs

Voir le contrat CLI en section 6.

## 5. Règles métier

| ID | Règle |
|---|---|
| BR-01 | Le nombre minimal d'entrées est 2. |
| BR-02 | L'ordre des entrées est significatif et doit être respecté. |
| BR-03 | Seules les extensions listées en FR-04 déclenchent la conversion d'image. |
| BR-04 | Une image produit exactement une page. |
| BR-05 | Une image carrée produit une page portrait. |
| BR-06 | Le ratio d'une image n'est jamais déformé. |
| BR-07 | Les métadonnées retenues sont celles du premier PDF, pas celles des images. |
| BR-08 | Les labels de pages sont décalés du nombre de pages déjà fusionnées. |
| BR-09 | Les noms de champs et de pièces jointes sont uniques dans la sortie. |
| BR-10 | Le répertoire de sortie doit exister ; il n'est pas créé. |
| BR-11 | Un échec ne doit jamais laisser de sortie partielle. |
| BR-12 | La sortie ne peut pas être une entrée. |

## 6. Contrat d'interface CLI

### 6.1 Syntaxe

```
pdfuse <input> <input> [...] -o <output.pdf> [--force]
```

`<input>` accepte un PDF ou une image.

### 6.2 Options

| Option | Obligatoire | Effet |
|---|---|---|
| `-o`, `--output` | oui | Fichier PDF de sortie |
| `-f`, `--force` | non | Remplace une sortie existante |
| `-h`, `--help` | non | Affiche l'aide, code 0 |
| `-V`, `--version` | non | Affiche la version, code 0 |

Une option inconnue commençant par `-` est une erreur d'usage (code 2).

### 6.3 Codes de sortie

| Code | Signification |
|---|---|
| `0` | Succès |
| `1` | Erreur métier : entrée absente/illisible, PDF invalide, sortie existante, etc. |
| `2` | Usage incorrect : option inconnue, `-o` manquant, moins de 2 entrées |

### 6.4 Flux

- `stdout` : résumé de succès
  `merged <n> files, <p> pages -> <chemin> (<taille>, <ms> ms)`.
- `stderr` : messages d'erreur préfixés par `pdfuse:`.

### 6.5 Exemples

```bash
# Deux PDF
java -jar target/pdfuse.jar a.pdf b.pdf -o sortie.pdf

# N PDF
java -jar target/pdfuse.jar a.pdf b.pdf c.pdf d.pdf -o sortie.pdf

# Mixte PDF + images
java -jar target/pdfuse.jar a.pdf photo.jpg scan.PNG b.pdf -o sortie.pdf

# Écrasement explicite
java -jar target/pdfuse.jar a.pdf b.pdf -o sortie.pdf --force
```

## 7. Exigences non fonctionnelles

| ID | Exigence |
|---|---|
| NFR-01 | Toutes les dépendances doivent être compatibles avec une distribution **fermée** : Apache-2.0, BSD, MIT, MPL-2.0, LGPL en liaison dynamique. Aucune dépendance AGPL. |
| NFR-02 | Java 17 ou supérieur. |
| NFR-03 | Aucun accès réseau, aucune télémétrie. |
| NFR-04 | 300 pages fusionnées en moins de 2 s sur la machine de référence. |
| NFR-05 | Suite de tests exécutable par `mvn test`, sans fichier externe. |
| NFR-06 | La sortie doit passer `pdfcpu validate -m strict`. |
| NFR-07 | Aucun fichier temporaire résiduel après succès ou échec. |
| NFR-08 | Le jar produit est autonome (`target/pdfuse.jar`). |

## 8. Critères d'acceptation

| ID | Scénario | Résultat attendu |
|---|---|---|
| AC-01 | Fusion de 2 PDF de 2 et 3 pages | 5 pages, ordre conservé, signets des deux documents |
| AC-02 | Fusion de 3 PDF | Nombre de pages = somme, `inputs = 3` |
| AC-03 | PDF + PNG paysage + JPEG portrait | 3 pages, pages 2 et 3 aux orientations attendues |
| AC-04 | Deux PDF avec le champ `customer_name` | Champs `customer_name`, `customer_name_2` |
| AC-05 | Deux PDF avec `readme.txt` en pièce jointe | Clés `readme.txt`, `readme_2.txt` |
| AC-06 | PDF chiffré AES-256, mot de passe utilisateur vide | Fusion réussie |
| AC-07 | PDF tronqué (xref/startxref) | Fusion réussie |
| AC-08 | Sortie existante sans `--force` | Code 1, fichier existant inchangé |
| AC-09 | Sortie existante avec `--force` | Code 0, fichier remplacé |
| AC-10 | Sortie égale à une entrée | Code 1, refus |
| AC-11 | Une seule entrée | Code 2, message d'usage |
| AC-12 | Image `.png` invalide | Code 1, aucune sortie créée |
| AC-13 | `--help` / `--version` | Code 0, texte attendu |
| AC-14 | Échec en cours de fusion | Aucun `.pdfuse-*.tmp` résiduel |
| AC-15 | Toute sortie produite | `pdfcpu validate -m strict` : OK |

## 9. Correspondance README ↔ spécification

| Section du README | Exigences couvertes |
|---|---|
| Build | NFR-02, NFR-08 |
| Usage | FR-01, FR-02, FR-03, FR-12, FR-13, FR-14, FR-15 |
| Images | FR-04, FR-05, BR-03 à BR-06 |
| Tests | NFR-05 |
| Ce que le merge préserve | FR-06 à FR-10 |
| Limites connues | Section 10 ci-dessous |
| Structure | NFR-08 |

## 10. Limites connues de la v0.3.0

- Mot de passe utilisateur non vide non géré.
- Orientation EXIF ignorée : une photo de smartphone peut être couchée.
- GIF animé : seule la première image est utilisée (comportement d'ImageIO).
- WebP et HEIC non supportés.
- Pas de réorganisation ni de rotation manuelle des pages.
- La sortie utilise des xref streams : qpdf émet un avertissement bénin
  (« xref entry for the xref stream itself is missing »), sans impact sur la
  validité constatée par `pdfcpu` et PDFium.
- Pas d'interface graphique dans cet incrément.

## 11. Backlog

| Priorité | Élément | Origine |
|---|---|---|
| Haute | Interface graphique (cible initiale du produit) | Demande initiale |
| Haute | `--password` par entrée | Limite FR-09 |
| Moyenne | Correction de l'orientation EXIF | Limite connue |
| Moyenne | `--image-page fit\|original` | Confort images |
| Moyenne | Réorganisation / rotation / suppression de pages | Usage GUI |
| Basse | Compression paramétrable | Optimisation |
| Basse | N-up (plusieurs pages par feuille) | Évolution |
| Basse | WebP / HEIC via plugin | Formats |

## 12. Traçabilité des décisions

| Décision | Source | État |
|---|---|---|
| Produit : utilitaire GUI de fusion de PDF | Demande initiale | Cible ; backend livré |
| Distribution fermée / propriétaire | Réponse de cadrage | Contrainte NFR-01 |
| Moteur PDFBox (Apache-2.0) | Choix technique | Implémenté |
| Backend batch à 2 fichiers | Demande | Livré en 0.1.0 |
| Passage à N fichiers | Demande | Livré en 0.2.0 |
| Sortie via `-o` obligatoire | Décision d'interface | Livré en 0.2.0 |
| Tests unitaires via Maven | Demande | Livré (15 tests) |
| Détection et conversion d'images | Demande | Livré en 0.3.0 |
| Spécification fonctionnelle | Demande | Ce document |

## 13. Glossaire

| Terme | Définition |
|---|---|
| AcroForm | Formulaire interactif PDF (champs, widgets). |
| GoTo | Action de lien interne vers une page ou une destination. |
| Label de page | Numérotation affichée (romaine, décimale, préfixe). |
| Outline | Arborescence de signets du document. |
| xref | Table ou flux des références croisées permettant de localiser les objets. |
| DCTDecode | Filtre PDF encapsulant un flux JPEG sans recompression. |
| FlateDecode | Compression sans perte (zlib) utilisée par les flux PDF. |
| SMask | Masque de transparence associé à une image. |

## 14. Références

- [README.md](../README.md) — build, usage, limites.
- [RAPPORT_LIBRAIRIES.md](../research/RAPPORT_LIBRAIRIES.md) — étude comparative
  des moteurs PDF.
- `src/main/java/com/pdfuse/` — implémentation.
- `src/test/java/com/pdfuse/PdfMergerTest.java` — tests de référence.
- Apache PDFBox 3.0.7 — documentation officielle.
