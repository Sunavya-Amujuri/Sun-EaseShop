import { useEffect, useRef, useState } from 'react';
import { api } from '../api.js';
import { useAuth } from '../store.jsx';
import { Spinner } from '../ui.jsx';

const OPENERS = [
  'Where is my order?',
  'What is your return policy?',
  'Do you sell yoga mats?',
];

/**
 * Floating customer assistant.
 *
 * Only rendered for signed-in shoppers: the backend scopes retrieval to the caller's own orders
 * and cart, so an anonymous session has nothing to ground answers in.
 */
export default function AiAssistant() {
  const { user } = useAuth();
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState([
    { from: 'bot', text: 'Hi! I can help with your orders, returns, payments and finding products. What do you need?' },
  ]);
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const bodyRef = useRef(null);

  useEffect(() => {
    if (bodyRef.current) bodyRef.current.scrollTop = bodyRef.current.scrollHeight;
  }, [messages, open]);

  if (!user) return null;

  async function send(text) {
    const question = (text ?? draft).trim();
    if (!question || sending) return;
    setDraft('');
    setMessages((m) => [...m, { from: 'user', text: question }]);
    setSending(true);
    try {
      const res = await api.assistant(question);
      setMessages((m) => [...m, { from: 'bot', text: res.answer }]);
    } catch (e) {
      setMessages((m) => [...m, { from: 'bot', text: `Sorry, I could not answer that. (${e.message})` }]);
    } finally {
      setSending(false);
    }
  }

  if (!open) {
    return (
      <button className="ai-fab" onClick={() => setOpen(true)} aria-label="Open shopping assistant" title="Shopping assistant">
        ✨
      </button>
    );
  }

  return (
    <div className="ai-panel">
      <div className="ai-head">
        <div>
          <div className="bold" style={{ fontSize: 14.5 }}>Shopping Assistant</div>
          <div style={{ fontSize: 11.5, opacity: .85 }}>Answers from your orders and our catalog</div>
        </div>
        <button onClick={() => setOpen(false)} style={{ color: '#fff', fontSize: 21, lineHeight: 1 }} aria-label="Close">×</button>
      </div>

      <div className="ai-body" ref={bodyRef}>
        {messages.map((m, i) => (
          <div key={i} className={`ai-msg ${m.from}`}>{m.text}</div>
        ))}

        {messages.length === 1 && (
          <div className="chips" style={{ marginTop: 4 }}>
            {OPENERS.map((q) => (
              <button key={q} className="chip" onClick={() => send(q)}>{q}</button>
            ))}
          </div>
        )}

        {sending && (
          <div className="ai-msg bot row" style={{ gap: 8 }}>
            <Spinner /> <span className="muted">Thinking…</span>
          </div>
        )}
      </div>

      <form className="ai-foot" onSubmit={(e) => { e.preventDefault(); send(); }}>
        <input
          className="input"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="Ask anything…"
          disabled={sending}
        />
        <button className="btn btn-dark btn-sm" disabled={sending || !draft.trim()}>Send</button>
      </form>
    </div>
  );
}
