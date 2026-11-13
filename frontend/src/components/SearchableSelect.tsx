import { useEffect, useMemo, useRef, useState, type CSSProperties, type KeyboardEvent } from 'react'

export type SearchableSelectOption = string | { value: string; label: string }

interface SearchableSelectProps {
  id: string
  value: string
  options: SearchableSelectOption[]
  onChange: (value: string) => void
  disabled?: boolean
  placeholder?: string
  emptyLabel?: string
  style?: CSSProperties
}

function normalizeOption(option: SearchableSelectOption): { value: string; label: string } {
  return typeof option === 'string' ? { value: option, label: option } : option
}

export default function SearchableSelect({
  id,
  value,
  options,
  onChange,
  disabled = false,
  placeholder = 'Select…',
  emptyLabel,
  style,
}: SearchableSelectProps) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [highlight, setHighlight] = useState(0)
  const rootRef = useRef<HTMLDivElement>(null)

  const allOptions = useMemo(() => {
    const rows = options.map(normalizeOption)
    if (emptyLabel != null) {
      return [{ value: '', label: emptyLabel }, ...rows]
    }
    return rows
  }, [options, emptyLabel])

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!open || !q) return allOptions
    return allOptions.filter(
      (row) => row.label.toLowerCase().includes(q) || row.value.toLowerCase().includes(q)
    )
  }, [allOptions, open, query])

  const closedLabel = allOptions.find((row) => row.value === value)?.label ?? value

  useEffect(() => {
    if (!open) return
    const onPointerDown = (event: MouseEvent) => {
      if (rootRef.current?.contains(event.target as Node)) return
      setOpen(false)
      setQuery('')
    }
    document.addEventListener('mousedown', onPointerDown)
    return () => document.removeEventListener('mousedown', onPointerDown)
  }, [open])

  useEffect(() => {
    setHighlight(0)
  }, [query, open])

  const pick = (next: string) => {
    onChange(next)
    setOpen(false)
    setQuery('')
  }

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (disabled) return
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setOpen(true)
      setHighlight((current) => Math.min(current + 1, Math.max(filtered.length - 1, 0)))
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setHighlight((current) => Math.max(current - 1, 0))
    } else if (event.key === 'Enter') {
      event.preventDefault()
      if (open && filtered[highlight]) {
        pick(filtered[highlight].value)
      } else {
        setOpen(true)
      }
    } else if (event.key === 'Escape') {
      event.preventDefault()
      setOpen(false)
      setQuery('')
    }
  }

  return (
    <div ref={rootRef} style={{ position: 'relative' }}>
      <input
        id={id}
        role="combobox"
        aria-expanded={open}
        aria-controls={`${id}-list`}
        aria-autocomplete="list"
        autoComplete="off"
        disabled={disabled}
        style={style}
        placeholder={placeholder}
        value={open ? query : closedLabel}
        onChange={(event) => {
          const next = event.target.value
          setOpen(true)
          if (!open && closedLabel && next.startsWith(closedLabel)) {
            setQuery(next.slice(closedLabel.length))
            return
          }
          setQuery(next)
        }}
        onFocus={() => {
          if (disabled) return
          setQuery('')
          setOpen(true)
        }}
        onKeyDown={onKeyDown}
      />
      {open && !disabled && (
        <ul
          id={`${id}-list`}
          role="listbox"
          style={{
            position: 'absolute',
            zIndex: 20,
            left: 0,
            right: 0,
            margin: '0.2rem 0 0',
            padding: '0.25rem 0',
            listStyle: 'none',
            background: 'white',
            border: '1px solid #d1d5db',
            borderRadius: '6px',
            maxHeight: '16rem',
            overflowY: 'auto',
            boxShadow: '0 8px 16px rgba(0,0,0,0.08)',
          }}
        >
          {filtered.length === 0 ? (
            <li style={{ padding: '0.5rem 0.65rem', color: '#6b7280' }}>No matches</li>
          ) : (
            filtered.map((row, index) => (
              <li
                key={row.value || '__empty__'}
                role="option"
                aria-selected={row.value === value}
                onMouseDown={(event) => {
                  event.preventDefault()
                  pick(row.value)
                }}
                onMouseEnter={() => setHighlight(index)}
                style={{
                  padding: '0.45rem 0.65rem',
                  cursor: 'pointer',
                  backgroundColor:
                    index === highlight ? '#eff6ff' : row.value === value ? '#f3f4f6' : 'white',
                }}
              >
                {row.label}
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  )
}
