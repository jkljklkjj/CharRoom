export type SocialVisibility = 'PUBLIC' | 'FRIENDS' | 'SELF'
export interface SocialSettings {
  userId: number; bio: string; interests: string[]; city: string; intent: string
  cover: 'sunset' | 'ocean' | 'forest'; visibility: SocialVisibility; discoverable: boolean; mutualDiscovery: boolean
}
export interface SocialEdit extends Omit<SocialSettings, 'userId'> { username: string; signature: string }
export interface SocialProfile {
  id: string; username: string; avatarUrl?: string; avatarKey?: string; signature?: string
  bio?: string; interests?: string[]; city?: string; intent?: string; cover: string
  relation: 'SELF' | 'FRIEND' | 'PENDING' | 'STRANGER'; detailsVisible: boolean
  settings?: SocialSettings; reason?: 'MUTUAL' | 'INTEREST' | 'EXPLORE'
}
export interface SocialDirectory { users: SocialProfile[]; nextCursor: number | null }
export interface DailyAnswer { id: string; content: string; createdAt: string; author: SocialProfile }
export interface DailyQuestion { day: string; question: string; myAnswer: string; answers: DailyAnswer[]; nextCursor: string | null }
export interface EncounterMessage { seq: number; author: string; content: string; createdAt: number; messageId: string }
export interface EncounterState {
  state: 'IDLE' | 'QUEUED' | 'PREPARING' | 'ACTIVE' | 'ENDED'
  roomId?: string; serverTime?: number; endsAt?: number; question?: string; mySlot?: string; reason?: string
  myReady?: boolean; extendRequested?: boolean; exchangeRequested?: boolean; extensions?: number; exchanged?: boolean
  messages: EncounterMessage[]; nextSeq?: number; peer?: SocialProfile
}
export interface EncounterAction { action: string; roomId?: string; messageId?: string; content?: string }
