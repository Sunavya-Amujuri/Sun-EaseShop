import { useEffect, useRef } from 'react';

/**
 * Six separate boxes for a one-time code.
 *
 * Handles the things people actually do with these: pasting the whole code, typing straight
 * through without tabbing, and backspacing out of an empty box to fix the previous digit.
 */
export default function OtpInput({ value, onChange, disabled, autoFocus = true, length = 6 }) {
  const refs = useRef([]);

  useEffect(() => {
    if (autoFocus) refs.current[0]?.focus();
  }, [autoFocus]);

  const digits = value.padEnd(length, ' ').slice(0, length).split('');

  function setDigit(index, digit) {
    const next = digits.map((d, i) => (i === index ? digit : d)).join('').trimEnd();
    onChange(next.replace(/\s/g, ''));
  }

  function handleChange(index, raw) {
    const only = raw.replace(/\D/g, '');
    if (!only) { setDigit(index, ' '); return; }

    if (only.length > 1) {
      // Someone typed or pasted several digits at once - spread them across the boxes.
      const chars = only.slice(0, length - index).split('');
      const next = [...digits];
      chars.forEach((c, k) => { next[index + k] = c; });
      onChange(next.join('').replace(/\s/g, ''));
      refs.current[Math.min(index + chars.length, length - 1)]?.focus();
      return;
    }

    setDigit(index, only);
    if (index < length - 1) refs.current[index + 1]?.focus();
  }

  function handleKeyDown(index, e) {
    if (e.key === 'Backspace' && !digits[index].trim() && index > 0) {
      e.preventDefault();
      setDigit(index - 1, ' ');
      refs.current[index - 1]?.focus();
    }
    if (e.key === 'ArrowLeft' && index > 0) refs.current[index - 1]?.focus();
    if (e.key === 'ArrowRight' && index < length - 1) refs.current[index + 1]?.focus();
  }

  function handlePaste(e) {
    const pasted = (e.clipboardData.getData('text') || '').replace(/\D/g, '').slice(0, length);
    if (!pasted) return;
    e.preventDefault();
    onChange(pasted);
    refs.current[Math.min(pasted.length, length - 1)]?.focus();
  }

  return (
    <div className="otp-row" onPaste={handlePaste}>
      {Array.from({ length }).map((_, i) => (
        <input
          key={i}
          ref={(el) => { refs.current[i] = el; }}
          className="otp-box"
          type="text"
          inputMode="numeric"
          autoComplete={i === 0 ? 'one-time-code' : 'off'}
          maxLength={length}
          value={digits[i].trim()}
          onChange={(e) => handleChange(i, e.target.value)}
          onKeyDown={(e) => handleKeyDown(i, e)}
          onFocus={(e) => e.target.select()}
          disabled={disabled}
          aria-label={`Digit ${i + 1}`}
        />
      ))}
    </div>
  );
}
