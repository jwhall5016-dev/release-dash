import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import CmdbForm from './CmdbForm'
import {
  CmdbItem,
  CmdbWriteResult,
  createCmdbItem,
  getCmdbItems,
  getCmdbSchema,
} from '../services/api'
import { CMDB_SCHEMA_SNAPSHOT, CmdbJsonSchema } from '../utils/cmdbSchema'

export default function CmdbNew() {
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [result, setResult] = useState<CmdbWriteResult | null>(null)

  const schemaQuery = useQuery({
    queryKey: ['cmdb-schema'],
    queryFn: getCmdbSchema,
    retry: false,
  })

  const itemsQuery = useQuery({
    queryKey: ['cmdb-items'],
    queryFn: getCmdbItems,
    retry: false,
  })

  const schema = (schemaQuery.data as CmdbJsonSchema | undefined) ?? CMDB_SCHEMA_SNAPSHOT
  const existingItems = itemsQuery.data ?? []

  const onSubmit = async (item: CmdbItem) => {
    setSubmitting(true)
    setSubmitError(null)
    try {
      setResult(await createCmdbItem(item))
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : String(error))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div>
      <p style={{ marginBottom: '1rem' }}>
        <Link to="/mentat/cmdb" style={{ color: '#1d4ed8' }}>← CMDB</Link>
      </p>
      <h1 style={{ marginBottom: '0.5rem' }}>New configuration item</h1>
      {result ? (
        <div style={{
          backgroundColor: '#ecfdf5',
          border: '1px solid #6ee7b7',
          borderRadius: '8px',
          padding: '1.25rem',
          maxWidth: '36rem',
        }}>
          <strong style={{ display: 'block', marginBottom: '0.5rem' }}>Pull request opened</strong>
          <p style={{ margin: '0 0 0.75rem', lineHeight: 1.5 }}>
            Wrote <code>{result.path}</code>. Merge the PR in GitHub; the catalog updates after
            the CMDB sync Action rebuilds <code>cmdb/cis.yaml</code>.
          </p>
          {result.pr_url ? (
            <p style={{ margin: 0 }}>
              <a href={result.pr_url} target="_blank" rel="noreferrer" style={{ color: '#1d4ed8' }}>
                Open PR{result.pr_number != null ? ` #${result.pr_number}` : ''} on GitHub
              </a>
            </p>
          ) : (
            <p style={{ margin: 0 }}>Branch <code>{result.branch}</code> was created.</p>
          )}
        </div>
      ) : (
        <>
          <p style={{ color: '#555', marginBottom: '1.5rem', maxWidth: '36rem' }}>
            Submitting opens a pull request on <code>mentatcmdb</code> with one per-CI YAML file.
            It does not edit the generated index. A reviewer merges the PR in GitHub.
          </p>
          <CmdbForm
            mode="create"
            schema={schema}
            existingItems={existingItems}
            submitting={submitting}
            submitError={submitError}
            onSubmit={onSubmit}
          />
        </>
      )}
    </div>
  )
}
