import { useMemo } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import CmdbForm from './CmdbForm'
import { getCmdbItem, getCmdbItems, getCmdbSchema } from '../services/api'
import { CMDB_SCHEMA_SNAPSHOT, CmdbJsonSchema, emptyCmdbItem } from '../utils/cmdbSchema'

export default function CmdbDetail() {
  const { ciNumber = '' } = useParams()
  const decoded = decodeURIComponent(ciNumber)

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

  const itemQuery = useQuery({
    queryKey: ['cmdb-item', decoded],
    queryFn: () => getCmdbItem(decoded),
    retry: false,
    enabled: !!decoded,
  })

  const schema = (schemaQuery.data as CmdbJsonSchema | undefined) ?? CMDB_SCHEMA_SNAPSHOT
  const existingItems = itemsQuery.data ?? []

  const initial = useMemo(() => {
    if (itemQuery.data) return itemQuery.data
    return { ...emptyCmdbItem(), ci_number: decoded }
  }, [itemQuery.data, decoded])

  return (
    <div>
      <p style={{ marginBottom: '1rem' }}>
        <Link to="/mentat/cmdb" style={{ color: '#1d4ed8' }}>← CMDB</Link>
      </p>
      <h1 style={{ marginBottom: '0.5rem' }}>{itemQuery.data?.name || decoded || 'Configuration item'}</h1>
      <p style={{ color: '#555', marginBottom: '1.5rem', maxWidth: '36rem' }}>
        Read-only view of this CI. Saving is disabled until Git writes are enabled.
        {itemQuery.data?.html_url ? (
          <>
            {' '}
            <a href={itemQuery.data.html_url} target="_blank" rel="noreferrer" style={{ color: '#1d4ed8' }}>
              View YAML on GitHub
            </a>
          </>
        ) : null}
      </p>
      {itemQuery.isLoading && <p>Loading CI…</p>}
      {itemQuery.error && (
        <p style={{ color: '#991b1b', marginBottom: '1rem' }}>
          {itemQuery.error instanceof Error ? itemQuery.error.message : String(itemQuery.error)}
        </p>
      )}
      <CmdbForm
        mode="edit"
        schema={schema}
        initial={initial}
        existingItems={existingItems}
        readOnly
        onSubmit={() => undefined}
      />
    </div>
  )
}
