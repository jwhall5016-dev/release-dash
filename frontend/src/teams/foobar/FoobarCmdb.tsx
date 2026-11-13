import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { getFoobarCmdbItems } from '../../services/api'

export default function FoobarCmdb() {
  const { data: items, isLoading, error } = useQuery({
    queryKey: ['foobar', 'cmdb', 'items'],
    queryFn: getFoobarCmdbItems,
  })

  return (
    <div>
      <p style={{ marginTop: 0 }}>
        <Link to="/foobar" style={{ color: '#1d4ed8' }}>← Foobar</Link>
      </p>
      <h1 style={{ marginTop: 0 }}>CMDB</h1>
      <p style={{ color: '#444', maxWidth: '40rem' }}>
        Configuration items from the Foobar API at /foobar/api/cmdb/items.
      </p>
      {isLoading && <p style={{ color: '#666' }}>Loading...</p>}
      {error && <p style={{ color: '#991b1b' }}>{error instanceof Error ? error.message : String(error)}</p>}
      {!isLoading && !error && (
        <table style={{ width: '100%', borderCollapse: 'collapse', backgroundColor: 'white', marginTop: '1rem' }}>
          <thead>
            <tr style={{ borderBottom: '2px solid #eee', textAlign: 'left' }}>
              <th style={{ padding: '0.75rem' }}>CI</th>
              <th style={{ padding: '0.75rem' }}>Name</th>
              <th style={{ padding: '0.75rem' }}>Status</th>
            </tr>
          </thead>
          <tbody>
            {(items ?? []).length === 0 ? (
              <tr>
                <td colSpan={3} style={{ padding: '0.75rem', color: '#666' }}>No configuration items found</td>
              </tr>
            ) : (
              items!.map((item) => (
                <tr key={item.ci_number} style={{ borderBottom: '1px solid #eee' }}>
                  <td style={{ padding: '0.75rem' }}>{item.ci_number}</td>
                  <td style={{ padding: '0.75rem' }}>{item.name}</td>
                  <td style={{ padding: '0.75rem' }}>{item.status}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      )}
    </div>
  )
}
