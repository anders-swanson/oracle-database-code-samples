import { describe, expect, it } from 'vitest';
import { parseInlineMarkdown } from '../src/lib/inlineMarkdown';

describe('inline markdown parsing', () => {
  it('renders relative README links against the GitHub README url', () => {
    const segments = parseInlineMarkdown(
      'Read [the SQL script](./src/test/resources/create-pdbs.sql) before running the sample.',
      'https://github.com/anders-swanson/oracle-database-code-samples/blob/main/database-per-service-example/sample/README.md'
    );

    expect(segments).toEqual([
      { kind: 'text', text: 'Read ' },
      {
        kind: 'link',
        text: 'the SQL script',
        href:
          'https://github.com/anders-swanson/oracle-database-code-samples/blob/main/database-per-service-example/sample/src/test/resources/create-pdbs.sql'
      },
      { kind: 'text', text: ' before running the sample.' }
    ]);
  });

  it('leaves unsafe links as plain text', () => {
    const segments = parseInlineMarkdown(
      'Do not click [this](javascript:alert1).',
      'https://github.com/anders-swanson/oracle-database-code-samples/blob/main/demo/README.md'
    );

    expect(segments).toEqual([
      { kind: 'text', text: 'Do not click ' },
      { kind: 'text', text: '[this](javascript:alert1)' },
      { kind: 'text', text: '.' }
    ]);
  });
});
