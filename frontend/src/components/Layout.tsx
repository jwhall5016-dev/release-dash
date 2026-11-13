import TeamLayout from './TeamLayout'

const links = [
  { to: '/mentat/releases', label: 'All Releases' },
  { to: '/mentat/live', label: 'Live Deployments' },
  { to: '/mentat/cmdb', label: 'CMDB' },
  { to: '/mentat/memory', label: 'Memory' },
]

export default function Layout() {
  return (
    <TeamLayout
      title="MENTAT Release Dashboard"
      homeTo="/mentat"
      links={links}
    />
  )
}
