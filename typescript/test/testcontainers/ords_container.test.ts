import {afterAll, beforeAll, describe, expect, it} from "vitest";
import {type ExecResult, GenericContainer, Network, type StartedNetwork, type StartedTestContainer, Wait} from "testcontainers";
import {MongoClient, type Collection} from "mongodb";

import {OrdsContainer, type StartedOrdsContainer} from "../../src/testcontainers/ords_container.js";

const DATABASE_IMAGE = "gvenzl/oracle-free:23.26.3-slim-faststart";
const DATABASE_ALIAS = "ordsdb";
const ADMIN_PASSWORD = "Welcome12345";
const DATABASE_CONNECTION = "jdbc:oracle:thin:@ordsdb:1521/freepdb1";
const SCHEMA_CONNECTION = "ordsdb:1521/freepdb1";
const DB_API_ADMIN_USERNAME = "ordsuser";
const DB_API_ADMIN_PASSWORD = "ordsuserpwd";
const MONGO_USERNAME = "mongouser";
const MONGO_PASSWORD = "mongouserpwd";
const MONGO_COLLECTION_NAME = "compat_typescript";
const MONGO_DOCUMENT_ID = "typescript-ords-document";

const ORDS_INIT_SQL = [
    "WHENEVER SQLERROR EXIT SQL.SQLCODE;",
    "ALTER SESSION SET CONTAINER = freepdb1;",
    "CREATE USER ordsuser IDENTIFIED BY ordsuserpwd QUOTA UNLIMITED ON users;",
    "GRANT connect, pdb_dba TO ordsuser;",
    "CREATE USER mongouser IDENTIFIED BY mongouserpwd QUOTA UNLIMITED ON users;",
    "GRANT create session, create table, soda_app TO mongouser;",
    "EXIT;"
].join("\n") + "\n";

const VERIFY_ORDS_USER_SQL = "SELECT username FROM user_users;\nEXIT;\n";

type DatabaseVersionResponse = {
    instance_name?: string;
    instance_version?: Array<{ banner?: string }>;
};

type CompatDocument = {
    _id: string;
    name: string;
    credits: number;
    active: boolean;
};

const MONGO_DOCUMENT: CompatDocument = {
    _id: MONGO_DOCUMENT_ID,
    name: "Alice",
    credits: 12,
    active: true
};

describe("OrdsContainer", () => {
    let network: StartedNetwork;
    let oracleContainer: StartedTestContainer;
    let ordsContainer: StartedOrdsContainer;

    beforeAll(async () => {
        network = await new Network().start();
        oracleContainer = await new GenericContainer(DATABASE_IMAGE)
            .withExposedPorts(1521)
            .withEnvironment({
                ORACLE_PASSWORD: ADMIN_PASSWORD,
                APP_USER: "testuser",
                APP_USER_PASSWORD: "testpwd"
            })
            .withNetwork(network)
            .withNetworkAliases(DATABASE_ALIAS)
            .withWaitStrategy(Wait.forLogMessage("DATABASE IS READY TO USE!"))
            .withStartupTimeout(5 * 60 * 1000)
            .start();

        await initializeDatabase(oracleContainer);

        ordsContainer = await new OrdsContainer()
            .withNetwork(network)
            .withDatabaseConnectionString(DATABASE_CONNECTION)
            .withOraclePassword(ADMIN_PASSWORD)
            .withSchema(DB_API_ADMIN_USERNAME, DB_API_ADMIN_PASSWORD, SCHEMA_CONNECTION)
            .withSchema(MONGO_USERNAME, MONGO_PASSWORD, SCHEMA_CONNECTION)
            .start();
    }, 10 * 60 * 1000);

    afterAll(async () => {
        await ordsContainer?.stop();
        await oracleContainer?.stop();
        await network?.stop();
    });

    it("starts ORDS against Oracle AI Database", async () => {
        const response = await fetch(ordsContainer.getBaseUrl());

        expect(response.status).toBeLessThan(400);
        expect(ordsContainer.getMongoDbApiPort()).toBeGreaterThan(0);
    });

    it("gets database version from ORDS API", async () => {
        const response = await fetch(
            `${ordsContainer.getBaseUrl()}/ords/${DB_API_ADMIN_USERNAME}/_/db-api/stable/database/version`,
            {headers: {Authorization: basicAuth(DB_API_ADMIN_USERNAME, DB_API_ADMIN_PASSWORD)}}
        );

        expect(response.status).toBe(200);
        expect(response.headers.get("content-type")).toContain("application/json");

        const databaseVersion = await response.json() as DatabaseVersionResponse;
        expect(databaseVersion.instance_name).toBeTruthy();
        expect(databaseVersion.instance_version?.length).toBeGreaterThan(0);
        expect(databaseVersion.instance_version?.[0]?.banner).toBeTruthy();
    });

    it("supports MongoDB client CRUD operations", async () => {
        await withMongoCollection(ordsContainer, async (collection) => {
            await collection.insertOne(MONGO_DOCUMENT);

            const insertedDocument = await collection.findOne({_id: MONGO_DOCUMENT_ID});
            expect(insertedDocument).not.toBeNull();
            expect(insertedDocument?.name).toBe("Alice");
            expect(insertedDocument?.credits).toBe(12);
            expect(insertedDocument?.active).toBe(true);

            const updated = await collection.updateOne(
                {_id: MONGO_DOCUMENT_ID},
                {$set: {credits: 15}}
            );
            expect(updated.modifiedCount).toBe(1);
            expect((await collection.findOne({_id: MONGO_DOCUMENT_ID}))?.credits).toBe(15);

            const deleted = await collection.deleteOne({_id: MONGO_DOCUMENT_ID});
            expect(deleted.deletedCount).toBe(1);
            expect(await collection.findOne({_id: MONGO_DOCUMENT_ID})).toBeNull();
        });
    });
});

