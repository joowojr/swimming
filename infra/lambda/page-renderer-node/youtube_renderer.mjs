import { fetchTranscript, listLanguages, toPlainText } from 'youtube-transcript-plus';

const DEFAULT_LANGUAGE = 'ko';
const USER_AGENT = 'Mozilla/5.0 (X11; Linux x86_64)';

export function isYouTubeVideoUrl(url) {
  try {
    const parsed = new URL(url);
    const host = parsed.hostname.toLowerCase();
    if (host === 'youtu.be') return parsed.pathname.length > 1;
    if (host === 'youtube.com' || host.endsWith('.youtube.com')) {
      return Boolean(parsed.searchParams.get('v'))
        || /^\/(shorts|live|embed)\/[^/]+/.test(parsed.pathname);
    }
    return false;
  } catch {
    return false;
  }
}

export async function extract(url, { language = DEFAULT_LANGUAGE, userAgent = USER_AGENT } = {}) {
  const languages = await listLanguages(url, { userAgent });
  const selectedLanguage = selectLanguage(languages, language);
  if (!selectedLanguage) return null;

  const transcript = await fetchTranscript(url, {
    lang: selectedLanguage,
    userAgent,
  });
  const text = toPlainText(transcript, '\n').trim();
  if (!text) return null;

  const languageLabel = transcript.language || selectedLanguage;
  console.log(JSON.stringify({
    stage: 'youtube-transcript-extracted',
    language: languageLabel,
    segments: transcript.length,
    textLength: text.length,
  }));

  return `<article><h1>YouTube Transcript</h1><p>${escapeHtml(text).replaceAll('\n', '<br>')}</p></article>`;
}

function selectLanguage(languages, preferredLanguage) {
  const available = languages || [];
  return available.find((item) => item.languageCode === preferredLanguage)?.languageCode
    || available.find((item) => item.languageCode === 'en')?.languageCode
    || available[0]?.languageCode
    || null;
}

function escapeHtml(value) {
  return value.replace(/[&<>"']/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[character]));
}
