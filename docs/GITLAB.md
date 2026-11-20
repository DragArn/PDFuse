# Publication GitLab

Le dépôt Git local pointe encore sur GitHub (`origin`). Pour publier sur GitLab :

## 1. Créer le projet et pousser

```bash
git remote add gitlab https://gitlab.com/<namespace>/<projet>.git
git push gitlab main
```

## 2. Taguer une version

```bash
git tag 0.6.0
git push gitlab 0.6.0
```

Le tag déclenche le pipeline :

- `build-portable` : construit `PDFuse.exe` + JRE et le zip portable ;
- `publish-release` : téléverse l'archive et l'exe dans le **Generic Package
  Registry**, puis crée la release GitLab.

## 3. Télécharger

GitLab → **Deploy → Releases → PDFuse 0.6.0** :

| Asset | Contenu |
|---|---|
| `PDFuse-windows-portable.zip` | `PDFuse.exe` + `jre/` — recommandé |
| `PDFuse.exe` | exe seul, nécessite un JRE 21 |

Le zip est aussi disponible dans **Deploy → Package Registry**.

## 4. Projet privé ou public

- Projet **privé** : les liens de package demandent une connexion GitLab
  (membres uniquement).
- Projet **public** : la release est visible, mais le Generic Package Registry
  peut encore exiger un token selon la configuration. Pour un téléchargement
  anonyme garanti, publier aussi via GitLab Pages ou rendre le projet public.

## 5. Token CI

`CI_JOB_TOKEN` suffit normalement pour uploader dans le package registry.
Si le pipeline renvoie `403`, créer un **Project Access Token** avec le scope
`api`, puis l'ajouter en variable CI masquée `GITLAB_TOKEN`. Le job utilise
automatiquement `PRIVATE-TOKEN` si `GITLAB_TOKEN` est défini.

## 6. Limites

- Le runner GitLab.com est Linux : il produit le **portable Launch4j**, pas
  l'installeur Windows `jpackage`.
- L'exe reste **non signé** : SmartScreen peut avertir.
- Le téléchargement est soumis à l'authentification si le projet est privé.
