export interface FeatureIcon {
  iconPath: string;
  sourceLabel: string;
}

interface FeatureIconDefinition {
  file: string;
  sourceLabel: string;
}

const featureIconDefinitions: Record<string, FeatureIconDefinition> = {
  AI: {
    file: 'ai.svg',
    sourceLabel: 'Artificial-Intelligence'
  },
  'AI Agents': {
    file: 'ai.svg',
    sourceLabel: 'Artificial-Intelligence'
  },
  'Duality Views': {
    file: 'duality-views.svg',
    sourceLabel: 'HJSON Relational Duality'
  },
  Graph: {
    file: 'graph.svg',
    sourceLabel: 'Graph'
  },
  'Property Graph': {
    file: 'graph.svg',
    sourceLabel: 'Graph'
  },
  GraphQL: {
    file: 'graphql.svg',
    sourceLabel: 'API'
  },
  'SQL GraphQL': {
    file: 'graphql.svg',
    sourceLabel: 'API'
  },
  JMS: {
    file: 'jms.svg',
    sourceLabel: 'Transaction-Processing'
  },
  JPA: {
    file: 'jpa.svg',
    sourceLabel: 'Data-Model'
  },
  JSON: {
    file: 'json.svg',
    sourceLabel: 'Database Badge {}'
  },
  Kafka: {
    file: 'kafka.svg',
    sourceLabel: 'Database Apache Kafka'
  },
  MCP: {
    file: 'mcp.svg',
    sourceLabel: 'Database-Tools-Service'
  },
  MongoDB: {
    file: 'mongodb.svg',
    sourceLabel: 'Mongo DB'
  },
  Observability: {
    file: 'observability.svg',
    sourceLabel: 'Dashboard'
  },
  OCI: {
    file: 'oci.svg',
    sourceLabel: 'Oracle_Cloud-at-Customer'
  },
  ORDS: {
    file: 'ords.svg',
    sourceLabel: 'Oracle-REST-Data-Services'
  },
  'Oracle Text': {
    file: 'oracle-text.svg',
    sourceLabel: 'database-text-search'
  },
  PDB: {
    file: 'pdb.svg',
    sourceLabel: 'Database-Pluggable'
  },
  'PL/SQL': {
    file: 'plsql.svg',
    sourceLabel: 'Database Badge SQL'
  },
  Python: {
    file: 'python.svg',
    sourceLabel: 'Programming Languages'
  },
  SpringBoot: {
    file: 'springboot.svg',
    sourceLabel: 'Server-Application'
  },
  Spring: {
    file: 'springboot.svg',
    sourceLabel: 'Server-Application'
  },
  Testcontainers: {
    file: 'testcontainers.svg',
    sourceLabel: 'Database-Container'
  },
  TxEventQ: {
    file: 'txeventq.svg',
    sourceLabel: 'Transactional-event-queue'
  },
  'Vector Search': {
    file: 'vector-search.png',
    sourceLabel: 'Vector Search'
  },
  microservices: {
    file: 'microservices.svg',
    sourceLabel: 'Application-Strategy'
  },
  security: {
    file: 'security.svg',
    sourceLabel: 'Data-Security'
  },
  Security: {
    file: 'security.svg',
    sourceLabel: 'Data-Security'
  },
  Spatial: {
    file: 'spatial.svg',
    sourceLabel: 'Spatial'
  },
  SQL: {
    file: 'plsql.svg',
    sourceLabel: 'Database Badge SQL'
  },
  SQLcl: {
    file: 'sqlcl.svg',
    sourceLabel: 'SQL-Developer-Command-Line'
  }
};

function buildIconPath(file: string) {
  const baseUrl = import.meta.env.BASE_URL.endsWith('/') ? import.meta.env.BASE_URL : `${import.meta.env.BASE_URL}/`;
  return `${baseUrl}feature-icons/${file}`;
}

export function getFeatureIcon(feature: string): FeatureIcon | undefined {
  const definition = featureIconDefinitions[feature];

  if (!definition) {
    return undefined;
  }

  return {
    iconPath: buildIconPath(definition.file),
    sourceLabel: definition.sourceLabel
  };
}
