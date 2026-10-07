import AgentIcon from './AgentIcon'
import { AGENT_NAMES } from './agentWorkLabels'
import type { AgentType } from './agentWorkTypes'
import styles from './AgentLabel.module.css'

interface AgentLabelProps {
  agentType: AgentType
  size?: number
  className?: string
}

/** 로고와 이름을 함께 보여준다. 이름이 글로 보이므로 로고는 장식으로 둔다. */
export default function AgentLabel({ agentType, size = 16, className }: AgentLabelProps) {
  return (
    <span className={`${styles.label} ${className ?? ''}`}>
      <AgentIcon agentType={agentType} size={size} hideLabel />
      <span>{AGENT_NAMES[agentType]}</span>
    </span>
  )
}