async function initializeDatabase(container: StartedTestContainer): Promise<void> {
    await runSqlPlus(container, "/ as sysdba", ORDS_INIT_SQL, "Database initialization failed");
    await runSqlPlus(
        container,
        `${DB_API_ADMIN_USERNAME}/${DB_API_ADMIN_PASSWORD}@localhost:1521/freepdb1`,
        VERIFY_ORDS_USER_SQL,
        "ORDS test user verification failed"
    );
}

async function runSqlPlus(
    container: StartedTestContainer,
    connectionString: string,
    sql: string,
    failureMessage: string
): Promise<void> {
    const result = await container.exec([
        "bash",
        "-lc",
        `printf %s ${shellQuote(sql)} | sqlplus -s ${shellQuote(connectionString)}`
    ]);
    assertSuccessfulExec(result, failureMessage);
}

function assertSuccessfulExec(result: ExecResult, message: string): void {
    if (result.exitCode === 0) {
        return;
    }

    throw new Error(`${message}.\nstdout:\n${result.stdout}\nstderr:\n${result.stderr}`);
}

async function withMongoCollection(
    ordsContainer: StartedOrdsContainer,
    callback: (collection: Collection<CompatDocument>) => Promise<void>
): Promise<void> {
    const client = new MongoClient(mongoConnectionString(ordsContainer), {tlsAllowInvalidCertificates: true});
    await client.connect();
    const collection = client.db(MONGO_USERNAME).collection<CompatDocument>(MONGO_COLLECTION_NAME);

    try {
        await callback(collection);
    } finally {
        await collection.drop().catch(() => undefined);
        await client.close();
    }
}

function mongoConnectionString(ordsContainer: StartedOrdsContainer): string {
    return (
        `mongodb://${MONGO_USERNAME}:${MONGO_PASSWORD}` +
        `@${ordsContainer.getHost()}:${ordsContainer.getMongoDbApiPort()}` +
        `/${MONGO_USERNAME}?authMechanism=PLAIN&authSource=%24external` +
        "&tls=true&retryWrites=false&loadBalanced=true"
    );
}

function basicAuth(username: string, password: string): string {
    return `Basic ${Buffer.from(`${username}:${password}`, "utf8").toString("base64")}`;
}

function shellQuote(value: string): string {
    return `'${value.replaceAll("'", "'\"'\"'")}'`;
}
