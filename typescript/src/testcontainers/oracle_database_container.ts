import {GenericContainer, type StartedTestContainer, Wait} from "testcontainers";
import * as OracleDB from "oracledb";
import type {Connection} from "oracledb";

// Implements an Oracle Database Free container for Testcontainers.
// Run the sample using "npm run testcontainers-example"
export class OracleDatabaseContainer {
    private container?: StartedTestContainer | undefined;

    constructor(
        private readonly image: string = "gvenzl/oracle-free:23.9-slim-faststart",
        private readonly port: number = 1521,
        private readonly username: string = "testuser",
        private readonly password: string = "Welcome12345",
        private readonly serviceName: string = "freepdb1"
    ) {}

    public async start(): Promise<StartedTestContainer> {
        this.container = await new GenericContainer(this.image)
            .withExposedPorts(this.port)
            .withEnvironment({
                "ORACLE_RANDOM_PASSWORD": "y",
                "APP_USER": this.username,
                "APP_USER_PASSWORD": this.password
            })
            .withWaitStrategy(Wait.forLogMessage("DATABASE IS READY TO USE!"))
            .start();

        return this.container;
    }

    public getHost(): string {
        if (!this.container) throw new Error("Container not started yet");
        return this.container.getHost();
    }

    public getPort(): number {
        if (!this.container) throw new Error("Container not started yet");
        return this.container.getMappedPort(this.port);
    }

    public async getDatabaseConnection(): Promise<Connection> {
        return OracleDB.getConnection({
            user: this.username,
            password: this.password,
            connectionString: this.getConnectionString()
        })
    }

    public getConnectionString(): string {
        return `${this.getHost()}:${this.getPort()}/${this.serviceName}`;
    }

    public async stop(): Promise<void> {
        if (this.container) {
            await this.container.stop();
            this.container = undefined;
        }
    }
}
