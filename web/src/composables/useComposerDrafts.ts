import { reactive } from 'vue'
import { ComposerDrafts } from '../services/composerDrafts'

// Shared across desktop/mobile remounts; every key still includes the account and conversation.
export const drafts = reactive(new ComposerDrafts())
export const composerSubmissions = reactive(new Set<string>())
