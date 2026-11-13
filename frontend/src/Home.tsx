import { Link } from 'react-router-dom'

// Not mounted while `/` redirects to `/mentat`. Restore the route in App.tsx to show this list.

const teams = [
  {
    name: 'Mentat',
    path: '/mentat',
    description: 'Release dashboard, live deployments, and CMDB',
  },
  {
    name: 'Foobar',
    path: '/foobar',
    description: 'Foobar team site',
  },
]

export default function Home() {
  return (
    <main style={{ padding: '3rem 2rem', maxWidth: '720px', margin: '0 auto' }}>
      <h1 style={{ marginTop: 0 }}>Teams</h1>
      <p style={{ color: '#444' }}>Each team has its own site on this host.</p>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginTop: '1.5rem' }}>
        {teams.map((team) => (
          <Link
            key={team.path}
            to={team.path}
            style={{
              display: 'block',
              backgroundColor: 'white',
              borderRadius: '8px',
              boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
              padding: '1.25rem 1.5rem',
              textDecoration: 'none',
              color: 'inherit',
            }}
          >
            <strong>{team.name}</strong>
            <p style={{ margin: '0.35rem 0 0', color: '#666' }}>{team.description}</p>
            <p style={{ margin: '0.35rem 0 0', color: '#1d4ed8' }}>{team.path}</p>
          </Link>
        ))}
      </div>
    </main>
  )
}
