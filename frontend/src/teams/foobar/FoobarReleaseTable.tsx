import type { Release } from '../../services/api'

export default function FoobarReleaseTable({ releases }: { releases: Release[] }) {
  if (releases.length === 0) {
    return <p style={{ color: '#666' }}>No releases found</p>
  }

  return (
    <table style={{ width: '100%', borderCollapse: 'collapse' }}>
      <thead>
        <tr style={{ borderBottom: '2px solid #eee', textAlign: 'left' }}>
          <th style={{ padding: '0.75rem' }}>Repository</th>
          <th style={{ padding: '0.75rem' }}>Version</th>
          <th style={{ padding: '0.75rem' }}>Environment</th>
        </tr>
      </thead>
      <tbody>
        {releases.map((release) => (
          <tr key={`${release.repository}-${release.pr_number}-${release.environment}`} style={{ borderBottom: '1px solid #eee' }}>
            <td style={{ padding: '0.75rem' }}>{release.repository}</td>
            <td style={{ padding: '0.75rem' }}>{release.release_tag || 'N/A'}</td>
            <td style={{ padding: '0.75rem' }}>{release.environment}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
