import { useQuery } from '@tanstack/react-query'
import { getFoobarLiveServicesCatalog, getFoobarReleases } from '../../services/api'
import FoobarReleaseTable from './FoobarReleaseTable'

const panelStyle = {
  backgroundColor: 'white',
  borderRadius: '8px',
  boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
  padding: '1.5rem',
}

export function FoobarReleases() {
  const { data: releases, isLoading, error } = useQuery({
    queryKey: ['foobar', 'releases'],
    queryFn: () => getFoobarReleases(undefined, undefined, 100000),
  })

  return (
    <div>
      <h1 style={{ marginBottom: '2rem' }}>All Releases</h1>
      <div style={panelStyle}>
        {isLoading && <p style={{ color: '#666' }}>Loading...</p>}
        {error && <p style={{ color: '#991b1b' }}>{error instanceof Error ? error.message : String(error)}</p>}
        {!isLoading && !error && <FoobarReleaseTable releases={releases ?? []} />}
      </div>
    </div>
  )
}

export function FoobarLive() {
  const { data: catalog, isLoading, error } = useQuery({
    queryKey: ['foobar', 'live-services'],
    queryFn: getFoobarLiveServicesCatalog,
  })

  const services = catalog?.services ?? []

  return (
    <div>
      <h1 style={{ marginBottom: '2rem' }}>Live Deployments</h1>
      <div style={panelStyle}>
        {isLoading && <p style={{ color: '#666' }}>Loading...</p>}
        {error && <p style={{ color: '#991b1b' }}>{error instanceof Error ? error.message : String(error)}</p>}
        {!isLoading && !error && services.length === 0 && (
          <p style={{ color: '#666' }}>No live deployments found</p>
        )}
        {!isLoading && !error && services.length > 0 && (
          <ul>
            {services.map((service) => (
              <li key={service.id}>{service.label}</li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}

export function FoobarMemory() {
  return (
    <div>
      <h1 style={{ marginBottom: '2rem' }}>Memory</h1>
      <div style={panelStyle}>
        <p style={{ color: '#666' }}>No memory data found</p>
      </div>
    </div>
  )
}
