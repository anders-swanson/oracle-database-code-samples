import {AbstractStartedContainer, GenericContainer, type StartedTestContainer, Wait} from "testcontainers";
import * as OracleDB from "oracledb";
import type {Connection} from "oracledb";

// Implements an Oracle Database Free container for Testcontainers.
// Run the sample using "npm run testcontainers-example"
export class OracleDatabaseContainer extends GenericContainer {
    constructor(
        image: string = "gvenzl/oracle-free:23.9-slim-faststart",
        private readonly port: number = 1521,
        private readonly username: string = "testuser",
        private readonly password: string = "Welcome12345",
        private readonly serviceName: string = "freepdb1"
    ) {
        super(image);
    }

    public override async start(): Promise<StartedOracleDatabaseContainer> {
        this.withExposedPorts(this.port)
            .withEnvironment({
                "ORACLE_RANDOM_PASSWORD": "y",
                "APP_USER": this.username,
                "APP_USER_PASSWORD": this.password
            })
            .withWaitStrategy(Wait.forLogMessage("DATABASE IS READY TO USE!"))
        return new StartedOracleDatabaseContainer(
            await super.start(),
            this.port,
            this.username,
            this.password,
            this.serviceName
        )
    }
}

export class StartedOracleDatabaseContainer extends AbstractStartedContainer {
    constructor(
        startedTestContainer: StartedTestContainer,
        private readonly port: number,
        private readonly username: string,
        private readonly password: string,
        private readonly serviceName: string
    ) {
        super(startedTestContainer);
    }

    public getPort(): number {
        return super.getMappedPort(this.port);
    }

    public getUsername(): string {
        return this.username;
    }

    public getPassword(): string {
        return this.password;
    }

    public getConnectionString(): string {
        return `${this.getHost()}:${this.getPort()}/${this.serviceName}`;
    }

    public async getDatabaseConnection(): Promise<Connection> {
        return OracleDB.getConnection({
            user: this.username,
            password: this.password,
            connectionString: this.getConnectionString()
        })
    }

}
