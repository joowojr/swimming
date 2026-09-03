import styles from './DdayChip.module.css'

/** 역할: 목표일이 가까운 항목에 남은 일수 칩을 붙인다. 폴더·할 일 등 목표일을 가진 화면이 함께 쓴다. */
interface DdayChipProps {
  targetDate: string | null
  /** 이 일수 이하로 남았을 때만 칩을 보여 준다. */
  thresholdDays?: number
}

const MS_PER_DAY = 24 * 60 * 60 * 1000

/** 남은 일수는 오늘 자정 기준으로 센다. 시각 차이가 결과를 흔들지 않게 한다. */
function getDaysUntil(targetDate: string) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return Math.round((new Date(`${targetDate}T00:00:00`).getTime() - today.getTime()) / MS_PER_DAY)
}

function formatDday(daysUntil: number) {
  if (daysUntil === 0) return 'D-DAY'
  return daysUntil > 0 ? `D-${daysUntil}` : `D+${-daysUntil}`
}

export default function DdayChip({ targetDate, thresholdDays = 7 }: DdayChipProps) {
  if (targetDate === null) return null

  const daysUntil = getDaysUntil(targetDate)
  if (daysUntil > thresholdDays) return null

  return (
    <span className={styles.chip}>
      <span className="sr-only">목표일까지 </span>
      {formatDday(daysUntil)}
    </span>
  )
}
