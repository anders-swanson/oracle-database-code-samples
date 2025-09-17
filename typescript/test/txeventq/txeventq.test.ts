import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import {
    OracleDatabaseContainer,
    StartedOracleDatabaseContainer
} from "../../src/testcontainers/generic_oracle_database_container.js";
import type {Connection} from "oracledb";
import {SQLProducer} from "../../src/txeventq/SQLProducer.js";
import {SQLConsumer} from "../../src/txeventq/SQLConsumer.js";

describe("Transactional Event Queues", () => {
    let startedContainer: StartedOracleDatabaseContainer
    let conn: Connection

    beforeAll(async () => {
        let container = new OracleDatabaseContainer()
            .withCopyFilesToContainer([{
                source: "./test/txeventq/init.sql",
                target: "/tmp/init.sql"
            }])
        startedContainer = await container.start()
        let {output, exitCode} = await startedContainer.exec("sqlplus / as sysdba @/tmp/init.sql")
        expect(exitCode).toBe(0)
        conn = await startedContainer.getDatabaseConnection()
    }, 10 * 60 * 1000); // With a pre-pulled image, the container should start up in seconds.

    afterAll(async () => {
        await startedContainer.stop()
    });

    it("plsql pub-sub", async () => {
        let producer = new SQLProducer(conn)
        let event = {
            message: "Hello from TxEventQ!"
        }
        await producer.send(event)
        console.log("Produced event: " + JSON.stringify(event))

        let result: any
        let consumer = new SQLConsumer(await startedContainer.getDatabaseConnection())
        while (!result) {
            result = await consumer.poll()
        }
        expect(result.message).toEqual(event.message)
        console.log("Consumed event: " + JSON.stringify(result))
    }, 5000)
});