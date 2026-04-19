import protobuf from 'protobufjs'

let rootPromise = null

export function loadProto() {
  if (!rootPromise) {
    rootPromise = protobuf.load('/src/proto/message.proto')
  }
  return rootPromise
}

export async function encodeMessage(wrapperObj) {
  const root = await loadProto()
  const Wrapper = root.lookupType('com.chatlite.proto.MessageWrapper')
  const err = Wrapper.verify(wrapperObj)
  if (err) throw Error(err)
  const message = Wrapper.create(wrapperObj)
  const buffer = Wrapper.encode(message).finish()
  return buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.length)
}

export async function decodeMessage(arrayBuffer) {
  const root = await loadProto()
  const Wrapper = root.lookupType('com.chatlite.proto.MessageWrapper')
  const uint8 = new Uint8Array(arrayBuffer)
  const msg = Wrapper.decode(uint8)
  const obj = Wrapper.toObject(msg, { longs: String, enums: String, defaults: true })
  return obj
}

export default { loadProto, encodeMessage, decodeMessage }

