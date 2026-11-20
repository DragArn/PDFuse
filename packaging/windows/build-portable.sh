#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$ROOT/target/windows"
CACHE="$ROOT/target/windows-build"
L4J="$CACHE/launch4j"
JRE_ZIP="$CACHE/temurin-jre21-win.zip"
VERSION="$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' "$ROOT/pom.xml" | head -1)"

mkdir -p "$OUT" "$CACHE"

echo "==> Build PDFuse $VERSION"
mvn -q -f "$ROOT/pom.xml" -DskipTests package

if [ ! -x "$L4J/launch4jc" ]; then
    echo "==> Download Launch4j 3.50"
    curl -sL --max-time 180 -o "$CACHE/launch4j.tgz" \
        'https://downloads.sourceforge.net/project/launch4j/launch4j-3/3.50/launch4j-3.50-linux-x64.tgz'
    rm -rf "$L4J"
    mkdir -p "$L4J"
    tar xzf "$CACHE/launch4j.tgz" -C "$L4J" --strip-components=1
    sed -i 's/\r$//' "$L4J/launch4jc"
    chmod +x "$L4J/launch4jc"
fi

if [ ! -x "$OUT/jre/bin/javaw.exe" ]; then
    echo "==> Download Temurin JRE 21 (Windows x64)"
    curl -sL --max-time 300 -o "$JRE_ZIP" \
        'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse'
    rm -rf "$OUT/jre" "$CACHE/jre-tmp"
    mkdir -p "$CACHE/jre-tmp"
    unzip -q "$JRE_ZIP" -d "$CACHE/jre-tmp"
    mv "$CACHE/jre-tmp"/* "$OUT/jre"
    rm -rf "$CACHE/jre-tmp"
fi

# Le jar principal a pour main class le CLI. On emballe une copie avec le
# manifeste GUI pour que l'exe ouvre directement la fenêtre.
GUI_JAR="$OUT/pdfuse-ui.jar"
cp "$ROOT/target/pdfuse.jar" "$GUI_JAR"
jar --update --file "$GUI_JAR" --main-class com.pdfuse.ui.PdfUseApp

CONFIG="$OUT/launch4j.xml"
cat > "$CONFIG" <<EOF
<launch4jConfig>
  <headerType>gui</headerType>
  <jar>$GUI_JAR</jar>
  <dontWrapJar>false</dontWrapJar>
  <outfile>$OUT/PDFuse.exe</outfile>
  <errTitle>PDFuse</errTitle>
  <chdir>.</chdir>
  <priority>normal</priority>
  <downloadUrl>https://adoptium.net/</downloadUrl>
  <stayAlive>false</stayAlive>
  <restartOnCrash>false</restartOnCrash>
  <icon>$ROOT/packaging/windows/pdfuse.ico</icon>
  <jre>
    <path>jre</path>
    <requiresJdk>false</requiresJdk>
    <requires64Bit>true</requires64Bit>
    <minVersion>21</minVersion>
  </jre>
  <versionInfo>
    <fileVersion>$VERSION.0</fileVersion>
    <txtFileVersion>$VERSION</txtFileVersion>
    <fileDescription>PDFuse - merge PDF and images</fileDescription>
    <copyright>PDFuse</copyright>
    <productVersion>$VERSION.0</productVersion>
    <txtProductVersion>$VERSION</txtProductVersion>
    <productName>PDFuse</productName>
    <internalName>PDFuse</internalName>
    <originalFilename>PDFuse.exe</originalFilename>
    <companyName>PDFuse</companyName>
  </versionInfo>
</launch4jConfig>
EOF

echo "==> Wrap jar into PDFuse.exe"
"$L4J/launch4jc" "$CONFIG"

echo "==> Package portable zip"
ZIP="$ROOT/target/PDFuse-windows-$VERSION-portable.zip"
rm -f "$ZIP"
if command -v zip >/dev/null 2>&1; then
    (cd "$OUT" && zip -qr "$ZIP" PDFuse.exe jre)
else
    python3 -m zipfile -c "$ZIP" "$OUT/PDFuse.exe" "$OUT/jre"
fi

echo "OK: $ZIP"
file "$OUT/PDFuse.exe"
du -sh "$ZIP"
