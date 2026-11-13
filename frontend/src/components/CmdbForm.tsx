import { FormEvent, useEffect, useMemo, useState, type CSSProperties } from 'react'
import { CmdbItem } from '../services/api'
import {
  CmdbJsonSchema,
  emptyCmdbItem,
  suggestedCiNumber,
  payloadFromForm,
  schemaEnums,
  categorySelectOptions,
  validateCmdbItem,
} from '../utils/cmdbSchema'
import SearchableSelect from './SearchableSelect'

const labelStyle: CSSProperties = {
  display: 'block',
  fontWeight: 600,
  fontSize: '0.875rem',
  marginBottom: '0.35rem',
}

const inputStyle: CSSProperties = {
  width: '100%',
  padding: '0.5rem 0.65rem',
  border: '1px solid #d1d5db',
  borderRadius: '6px',
  fontSize: '1rem',
  backgroundColor: 'white',
}

const fieldStyle: CSSProperties = { marginBottom: '1rem' }

interface CmdbFormProps {
  mode: 'create' | 'edit'
  schema: CmdbJsonSchema
  initial?: Partial<CmdbItem>
  existingItems?: CmdbItem[]
  submitting?: boolean
  submitError?: string | null
  readOnly?: boolean
  onSubmit: (item: CmdbItem) => Promise<void> | void
}

