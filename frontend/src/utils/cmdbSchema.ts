import { CmdbItem } from '../services/api'

/** Bundled snapshot — keep in sync with backend/src/main/resources/cmdb/ci-record.schema.json */
export const CMDB_SCHEMA_SNAPSHOT = {
  $schema: 'http://json-schema.org/draft-07/schema#',
  title: 'MENTAT Configuration Item (snapshot — refresh after CMDB repo move)',
  type: 'object',
  required: ['name', 'category', 'ci_number', 'parent', 'status'],
  properties: {
    name: { type: 'string', minLength: 1 },
    category: { type: 'string', enum: ['SYS', 'ENV', 'REL', 'DOC', 'DMO', 'SSC'] },
    ci_number: {
      type: 'string',
      pattern: '^MENTAT-(SYS|ENV|REL|DOC|DMO|SSC)-[0-9]{2}(\\.[A-Z](\\.[0-9]{2})?)*(-[A-Z]+-Rev[0-9]{3})?$',
    },
    parent: { type: ['string', 'null'] },
    doc_type: {
      type: ['string', 'null'],
      enum: ['PLAN', 'PROCEDURE', 'REPORT', 'MATRIX', null],
    },
    revision: { type: ['integer', 'null'], minimum: 0 },
    status: {
      type: 'string',
      enum: ['active', 'in works', 'retired', 'pre-baseline', 'awaiting approval'],
    },
    notes: { type: 'string' },
  },
  additionalProperties: false,
} as const

export type CmdbJsonSchema = {
  required?: readonly string[]
  properties?: {
    category?: { enum?: readonly (string | null)[] }
    status?: { enum?: readonly (string | null)[] }
    doc_type?: { enum?: readonly (string | null)[] }
    ci_number?: { pattern?: string }
  }
}

const CI_NUMBER_RE =
  /^MENTAT-(SYS|ENV|REL|DOC|DMO|SSC)-([0-9]{2})(?:\.[A-Z](?:\.[0-9]{2})?)*(?:-[A-Z]+-Rev[0-9]{3})?$/
const PARENT_ID_RE =
  /^MENTAT-(SYS|ENV|REL|DOC|DMO|SSC)-[0-9]{2}(?:\.[A-Z](?:\.[0-9]{2})?)*$/

/** Full names for category codes. The code remains the stored value / CI prefix. */
export const CATEGORY_DISPLAY_NAMES: Record<string, string> = {
  SYS: 'System',
  ENV: 'Environment',
  REL: 'Release',
  DOC: 'Document',
  DMO: 'Demo',
  SSC: 'Software and source code',
}

export function categoryDisplayName(code: string): string {
  const key = code.trim().toUpperCase()
  const name = CATEGORY_DISPLAY_NAMES[key]
  return name ? `${name} (${key})` : code
}

export function categorySelectOptions(codes: readonly string[]) {
  return codes.map((value) => ({
    value,
    label: categoryDisplayName(value),
  }))
}

export function schemaEnums(schema: CmdbJsonSchema = CMDB_SCHEMA_SNAPSHOT) {
  const strings = (values: readonly (string | null)[] | undefined) =>
    (values ?? []).filter((v): v is string => typeof v === 'string' && v.length > 0)

  return {
    categories: strings(schema.properties?.category?.enum).length
      ? strings(schema.properties?.category?.enum)
      : [...CMDB_SCHEMA_SNAPSHOT.properties.category.enum],
    statuses: strings(schema.properties?.status?.enum).length
      ? strings(schema.properties?.status?.enum)
      : [...CMDB_SCHEMA_SNAPSHOT.properties.status.enum],
    docTypes: strings(schema.properties?.doc_type?.enum).length
      ? strings(schema.properties?.doc_type?.enum)
      : ['PLAN', 'PROCEDURE', 'REPORT', 'MATRIX'],
    ciNumberPattern:
      schema.properties?.ci_number?.pattern ??
      CMDB_SCHEMA_SNAPSHOT.properties.ci_number.pattern,
  }
}

