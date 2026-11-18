const fs = require('fs');
const { PDFDocument } = require('pdf-lib');

(async () => {
  const [out, ...inputs] = process.argv.slice(2);
  const t0 = process.hrtime.bigint();
  try {
    const dst = await PDFDocument.create();
    for (const p of inputs) {
      const src = await PDFDocument.load(fs.readFileSync(p), { ignoreEncryption: true });
      const pages = await dst.copyPages(src, src.getPageIndices());
      pages.forEach(pg => dst.addPage(pg));
    }
    fs.writeFileSync(out, await dst.save());
    const ms = Number(process.hrtime.bigint() - t0) / 1e6;
    console.log(JSON.stringify({ ok: true, ms: +ms.toFixed(1), bytes: fs.statSync(out).size }));
  } catch (e) {
    console.log(JSON.stringify({ ok: false, error: `${e.name}: ${e.message}` }));
  }
})();
