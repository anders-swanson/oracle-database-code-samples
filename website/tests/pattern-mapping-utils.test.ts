import { describe, expect, it } from 'vitest';
import { buildPatternMappings } from '../scripts/pattern-mapping-utils.mjs';

describe('pattern mapping generation', () => {
  it('builds pattern sample ids from sample metadata criteria', () => {
    const samples = [
      {
        id: 'vector-java',
        path: 'vector-java',
        language: 'Java',
        tags: ['Vector Search'],
        features: ['Vector Search']
      },
      {
        id: 'json-basic',
        path: 'json/jdbc-json-basic',
        language: 'Java',
        tags: ['JSON'],
        features: ['JSON']
      },
      {
        id: 'excluded-agent',
        path: 'agent',
        language: 'Java',
        tags: ['AI'],
        features: ['Vector Search', 'AI Agents']
      }
    ];

    const mappings = buildPatternMappings(samples, {
      intents: [
        {
          id: 'build-ai-experiences',
          title: 'Build AI experiences',
          summary: 'Retrieval and agent workflows',
          color: '#59d4ff'
        }
      ],
      patterns: [
        {
          id: 'semantic-search-rag',
          intentId: 'build-ai-experiences',
          title: 'Semantic Search / RAG',
          summary: 'Search by meaning.',
          useWhen: 'Use this when retrieval needs grounded records.',
          features: ['Vector Search'],
          sampleCriteria: {
            include: [{ featuresAll: ['Vector Search'] }, { pathIncludes: ['json/jdbc-json'] }],
            exclude: [{ featuresAll: ['AI Agents'] }]
          }
        }
      ]
    });

    expect(mappings.patterns[0].sampleIds).toEqual(['vector-java', 'json-basic']);
  });
});
