// Generated from src/proto/*.proto. Run npm run generate:protocol; do not edit by hand.

export const MSG_TYPE = Object.freeze({
  "MESSAGE_WRAPPER_TYPE_UNSPECIFIED": 0,
  "LOGIN": 1,
  "LOGOUT": 2,
  "CHAT": 3,
  "GROUP_CHAT": 4,
  "AGENT_CHAT": 5,
  "AGENT_CHAT_STREAM": 6,
  "CHECK": 7,
  "HEARTBEAT": 8,
  "ACK": 9,
  "RESPONSE": 10,
  "STREAM_INIT_ACK": 11,
  "SYNC_HINT": 12,
  "FRIEND_ACCEPTED": 13
} as const)

export const PROTO_DEVICE_TYPE = Object.freeze({
  "DEVICE_TYPE_UNSPECIFIED": 0,
  "MOBILE": 1,
  "DESKTOP": 2,
  "WEB": 3,
  "CLI": 4
} as const)

export const CONVERSATION_TYPE = Object.freeze({
  "CONVERSATION_TYPE_UNSPECIFIED": 0,
  "PRIVATE": 1,
  "GROUP": 2,
  "AGENT": 3
} as const)

export const AGENT_STREAM_TYPE = Object.freeze({
  "STREAM_TEXT": 0,
  "STREAM_TOOL_CALL": 1,
  "STREAM_TOOL_RESULT": 2,
  "STREAM_USAGE": 3,
  "STREAM_DONE": 4,
  "STREAM_ERROR": 5
} as const)

export const DEVICE_TYPE_NUM = Object.freeze({
  mobile: PROTO_DEVICE_TYPE.MOBILE,
  desktop: PROTO_DEVICE_TYPE.DESKTOP,
  web: PROTO_DEVICE_TYPE.WEB,
  cli: PROTO_DEVICE_TYPE.CLI
} as const)
