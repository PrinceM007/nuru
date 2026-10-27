import { createContext, useContext, useMemo, useState } from 'react'
import type { ReactNode } from 'react'

/**
 * Prefill payload so a task can move between the assistant and a visual form
 * without losing entered details.
 */
export interface FormDraft {
  form: 'meeting' | 'statement' | 'loan' | 'vote'
  values: Record<string, string>
}

interface AssistantState {
  open: boolean
  setOpen: (open: boolean) => void
  draft: FormDraft | null
  setDraft: (draft: FormDraft | null) => void
}

const AssistantContext = createContext<AssistantState | null>(null)

export function AssistantProvider({ children }: { children: ReactNode }): React.JSX.Element {
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState<FormDraft | null>(null)
  const value = useMemo(() => ({ open, setOpen, draft, setDraft }), [open, draft])
  return <AssistantContext.Provider value={value}>{children}</AssistantContext.Provider>
}

export function useAssistant(): AssistantState {
  const ctx = useContext(AssistantContext)
  if (!ctx) throw new Error('useAssistant must be used within AssistantProvider')
  return ctx
}

/** One-shot hook for a form screen: consume a pending draft for this form. */
export function useFormDraft(form: FormDraft['form']): Record<string, string> | null {
  const { draft, setDraft } = useAssistant()
  const [consumed] = useState(() => {
    if (draft && draft.form === form) {
      // Defer clearing so the values are applied first.
      queueMicrotask(() => setDraft(null))
      return draft.values
    }
    return null
  })
  return consumed
}
