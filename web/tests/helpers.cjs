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
  const dynamic = new Map()
  function moduleFor(file) {
    if (cache.has(file)) return cache.get(file)
    const module = new vm.SourceTextModule(readTs(file), { context, identifier: file,
      initializeImportMeta(meta) { meta.url = pathToFileURL(file).href; meta.env = {} },
      importModuleDynamically(name, from) {
        const target = linker(name, from)
        if (!dynamic.has(target)) dynamic.set(target, (async () => {
          if (target.status === 'unlinked') await target.link(linker)
          if (target.status === 'linked') await target.evaluate()
          return target
        })())
        return dynamic.get(target)
      } })
    cache.set(file, module)
    return module
  }
  const entry = moduleFor(file)
  function linker(name, from) {
    if (name in stubs) {
      if (cache.has(name)) return cache.get(name)
      const values = stubs[name]
      const stub = new vm.SyntheticModule(Object.keys(values), function () {
        for (const [key, value] of Object.entries(values)) this.setExport(key, value)
      }, { context })
      cache.set(name, stub)
      return stub
    }
    const resolved = path.resolve(path.dirname(from.identifier), name)
    return moduleFor(resolved.endsWith('.ts') ? resolved : resolved + '.ts')
  }
  await entry.link(linker)
  await entry.evaluate()
  return { context, module: entry, exports: entry.namespace }
}
module.exports = { readTs, transpile, loadTs }
