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

## Signature Windows

Un exe non signé déclenche SmartScreen (« Windows a protégé votre PC ») et
peut être signalé par Defender, surtout avec un wrapper Launch4j.

### Production

Il faut un certificat de signature de code **OV** ou **EV** (Sectigo, Certum,
DigiCert…) ou **Azure Trusted Signing**. Un certificat EV donne immédiatement
une réputation SmartScreen ; un OV la construit progressivement.

Configurer les secrets GitHub :

| Secret | Contenu |
|---|---|
| `WINDOWS_CERT_PFX_BASE64` | PFX encodé en base64 |
| `WINDOWS_CERT_PASSWORD` | mot de passe du PFX |

Le workflow signe alors l'installeur et l'exe de l'app-image avec `signtool`
(horodatage DigiCert). Sans ces secrets, les artefacts restent non signés.

### Test local (auto-signé)

Sur Windows, après extraction du zip :

```powershell
powershell -ExecutionPolicy Bypass -File .\sign-local.ps1 -File .\PDFuse.exe
```

Le script crée un certificat auto-signé, l'importe dans
`Cert:\CurrentUser\TrustedPublisher` et `Root`, puis signe l'exe. Cela supprime
l'avertissement « éditeur inconnu » **sur cette machine uniquement**.
SmartScreen peut encore avertir : il n'y a pas de réputation.

Ne jamais distribuer ce certificat ni l'utiliser pour une release publique.

### Contournement immédiat

Dans l'ordre :

```powershell
Unblock-File .\PDFuse-windows-0.6.0-portable.zip
Expand-Archive .\PDFuse-windows-0.6.0-portable.zip -DestinationPath PDFuse
Unblock-File .\PDFuse\PDFuse.exe
```

Puis, si SmartScreen persiste : **Informations complémentaires → Exécuter
quand même**. Si Defender supprime l'exe, le restaurer et le signaler comme
faux positif, ou utiliser l'installeur `jpackage` (moins susceptible d'être
pris pour un wrapper).
