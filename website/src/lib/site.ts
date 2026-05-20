import rawSiteMetadata from '../data/siteMetadata.json';

export const siteMetadata = rawSiteMetadata;

export const SITE_NAME = siteMetadata.siteName;
export const SITE_TITLE = siteMetadata.siteTitle;
export const SITE_URL = siteMetadata.siteUrl;
export const SITE_BASE_PATH = siteMetadata.siteBasePath;
export const SAMPLE_SOCIAL_CARD_DIRECTORY = siteMetadata.sampleSocialCardDirectory;
export const SOCIAL_CARD_EXTENSION = siteMetadata.socialCardExtension;
export const SOCIAL_CARD_WIDTH = siteMetadata.socialCardWidth;
export const SOCIAL_CARD_HEIGHT = siteMetadata.socialCardHeight;
export const DEFAULT_DESCRIPTION = siteMetadata.defaultDescription;
export const DEFAULT_OG_IMAGE_ALT = siteMetadata.defaultOgImageAlt;
export const DEFAULT_OG_IMAGE_URL = `${SITE_URL}social-card.${SOCIAL_CARD_EXTENSION}`;

export function buildCanonicalUrl(pathname = '/') {
  const normalized = pathname.startsWith('/') ? pathname.slice(1) : pathname;
  return new URL(normalized, SITE_URL).toString();
}

export function buildLanguagePath(slug: string) {
  return `/languages/${slug}/`;
}

export function buildSamplePath(id: string) {
  return `/samples/${id}/`;
}
