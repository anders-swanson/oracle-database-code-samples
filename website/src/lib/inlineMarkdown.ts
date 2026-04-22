export interface InlineMarkdownSegment {
  kind: 'text' | 'link';
  text: string;
  href?: string;
}

const MARKDOWN_LINK_PATTERN = /\[([^\]]+)\]\(([^)]+)\)/g;
const SAFE_PROTOCOLS = new Set(['http:', 'https:', 'mailto:', 'tel:']);

export function parseInlineMarkdown(value: string, baseUrl: string): InlineMarkdownSegment[] {
  const segments: InlineMarkdownSegment[] = [];
  let cursor = 0;

  for (const match of value.matchAll(MARKDOWN_LINK_PATTERN)) {
    const [fullMatch, text, href] = match;
    const matchIndex = match.index ?? 0;

    if (matchIndex > cursor) {
      segments.push({
        kind: 'text',
        text: value.slice(cursor, matchIndex)
      });
    }

    const resolvedHref = resolveMarkdownHref(href.trim(), baseUrl);
    if (resolvedHref) {
      segments.push({
        kind: 'link',
        text,
        href: resolvedHref
      });
    } else {
      segments.push({
        kind: 'text',
        text: fullMatch
      });
    }

    cursor = matchIndex + fullMatch.length;
  }

  if (cursor < value.length) {
    segments.push({
      kind: 'text',
      text: value.slice(cursor)
    });
  }

  return segments.length > 0 ? segments : [{ kind: 'text', text: value }];
}

function resolveMarkdownHref(href: string, baseUrl: string): string | null {
  if (!href) {
    return null;
  }

  if (href.startsWith('#')) {
    return href;
  }

  try {
    const resolvedUrl = new URL(href, baseUrl);
    if (!SAFE_PROTOCOLS.has(resolvedUrl.protocol)) {
      return null;
    }

    return resolvedUrl.toString();
  } catch {
    return null;
  }
}