export default function CmdbForm({
  mode,
  schema,
  initial,
  existingItems = [],
  submitting = false,
  submitError,
  readOnly = false,
  onSubmit,
}: CmdbFormProps) {
  const enums = useMemo(() => schemaEnums(schema), [schema])
  const [item, setItem] = useState<CmdbItem>(() => ({
    ...emptyCmdbItem(),
    ...initial,
  }))
  const [clientErrors, setClientErrors] = useState<string[]>([])

  useEffect(() => {
    if (initial) {
      setItem((prev) => ({ ...emptyCmdbItem(), ...prev, ...initial }))
    }
  }, [initial])

  useEffect(() => {
    if (mode !== 'create') return
    const existingIds = existingItems.map((row) => row.ci_number)
    setItem((prev) => {
      if (!existingIds.includes(prev.ci_number)) return prev
      return {
        ...prev,
        ci_number: suggestedCiNumber(prev.category, prev.parent, existingIds),
      }
    })
  }, [existingItems, mode])

  const parentOptions = useMemo(() => {
    const selfId = mode === 'edit' ? (initial?.ci_number ?? item.ci_number) : null
    const numbers = existingItems
      .filter((row) => row.category === item.category)
      .map((row) => row.ci_number)
      .filter((id) => id && id !== selfId)
    return [...new Set(numbers)].sort()
  }, [existingItems, initial?.ci_number, item.category, item.ci_number, mode])

  const onParentChange = (parent: string | null) => {
    setItem((prev) => {
      if (mode !== 'create') {
        return { ...prev, parent }
      }
      const existingIds = existingItems.map((row) => row.ci_number)
      return {
        ...prev,
        parent,
        ci_number: suggestedCiNumber(prev.category, parent, existingIds),
      }
    })
  }

  const setField = <K extends keyof CmdbItem>(key: K, value: CmdbItem[K]) => {
    setItem((prev) => ({ ...prev, [key]: value }))
  }

  const onCategoryChange = (category: string) => {
    setItem((prev) => {
      const existingIds = existingItems.map((row) => row.ci_number)
      const parentStillValid = existingItems.some(
        (row) => row.category === category && row.ci_number === prev.parent
      )
      const parent = parentStillValid ? prev.parent : null
      const shouldPrefill =
        mode === 'create' &&
        (!prev.ci_number || prev.ci_number.startsWith(`MENTAT-${prev.category}-`))
      return {
        ...prev,
        category,
        parent,
        ci_number: shouldPrefill
          ? suggestedCiNumber(category, parent, existingIds)
          : prev.ci_number,
        doc_type: category === 'DOC' ? prev.doc_type ?? 'PLAN' : null,
        revision: category === 'DOC' ? prev.revision ?? 0 : null,
      }
    })
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (readOnly) return
    const payload = payloadFromForm(item)
    if (mode === 'create') {
      payload.status = 'awaiting approval'
    }
    const errors = validateCmdbItem(payload, schema)
    if (mode === 'create' && existingItems.some((row) => row.ci_number === payload.ci_number)) {
      errors.push(`CI number ${payload.ci_number} already exists in the catalog`)
    }
    setClientErrors(errors)
    if (errors.length > 0) return
    await onSubmit(payload)
  }

  const isDoc = item.category === 'DOC'
  const fieldsDisabled = readOnly || submitting
  const controlStyle: CSSProperties = {
    ...inputStyle,
    backgroundColor: fieldsDisabled ? '#f3f4f6' : 'white',
    color: fieldsDisabled ? '#4b5563' : 'inherit',
    cursor: fieldsDisabled ? 'not-allowed' : undefined,
  }
  const selectStyle: CSSProperties = {
    ...controlStyle,
    cursor: fieldsDisabled ? 'not-allowed' : 'pointer',
  }
  const statusLocked = fieldsDisabled || mode === 'create'
  const statusStyle: CSSProperties = {
    ...selectStyle,
    backgroundColor: statusLocked ? '#f3f4f6' : 'white',
    color: statusLocked ? '#4b5563' : 'inherit',
    cursor: statusLocked ? 'not-allowed' : 'pointer',
  }

  return (
    <form onSubmit={handleSubmit} style={{ maxWidth: '36rem' }}>
      <div style={fieldStyle}>
        <label style={labelStyle} htmlFor="cmdb-category">Category</label>
        <SearchableSelect
          id="cmdb-category"
          style={selectStyle}
          value={item.category}
          options={categorySelectOptions(enums.categories)}
          disabled={fieldsDisabled}
          placeholder="Search category"
          onChange={onCategoryChange}
        />
      </div>

      <div style={fieldStyle}>
        <label style={labelStyle} htmlFor="cmdb-parent">Parent</label>
        {parentOptions.length > 0 ? (
          <SearchableSelect
            id="cmdb-parent"
            style={selectStyle}
            value={item.parent ?? ''}
            options={parentOptions}
            disabled={fieldsDisabled}
            placeholder="Search parent"
            emptyLabel="None"
            onChange={(value) => onParentChange(value || null)}
          />
        ) : (
          <input
            id="cmdb-parent"
            style={controlStyle}
            placeholder="MENTAT-SYS-01 (optional)"
            value={item.parent ?? ''}
            disabled={fieldsDisabled}
            onChange={(e) => onParentChange(e.target.value || null)}
          />
        )}
      </div>

      <div style={fieldStyle}>
        <label style={labelStyle} htmlFor="cmdb-ci-number">CI number</label>
        <input
          id="cmdb-ci-number"
          style={controlStyle}
          value={item.ci_number}
          readOnly={mode === 'edit' || readOnly}
          disabled={fieldsDisabled}
          onChange={(e) => setField('ci_number', e.target.value)}
        />
        <p style={{ color: '#6b7280', fontSize: '0.8rem', margin: '0.35rem 0 0' }}>
          Pattern: MENTAT-{'{'}CAT{'}'}-NN, optional .A / .A.01, or DOC -TYPE-Rev000
        </p>
      </div>

      <div style={fieldStyle}>
        <label style={labelStyle} htmlFor="cmdb-name">Name</label>
        <input
          id="cmdb-name"
          style={controlStyle}
          value={item.name}
          disabled={fieldsDisabled}
          onChange={(e) => setField('name', e.target.value)}
        />
      </div>

      <div style={fieldStyle}>
        <label style={labelStyle} htmlFor="cmdb-status">Status</label>
        <select
          id="cmdb-status"
          style={statusStyle}
          value={mode === 'create' ? 'awaiting approval' : item.status}
          disabled={statusLocked}
          onChange={(e) => setField('status', e.target.value)}
        >
          {enums.statuses.map((value) => (
            <option key={value} value={value}>{value}</option>
          ))}
        </select>
        {mode === 'create' && (
          <p style={{ color: '#6b7280', fontSize: '0.8rem', margin: '0.35rem 0 0' }}>
            New CIs are proposed as awaiting approval. A reviewer can change status after merge.
          </p>
        )}
      </div>

      {isDoc && (
        <>
          <div style={fieldStyle}>
            <label style={labelStyle} htmlFor="cmdb-doc-type">Document type</label>
            <select
              id="cmdb-doc-type"
              style={selectStyle}
              value={item.doc_type ?? ''}
              disabled={fieldsDisabled}
              onChange={(e) => setField('doc_type', e.target.value || null)}
            >
              {enums.docTypes.map((value) => (
                <option key={value} value={value}>{value}</option>
              ))}
            </select>
          </div>
          <div style={fieldStyle}>
            <label style={labelStyle} htmlFor="cmdb-revision">Revision</label>
            <input
              id="cmdb-revision"
              type="number"
              min={0}
              step={1}
              style={controlStyle}
              value={item.revision ?? 0}
              disabled={fieldsDisabled}
              onChange={(e) => setField('revision', Number(e.target.value))}
            />
          </div>
        </>
      )}

      <div style={fieldStyle}>
        <label style={labelStyle} htmlFor="cmdb-notes">Notes</label>
        <textarea
          id="cmdb-notes"
          rows={4}
          style={{ ...controlStyle, resize: 'vertical' }}
          value={item.notes ?? ''}
          disabled={fieldsDisabled}
          onChange={(e) => setField('notes', e.target.value)}
        />
      </div>

      {clientErrors.length > 0 && (
        <ul style={{ color: '#991b1b', marginBottom: '1rem', paddingLeft: '1.25rem' }}>
          {clientErrors.map((err) => (
            <li key={err}>{err}</li>
          ))}
        </ul>
      )}

      {submitError && (
        <div style={{
          backgroundColor: '#eff6ff',
          border: '1px solid #bfdbfe',
          borderRadius: '8px',
          padding: '0.85rem 1rem',
          color: '#1e3a5f',
          marginBottom: '1rem',
        }}>
          {submitError}
        </div>
      )}

      <button
        type="submit"
        disabled={fieldsDisabled}
        style={{
          backgroundColor: fieldsDisabled ? '#d1d5db' : '#1a1a1a',
          color: fieldsDisabled ? '#6b7280' : 'white',
          border: 'none',
          borderRadius: '6px',
          padding: '0.6rem 1.1rem',
          cursor: fieldsDisabled ? 'not-allowed' : 'pointer',
          fontSize: '1rem',
        }}
      >
        {submitting ? 'Opening PR…' : mode === 'create' ? 'Open pull request' : 'Save CI'}
      </button>
    </form>
  )
}
