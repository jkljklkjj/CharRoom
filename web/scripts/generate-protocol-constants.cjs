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
  return `export const ${name} = Object.freeze(${JSON.stringify(values, null, 2)})`
})
const devices = root.lookupEnum('com.chatlite.proto.DeviceType').values
const deviceNames = Object.keys(devices).filter(name => name !== 'DEVICE_TYPE_UNSPECIFIED')
declarations.push('export const DEVICE_TYPE_NUM = Object.freeze({\n' +
  deviceNames.map(name => `  ${name.toLowerCase()}: PROTO_DEVICE_TYPE.${name}`).join(',\n') + '\n})')
const content = '// Generated from src/proto/*.proto. Run npm run generate:protocol; do not edit by hand.\n\n' +
  declarations.join('\n\n') + '\n'
const output = path.join(web, 'src/proto/constants.js')
if (process.argv.includes('--check')) {
  const current = fs.existsSync(output) ? fs.readFileSync(output, 'utf8').replace(/\r\n/g, '\n') : ''
  if (current !== content) {
    console.error('Protocol constants are stale; run npm run generate:protocol.')
    process.exitCode = 1
  }
} else {
  fs.writeFileSync(output, content)
  console.log('Generated protocol constants from the Web proto sources.')
}
