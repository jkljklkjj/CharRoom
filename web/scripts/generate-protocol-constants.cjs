const fs = require('node:fs')
const path = require('node:path')
const protobuf = require('protobufjs')

const web = path.resolve(__dirname, '..')
const root = protobuf.loadSync(path.join(web, 'src/proto/message.proto'))
const tables = {
  MSG_TYPE: 'MessageWrapperType',
  PROTO_DEVICE_TYPE: 'DeviceType',
  CONVERSATION_TYPE: 'ConversationType',
  AGENT_STREAM_TYPE: 'AgentStreamType'
}
const declarations = Object.entries(tables).map(([name, type]) => {
  const values = root.lookupEnum(`com.chatlite.proto.${type}`).values
  return `export const ${name} = Object.freeze(${JSON.stringify(values, null, 2)} as const)`
})
const devices = root.lookupEnum('com.chatlite.proto.DeviceType').values
const deviceNames = Object.keys(devices).filter(name => name !== 'DEVICE_TYPE_UNSPECIFIED')
declarations.push('export const DEVICE_TYPE_NUM = Object.freeze({\n' +
  deviceNames.map(name => `  ${name.toLowerCase()}: PROTO_DEVICE_TYPE.${name}`).join(',\n') + '\n} as const)')
const content = '// Generated from src/proto/*.proto. Run npm run generate:protocol; do not edit by hand.\n\n' +
  declarations.join('\n\n') + '\n'
root.resolveAll()
const namespace = root.lookup('com.chatlite.proto')
function fieldType(field) {
  if (field.resolvedType instanceof protobuf.Type) return field.resolvedType.name
  if (field.resolvedType instanceof protobuf.Enum) return Object.values(field.resolvedType.values).join(' | ')
  if (field.type === 'string') return 'string'
  if (field.type === 'bool') return 'boolean'
  if (field.type === 'bytes') return 'Uint8Array'
  if (field.type.includes('64')) return 'WireLong'
  return 'number'
}
const interfaces = Object.values(namespace.nested).filter(type => type instanceof protobuf.Type).map(type =>
  `export interface ${type.name} {\n` + type.fieldsArray.map(field => {
    const value = fieldType(field)
    const result = field.map ? `Record<string, ${value}>` : field.repeated ? `(${value})[]` : value
    return `  ${field.name}?: ${result}`
  }).join('\n') + '\n}')
const contracts = '// Generated from src/proto/*.proto. Run npm run generate:protocol; do not edit by hand.\n\n' +
  'export type WireLong = string | number\n\n' + interfaces.join('\n\n') + '\n'
for (const [name, source] of [['constants.ts', content], ['messages.ts', contracts]]) {
  const output = path.join(web, 'src/proto', name)
  if (process.argv.includes('--check')) {
    const current = fs.existsSync(output) ? fs.readFileSync(output, 'utf8').replace(/\r\n/g, '\n') : ''
    if (current !== source) {
      console.error(`Protocol ${name} is stale; run npm run generate:protocol.`)
      process.exitCode = 1
    }
  } else {
    fs.writeFileSync(output, source)
  }
}
