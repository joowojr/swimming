import { useState } from 'react'
import styles from './ArcadeDemo.module.css'

const ARCADE_EMBED_URL = 'https://demo.arcade.software/6hc0sGlWpaQYxIBWfmkF?embed&embed_mobile=tab&embed_desktop=inline&show_copy_link=true'

export default function ArcadeDemo() {
  const [isLoaded, setIsLoaded] = useState(false)

  return (
    <figure className={styles.demo} aria-labelledby="arcade-demo-caption">
      <div className={styles.frame}>
        {!isLoaded && (
          <div className={styles.loading} role="status">
            제품 데모를 불러오는 중…
          </div>
        )}
        <iframe
          className={styles.iframe}
          data-loaded={isLoaded}
          src={ARCADE_EMBED_URL}
          title="swimming-now.kro.kr"
          loading="lazy"
          allowFullScreen
          allow="clipboard-write; autoplay"
          onLoad={() => setIsLoaded(true)}
        />
      </div>
      <figcaption id="arcade-demo-caption" className={styles.caption}>
        <a href={ARCADE_EMBED_URL} target="_blank" rel="noreferrer">새 창에서 데모 보기</a>
      </figcaption>
    </figure>
  )
}
