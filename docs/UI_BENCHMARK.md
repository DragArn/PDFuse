# Benchmark UI — PDFuse

| Élément | Valeur |
|---|---|
| Date | 2026-11-19 |
| Environnement | Ubuntu WSL2 / WSLg, OpenJDK 21.0.13, GTK3 |
| Candidats mesurés | Swing + FlatLaf 3.8, JavaFX 21.0.13 |
| Candidats écartés | Compose Desktop, SWT, Tauri, Electron, Vaadin |
| Verdict | **Swing + FlatLaf** pour la v1 |

## 1. Protocole

Deux prototypes strictement équivalents :

- fenêtre 1100 × 700 ;
- liste virtualisée à gauche, aperçu à droite ;
- boutons « Add files » et « Merge » ;
- glisser-déposer de fichiers ;
- 20 vignettes PDF rendues par PDFBox 3.0.7 à 96 DPI ;
- aperçu de la première page à 110 DPI ;
- même redimensionnement Java2D (bilinéaire) dans les deux prototypes ;
- PDF de test : `research/big/block.pdf`, 100 pages.

Mesures :

- **ui_ms** : initialisation de la classe → fenêtre visible (hors démarrage JVM) ;
- **render_ms** : 20 pages rendues, redimensionnées et injectées dans la liste ;
- **wall_s** : processus complet, arrêt inclus ;
- **maxrss_kb** : pic RSS mesuré par `/usr/bin/time -v` ;
- 1 warm-up puis **5 processus neufs**, exécution **séquentielle** (pas de charge parallèle), médiane retenue.

JavaFX est lancé en **module path** (`javafx.controls`, `javafx.swing`), configuration de
production. En classpath, le démarrage mesuré était d'environ 0,85 s (3 runs), soit ~10 %
de plus, avec l'avertissement « Unsupported JavaFX configuration ».

## 2. Résultats

| Métrique | Swing + FlatLaf | JavaFX (module path) | Écart |
|---|---:|---:|---:|
| Démarrage jusqu'à la fenêtre | **386 ms** (381–407) | 760 ms (720–765) | ×1,97 |
| 20 vignettes (rendu + scale) | **700 ms** (665–732) | 818 ms (751–866) | ×1,17 |
| Pic RSS | **209 Mo** (204–211) | 372 Mo (356–386) | ×1,78 |
| Temps mural complet | **1,61 s** (1,54–1,68) | 2,05 s (1,96–2,12) | ×1,27 |
| Dépendances runtime | **4,7 Mo** | 12 Mo | ×2,55 |
| App-image `jpackage` | **88 Mo** | 101 Mo | ×1,15 |

Tailles d'app-image non compressées (runtime jlink inclus).

### Lecture

- **Démarrage** : Swing est deux fois plus rapide. C'est le point le plus visible
  pour un utilitaire lancé ponctuellement.
- **Rendu des vignettes** : JavaFX n'est que 17 % plus lent ici, alors qu'il tourne
  en **pipeline logiciel** (ES2 a échoué sous WSLg). Sur un GPU réel, l'écart peut
  s'inverser. Mais PDFBox domine le coût : le choix du toolkit ne doit pas se
  faire sur ce chiffre.
- **Mémoire** : écart structurel de ~160 Mo (scene graph + Prism). Pour un
  utilitaire qui traite des PDF volumineux, c'est significatif.
- **Packaging** : JavaFX ajoute 13 Mo et impose des jmods + module path ;
  Swing tient dans le JDK.

## 3. Matrice qualitative

| Critère | Swing + FlatLaf | JavaFX |
|---|---|---|
| Licence | GPL-2.0 + Classpath Exception | GPL-2.0 + Classpath Exception |
| FlatLaf / thème | Apache-2.0, thèmes modernes, dark | CSS complet |
| Look moderne | bon avec FlatLaf | très bon nativement |
| API | verbeuse, impérative | déclarative, FXML, Scene Builder |
| Animations | limitées | excellentes |
| HiDPI | bon | excellent |
| Intégration `BufferedImage` | directe (`ImageIcon`) | conversion `SwingFXUtils` |
| Liste virtualisée | `JList` / `JTable` | `ListView` / `TableView` |
| Drag & drop | `TransferHandler` | API événements simple |
| Packaging | `jpackage` simple | jmods + module path |
| Écosystème | très mature | mature |
| Distribution fermée | oui | oui |

