export interface ReplyDraft { sender: string; content: string; messageId?: string }
export interface DraftSnapshot { key: string; text: string; reply: ReplyDraft | null; revision: number }
/** Account/conversation owned drafts; completion may only clear the exact submitted revision. */
export class ComposerDrafts {
  private drafts = new Map<string, DraftSnapshot>()
  snapshot(key: string): DraftSnapshot {
    const draft = this.drafts.get(key) || { key, text: '', reply: null, revision: 0 }
    return { ...draft, reply: draft.reply ? { ...draft.reply } : null }
  }
  edit(key: string, text: string, reply: ReplyDraft | null) {
    const previous = this.snapshot(key)
    this.drafts.set(key, { key, text, reply: reply ? { ...reply } : null, revision: previous.revision + 1 })
  }
  commit(snapshot: DraftSnapshot) {
    if (this.snapshot(snapshot.key).revision === snapshot.revision) this.edit(snapshot.key, '', null)
  }
}
export async function submitDraft(persist: () => Promise<unknown>, enqueue: () => Promise<unknown>, commit: () => void) {
  await persist()
  await enqueue()
  commit()
}
