# Packaging Windows

## Portable (`PDFuse.exe` + JRE)

Depuis Linux/WSL, avec Java 21, Maven, `curl`, `unzip` et `zip` :

```bash
bash packaging/windows/build-portable.sh
```

Le script :

1. construit `target/pdfuse.jar` ;
2. télécharge Launch4j 3.50 (cache `target/windows-build/`) ;
3. télécharge Temurin JRE 21 Windows x64 (cache) ;
4. enveloppe le jar dans `target/windows/PDFuse.exe` ;
5. produit `target/PDFuse-windows-0.6.0-portable.zip`.

Le zip contient `PDFuse.exe` et `jre/` : aucun Java n'est requis sur la machine
cible. L'exe n'est pas signé ; SmartScreen peut afficher un avertissement.

## Installeur `.exe`

`jpackage` ne cross-compile pas : l'installeur doit être construit sur Windows.
Le workflow `.github/workflows/windows-exe.yml` s'en charge sur un runner
`windows-latest` :

1. pousser le dépôt ;
2. GitHub → Actions → **Windows installer** → **Run workflow** ;
3. récupérer l'artefact `PDFuse-windows` :
   - `PDFuse-0.6.0.exe` (installeur) ;
   - `PDFuse-windows-portable.zip` (app-image).

Le workflow construit aussi une app-image, utile comme fallback si WiX n'est
pas disponible sur le runner.
