function hasEvery(actualValues, expectedValues = []) {
  return expectedValues.every((value) => actualValues.includes(value));
}

function hasSome(actualValues, expectedValues = []) {
  return expectedValues.some((value) => actualValues.includes(value));
}

function pathHasPrefix(samplePath, prefixes = []) {
  return prefixes.some((prefix) => samplePath === prefix || samplePath.startsWith(`${prefix}/`));
}

function pathIncludes(samplePath, fragments = []) {
  return fragments.some((fragment) => samplePath.includes(fragment));
}

function matchesSelector(sample, selector) {
  if (selector.ids && !selector.ids.includes(sample.id)) {
    return false;
  }
  if (selector.pathPrefixes && !pathHasPrefix(sample.path, selector.pathPrefixes)) {
    return false;
  }
  if (selector.pathIncludes && !pathIncludes(sample.path, selector.pathIncludes)) {
    return false;
  }
  if (selector.featuresAll && !hasEvery(sample.features, selector.featuresAll)) {
    return false;
  }
  if (selector.featuresAny && !hasSome(sample.features, selector.featuresAny)) {
    return false;
  }

  return true;
}

function resolvePatternSampleIds(samples, sampleCriteria) {
  const includeSelectors = sampleCriteria?.include ?? [];
  const excludeSelectors = sampleCriteria?.exclude ?? [];

  return samples
    .filter((sample) => includeSelectors.some((selector) => matchesSelector(sample, selector)))
    .filter((sample) => !excludeSelectors.some((selector) => matchesSelector(sample, selector)))
    .map((sample) => sample.id);
}

export function buildPatternMappings(samples, definitions) {
  const intentIds = new Set(definitions.intents.map((intent) => intent.id));

  return {
    intents: definitions.intents,
    patterns: definitions.patterns.map(({ sampleCriteria, ...pattern }) => {
      if (!intentIds.has(pattern.intentId)) {
        throw new Error(`Pattern ${pattern.id} references unknown intent ${pattern.intentId}`);
      }

      const sampleIds = resolvePatternSampleIds(samples, sampleCriteria);

      if (sampleIds.length === 0) {
        throw new Error(`Pattern ${pattern.id} did not match any samples`);
      }

      return {
        ...pattern,
        sampleIds
      };
    })
  };
}