## 4. Candidats écartés

| Framework | Pourquoi |
|---|---|
| **Compose Desktop** | Non mesuré (Gradle/Kotlin absents de l'environnement). Apache-2.0, UI moderne, mais ajoute Kotlin, Gradle et un runtime plus lourd. Pertinent seulement si l'équipe veut Kotlin. |
| **SWT** | Widgets natifs, EPL-2.0, mais packaging par OS et API datée ; aucun avantage décisif ici. |
| **Tauri** | UI web légère, mais le backend Java devrait être exposé en sidecar/HTTP : processus supplémentaire, RPC, packaging JRE. Complexité sans gain pour un utilitaire local. |
| **Electron** | ~150 Mo et consommation mémoire élevée ; surdimensionné. |
| **Vaadin** | UI web server-side ; pertinent pour une application multi-utilisateurs, pas pour un utilitaire de bureau. |

## 5. Recommandation

**Swing + FlatLaf pour la v1.**

1. Démarrage ~2× plus rapide, mémoire ~1,8× plus faible.
2. Intégration directe avec `BufferedImage`/PDFBox, sans conversion.
3. Packaging plus simple : pas de jmods, pas de module path.
4. FlatLaf fournit un rendu moderne, un thème sombre et le HiDPI.
5. Licences compatibles avec une distribution fermée.

JavaFX reste le bon choix si l'interface devient riche : animations, CSS,
composants custom, Scene Builder, vue document complexe. Ce n'est pas le besoin
d'un merge utilitaire.

## 6. Architecture UI proposée

```
pdfuse-ui/
  MainFrame.java          fenêtre, menu, barre d'état
  FileListPanel.java      JList<DocumentItem> + TransferHandler
  PreviewPanel.java       aperçu PDF (PDFRenderer -> BufferedImage)
  ThumbnailService.java   SwingWorker + cache LRU (path + mtime + page)
  MergeWorker.java        appelle MergeController hors EDT
  Theme.java              FlatLaf light/dark
```

Règles :

- EDT uniquement pour l'UI ; rendu et merge dans des `SwingWorker` ;
- cache de vignettes pour éviter de rerendre au scroll ;
- `DocumentItem` = chemin + plage + vignette + état ;
- progression et annulation sur le merge ;
- réutilisation directe de `MergeController` (aucun changement backend).

## 7. Limites du benchmark

- WSLg force JavaFX en pipeline logiciel ; les chiffres ne représentent pas
  Windows/macOS avec accélération GPU.
- PDF de test textuel de 100 pages ; un PDF scanné sera plus lent pour les deux.
- 5 exécutions par framework ; variance non négligeable sur le rendu.
- Les app-images sont non compressées ; les installeurs finaux varieront.
- L'ergonomie (drag & drop, animations, confort) n'est pas notée quantitativement.

## 8. Reproduction

```bash
# Build
mvn -f research/ui-bench/swing/pom.xml package
mvn -f research/ui-bench/javafx/pom.xml package

# Swing
java -cp "research/ui-bench/swing/target/classes:research/ui-bench/swing/target/deps/*" \
  bench.swing.Main research/big/block.pdf --bench

# JavaFX (module path)
cd research/ui-bench/javafx
CP="target/classes:$(find target/deps -maxdepth 1 -name '*.jar' ! -name 'javafx-*' -printf '%p:')"
java --module-path target/fxmods --add-modules javafx.controls,javafx.swing \
  -cp "$CP" bench.fx.Main ../../big/block.pdf --bench
```

Les prototypes sont dans `research/ui-bench/` et ne font pas partie du jar PDFuse.