export function emptyCmdbItem(): CmdbItem {
  return {
    name: '',
    category: 'SYS',
    ci_number: 'MENTAT-SYS-01',
    parent: null,
    status: 'awaiting approval',
    notes: '',
    doc_type: null,
    revision: null,
  }
}

export function nextCiNumber(category: string, existing: string[]): string {
  const cat = category.trim().toUpperCase()
  let max = 0
  for (const id of existing) {
    const match = CI_NUMBER_RE.exec(id.trim())
    if (match && match[1] === cat) {
      max = Math.max(max, Number(match[2]))
    }
  }
  return `MENTAT-${cat}-${String(max + 1).padStart(2, '0')}`
}

function escapeRe(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/** Next child under parent: NN → NN.A, NN.A → NN.A.01. Never returns the parent id itself. */
export function nextChildCiNumber(parentId: string, existing: string[]): string {
  const parent = parentId.trim()
  if (!PARENT_ID_RE.test(parent)) {
    return parent
  }
  const escaped = escapeRe(parent)
  if (/\.[A-Z]$/.test(parent)) {
    const childRe = new RegExp(`^${escaped}\\.([0-9]{2})$`)
    let max = 0
    for (const id of existing) {
      const match = childRe.exec(id.trim())
      if (match) {
        max = Math.max(max, Number(match[1]))
      }
    }
    if (max >= 99) {
      return `${parent}.01`
    }
    return `${parent}.${String(max + 1).padStart(2, '0')}`
  }
  const childRe = new RegExp(`^${escaped}\\.([A-Z])$`)
  let maxCode = 64
  for (const id of existing) {
    const match = childRe.exec(id.trim())
    if (match) {
      maxCode = Math.max(maxCode, match[1].charCodeAt(0))
    }
  }
  if (maxCode >= 90) {
    return `${parent}.A`
  }
  return `${parent}.${String.fromCharCode(maxCode + 1)}`
}

export function suggestedCiNumber(
  category: string,
  parent: string | null | undefined,
  existing: string[]
): string {
  if (parent?.trim()) {
    return nextChildCiNumber(parent.trim(), existing)
  }
  return nextCiNumber(category, existing)
}

export function validateCmdbItem(item: CmdbItem, schema: CmdbJsonSchema = CMDB_SCHEMA_SNAPSHOT): string[] {
  const { categories, statuses, docTypes, ciNumberPattern } = schemaEnums(schema)
  const errors: string[] = []
  const name = item.name?.trim() ?? ''
  if (!name) errors.push('Name is required')

  if (!categories.includes(item.category)) {
    errors.push('Category is required')
  }

  const pattern = new RegExp(ciNumberPattern)
  if (!item.ci_number || !pattern.test(item.ci_number)) {
    errors.push('CI number must match MENTAT-{CAT}-NN (optional .A, .A.01, or DOC revision suffix)')
  } else if (item.category && !item.ci_number.startsWith(`MENTAT-${item.category}-`)) {
    errors.push('CI number prefix must match the selected category')
  }

  if (!statuses.includes(item.status)) {
    errors.push('Status is required')
  }

  if (item.category === 'DOC') {
    if (!item.doc_type || !docTypes.includes(item.doc_type)) {
      errors.push('Document type is required for DOC items')
    }
    if (item.revision == null || item.revision < 0 || !Number.isInteger(item.revision)) {
      errors.push('Revision must be an integer ≥ 0 for DOC items')
    }
  }

  return errors
}

export function payloadFromForm(item: CmdbItem): CmdbItem {
  const isDoc = item.category === 'DOC'
  return {
    name: item.name.trim(),
    category: item.category,
    ci_number: item.ci_number.trim(),
    parent: item.parent?.trim() ? item.parent.trim() : null,
    status: item.status,
    notes: item.notes?.trim() ? item.notes.trim() : undefined,
    doc_type: isDoc ? item.doc_type ?? null : null,
    revision: isDoc ? item.revision ?? 0 : null,
  }
}
