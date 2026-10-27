const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const { pathToFileURL } = require('node:url')
function transpile(source, fileName = 'fixture.ts') {
  return ts.transpileModule(source, { fileName, compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext, verbatimModuleSyntax: true
  } }).outputText
}
function readTs(file) { return transpile(fs.readFileSync(file, 'utf8'), file) }
async function loadTs(file, globals = {}, stubs = {}) {
  const context = vm.createContext({ console, ArrayBuffer, Uint8Array, Blob, URL, String, Number,
    setTimeout, clearTimeout, setInterval, clearInterval, AbortController, AbortSignal, ...globals })
  const cache = new Map()
  function moduleFor(file) {
    if (cache.has(file)) return cache.get(file)
    const module = new vm.SourceTextModule(readTs(file), { context, identifier: file,
      initializeImportMeta(meta) { meta.url = pathToFileURL(file).href; meta.env = {} } })
    cache.set(file, module)
    return module
  }
  const entry = moduleFor(file)
  await entry.link((name, from) => {
    if (name in stubs) {
      const values = stubs[name]
      return new vm.SyntheticModule(Object.keys(values), function () {
        for (const [key, value] of Object.entries(values)) this.setExport(key, value)
      }, { context })
    }
    const resolved = path.resolve(path.dirname(from.identifier), name)
    return moduleFor(resolved.endsWith('.ts') ? resolved : resolved + '.ts')
  })
  await entry.evaluate()
  return { context, module: entry, exports: entry.namespace }
}
module.exports = { readTs, transpile, loadTs }
