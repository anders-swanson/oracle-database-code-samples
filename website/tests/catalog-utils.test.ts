import { describe, expect, it } from 'vitest';
import { deriveSampleRecord, parseReadmeSource } from '../scripts/sample-catalog-utils.mjs';

describe('sample catalog utilities', () => {
  it('parses indented tags from front matter', () => {
    const parsed = parseReadmeSource(
      'demo/README.md',
      `---
name: demo
description: Demo sample
tags:
  - Vector
  - Java
blog_post: ""
---

# Demo

Demo body paragraph.
`
    );

    expect(parsed?.metadata.tags).toEqual(['Vector', 'Java']);
  });

  it('derives features and links from parsed README data', () => {
    const parsed = parseReadmeSource(
      'jdbc-demo/README.md',
      `---
name: demo
description: Demo vector sample with JDBC
tags:
  - Vector
  - JDBC
---

# Demo

Demo body paragraph.
`
    );

    const sample = deriveSampleRecord(parsed!);

    expect(sample.features).toContain('Vector Search');
    expect(sample.tags).toEqual(['Vector']);
    expect(sample.githubCodeUrl).toContain('/tree/main/jdbc-demo');
    expect(sample.githubReadmeUrl).toContain('/blob/main/jdbc-demo/README.md');
    expect(sample.urlPath).toBe('/samples/jdbc-demo/');
    expect(sample.canonicalUrl).toBe('https://anders-swanson.github.io/oracle-database-code-samples/samples/jdbc-demo/');
    expect(sample.metaTitle).toBe('Demo | Oracle AI Database Code Samples');
    expect(sample.ogImageUrl).toBe('https://anders-swanson.github.io/oracle-database-code-samples/social-card.svg');
  });
});
