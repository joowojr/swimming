import {
  IconBrandAws,
  IconBrandDocker,
  IconBrandFigma,
  IconBrandGithub,
  IconBrandGitlab,
  IconBrandGoogle,
  IconBrandLinkedin,
  IconBrandMedium,
  IconBrandNextjs,
  IconBrandNotion,
  IconBrandNpm,
  IconBrandOpenai,
  IconBrandReact,
  IconBrandReddit,
  IconBrandSlack,
  IconBrandStackoverflow,
  IconBrandSupabase,
  IconBrandVercel,
  IconBrandWikipedia,
  IconBrandX,
  IconBrandYoutube,
  IconBrandKakaoTalk,
  IconArticle,
  IconLeaf,
  IconLink,
} from '@tabler/icons-react'
import type { Icon } from '@tabler/icons-react'
import { createElement } from 'react'

/**
 * 아는 출처는 로고로 알아보게 한다. 모르는 곳은 링크 아이콘 그대로다.
 *
 * 별도 아이콘 패키지를 들이지 않고 이미 쓰는 tabler의 브랜드 아이콘만 쓴다. 같은 선 스타일이라
 * 나머지 아이콘과 섞이지 않는다.
 */
const DOMAIN_ICONS: Record<string, Icon> = {
  'github.com': IconBrandGithub,
  'gitlab.com': IconBrandGitlab,
  'stackoverflow.com': IconBrandStackoverflow,
  'medium.com': IconBrandMedium,
  'youtube.com': IconBrandYoutube,
  'youtu.be': IconBrandYoutube,
  'notion.so': IconBrandNotion,
  'notion.site': IconBrandNotion,
  'reddit.com': IconBrandReddit,
  'x.com': IconBrandX,
  'twitter.com': IconBrandX,
  'npmjs.com': IconBrandNpm,
  'openai.com': IconBrandOpenai,
  'vercel.com': IconBrandVercel,
  'nextjs.org': IconBrandNextjs,
  'react.dev': IconBrandReact,
  'wikipedia.org': IconBrandWikipedia,
  'linkedin.com': IconBrandLinkedin,
  'docker.com': IconBrandDocker,
  'figma.com': IconBrandFigma,
  'slack.com': IconBrandSlack,
  'supabase.com': IconBrandSupabase,
  'aws.amazon.com': IconBrandAws,
  'google.com': IconBrandGoogle,
  // tabler에 Spring 로고가 없어 잎사귀로 대신한다. Spring 로고 자체가 잎 모양이다.
  'spring.io': IconLeaf,

  // 한국 서비스는 tabler에 카카오톡 하나뿐이다. 나머지 블로그 계열은 로고 대신 '글'이라는
  // 성격만 구분해 준다. 없는 로고를 비슷한 브랜드 아이콘으로 흉내 내지는 않는다.
  'kakao.com': IconBrandKakaoTalk,
  'velog.io': IconArticle,
  'tistory.com': IconArticle,
  'brunch.co.kr': IconArticle,
  'naver.com': IconArticle,
  'yozm.wishket.com': IconArticle,
  'toss.tech': IconArticle,
  'woowahan.com': IconArticle,
  'daangn.com': IconArticle,
  'oopy.io': IconArticle,
}

/**
 * 서버가 주는 domain은 `docs.spring.io`처럼 서브도메인이 붙은 호스트다. 뒤에서부터 좁혀
 * 맞춰야 `docs.github.com`도 github 규칙에 걸린다.
 */
function iconFor(domain: string | null): Icon {
  if (!domain) return IconLink

  const host = domain.toLowerCase().replace(/^www\./, '')
  const labels = host.split('.')

  for (let index = 0; index < labels.length - 1; index += 1) {
    const candidate = labels.slice(index).join('.')
    const icon = DOMAIN_ICONS[candidate]
    if (icon) return icon
  }

  return IconLink
}

/**
 * 출처 표시 하나를 그린다.
 *
 * 고른 아이콘을 컴포넌트 변수에 담아 JSX로 쓰지 않고 여기서 만들어 돌려준다. 변수에 담으면
 * 렌더마다 컴포넌트를 새로 만드는 것과 구분되지 않는다.
 */
export function sourceMark(domain: string | null, size: number, className?: string) {
  return createElement(iconFor(domain), {
    className,
    size,
    stroke: 1.8,
    'aria-hidden': true,
  })
}
