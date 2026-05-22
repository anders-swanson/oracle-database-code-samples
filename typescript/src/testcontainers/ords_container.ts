import {AbstractStartedContainer, type ExecResult, GenericContainer, type StartedTestContainer, Wait} from "testcontainers";

const DEFAULT_ORDS_IMAGE = "container-registry.oracle.com/database/ords:latest";
const HTTP_PORT = 8080;
const HTTPS_PORT = 8443;
const MONGODB_API_PORT = 27017;

const CONNECTION_STRING_ENV = "CONN_STRING";
const ORACLE_PASSWORD_ENV = "ORACLE_PWD";
const ORDS_JAVA_OPTIONS_ENV = "_JAVA_OPTIONS";
const DEFAULT_ORDS_JAVA_OPTIONS = "-Xms128m -Xmx768m";
const DEFAULT_STARTUP_TIMEOUT_MS = 5 * 60 * 1000;
const ENABLE_SCHEMA_SQL = [
    "WHENEVER SQLERROR EXIT SQL.SQLCODE",
    "EXECUTE ORDS.ENABLE_SCHEMA;",
    "EXIT;"
].join("\n") + "\n";

type SchemaConfiguration = {
    username: string;
    password: string;
    connectDescriptor: string;
};

export class OrdsContainer extends GenericContainer {
    private readonly schemas: SchemaConfiguration[] = [];

    constructor(
        image: string = DEFAULT_ORDS_IMAGE,
        private readonly httpPort: number = HTTP_PORT,
        private readonly httpsPort: number = HTTPS_PORT,
        private readonly mongodbApiPort: number = MONGODB_API_PORT
    ) {
        super(image);
        this.withExposedPorts(this.httpPort, this.httpsPort, this.mongodbApiPort)
            .withEnvironment({[ORDS_JAVA_OPTIONS_ENV]: DEFAULT_ORDS_JAVA_OPTIONS})
            .withWaitStrategy(
                Wait.forHttp("/", this.httpPort)
                    .forStatusCodeMatching((status) => status >= 200 && status < 500)
                    .withStartupTimeout(DEFAULT_STARTUP_TIMEOUT_MS)
            );
    }

    public withDatabaseConnectionString(connectionString: string): this {
        return this.withRequiredEnvironment(
            CONNECTION_STRING_ENV,
            connectionString,
            "Oracle AI Database connection string cannot be empty"
        );
    }

    public withOraclePassword(oraclePassword: string): this {
        return this.withRequiredEnvironment(
            ORACLE_PASSWORD_ENV,
            oraclePassword,
            "Oracle AI Database password cannot be empty"
        );
    }

    public withSchema(username: string, password: string, connectDescriptor: string): this {
        this.schemas.push({
            username: requireNonBlank(username, "Schema username is required"),
            password: requireNonBlank(password, "Schema password is required"),
            connectDescriptor: requireNonBlank(connectDescriptor, "Schema connect descriptor is required")
        });
        return this;
    }

    public override async start(): Promise<StartedOrdsContainer> {
        this.validateRequiredEnv(CONNECTION_STRING_ENV);
        this.validateRequiredEnv(ORACLE_PASSWORD_ENV);

        const container = await super.start();
        try {
            for (const schema of this.schemas) {
                await enableSchema(container, schema);
            }
        } catch (error) {
            await container.stop().catch(() => undefined);
            throw error;
        }

        return new StartedOrdsContainer(container, this.httpPort, this.httpsPort, this.mongodbApiPort);
    }

    private validateRequiredEnv(envName: string): void {
        if (isBlank(this.environment[envName])) {
            throw new Error(`${envName} must be configured before starting ORDS`);
        }
    }

    private withRequiredEnvironment(envName: string, value: string, message: string): this {
        return this.withEnvironment({[envName]: requireNonBlank(value, message)});
    }
}

export class StartedOrdsContainer extends AbstractStartedContainer {
    constructor(
        startedTestContainer: StartedTestContainer,
        private readonly httpPort: number,
        private readonly httpsPort: number,
        private readonly mongodbApiPort: number
    ) {
        super(startedTestContainer);
    }

    public getBaseUrl(): string {
        return `http://${this.getHost()}:${this.getHttpPort()}`;
    }

    public getHttpPort(): number {
        return this.getMappedPort(this.httpPort);
    }

    public getHttpsPort(): number {
        return this.getMappedPort(this.httpsPort);
    }

    public getMongoDbApiPort(): number {
        return this.getMappedPort(this.mongodbApiPort);
    }
}

async function enableSchema(container: StartedTestContainer, schema: SchemaConfiguration): Promise<void> {
    const result = await container.exec([
        "bash",
        "-lc",
        `printf %s ${shellQuote(ENABLE_SCHEMA_SQL)} | sql -s ${shellQuote(schemaConnectionString(schema))}`
    ]);

    assertSuccessfulExec(result, "ORDS schema enablement failed");
}

function schemaConnectionString(schema: SchemaConfiguration): string {
    return `${schema.username}/${schema.password}@${schema.connectDescriptor}`;
}

function assertSuccessfulExec(result: ExecResult, message: string): void {
    if (result.exitCode === 0) {
        return;
    }
    throw new Error(
        `${message}.\nstdout:\n${result.stdout}\nstderr:\n${result.stderr}`
    );
}

function requireNonBlank(value: string, message: string): string {
    if (isBlank(value)) {
        throw new Error(message);
    }
    return value;
}

function isBlank(value: string | undefined): boolean {
    return value === undefined || value.trim() === "";
}

function shellQuote(value: string): string {
    return `'${value.replaceAll("'", "'\"'\"'")}'`;
}
