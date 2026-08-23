import { useState } from 'react';
import { IconSparkles } from '@tabler/icons-react';
import styles from './MemoCard.module.css'


interface MemoCardProps {
  onOrganize: (text: string) => Promise<void> | void;
}

export default function MemoCard({ onOrganize }: MemoCardProps) {
  const [memo, setMemo] = useState<string>('');
  const [isOrganizing, setIsOrganizing] = useState<boolean>(false);

  const handleOrganize = async (): Promise<void> => {
    if (!memo.trim() || isOrganizing) return;
    setIsOrganizing(true);
    try {
      await onOrganize(memo);
      setMemo('');                       // 정리 후 비움
    } finally {
      setIsOrganizing(false);
    }
  };

  return (
    <section className={styles['memo-card']} aria-labelledby="memo-title">
      <div className={styles['memo-head']}>
        <h3 id="memo-title" className={styles['memo-title']}>메모</h3>
      </div>

      <textarea
        className={styles['memo-paper']}
        placeholder="떠오르는 일을 편하게 적어두세요."
        value={memo}
        onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setMemo(e.target.value)}
        disabled={isOrganizing}
      />

      <div className={styles['memo-foot']}>
        <span className={styles['memo-hint']}>정리하면 비워집니다</span>
        <button
          type="button"
          className={styles['organize-action']}
          onClick={handleOrganize}
          disabled={!memo.trim() || isOrganizing}
        >
          <IconSparkles size={16} aria-hidden="true" />
          {isOrganizing ? '정리하는 중' : '할 일로 정리'}
        </button>
      </div>
    </section>
  );
}