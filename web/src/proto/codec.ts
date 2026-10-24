import protobuf from 'protobufjs'
import type { MessageWrapper } from './messages'

let rootPromise: Promise<protobuf.Root> | null = null
export function loadProto(): Promise<protobuf.Root> {
  rootPromise ??= protobuf.load('/proto/message.proto').catch(error => {
    rootPromise = null
    throw error
  })
  return rootPromise
}
export async function encode(wrapper: MessageWrapper): Promise<ArrayBuffer> {
  const type = (await loadProto()).lookupType('com.chatlite.proto.MessageWrapper')
  const message = type.fromObject(wrapper)
  const error = type.verify(message)
  if (error) throw new Error(error)
  return Uint8Array.from(type.encode(message).finish()).buffer
}
export async function decode(buffer: ArrayBuffer): Promise<MessageWrapper> {
  const type = (await loadProto()).lookupType('com.chatlite.proto.MessageWrapper')
  return type.toObject(type.decode(new Uint8Array(buffer)), {
    longs: String, enums: Number, defaults: true
  }) as MessageWrapper
}
