import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import {OracleDatabaseContainer} from "../../src/testcontainers/oracle_database_container.js";

describe("OracleDatabaseContainer", () => {
    let db: OracleDatabaseContainer;

    beforeAll(async () => {
        db = new OracleDatabaseContainer();
        await db.start();
    }, 10 * 60 * 1000); // With a pre-pulled image, the container should start up in seconds.

    afterAll(async () => {
        await db.stop();
    });

    it("should connect and get the current database version", async () => {
        let conn = await db.getDatabaseConnection();

        const result = await conn.execute("select * from V$VERSION")

        expect(result).not.toBeUndefined()
        if (result.rows) {
            for (const row of result.rows) {
                console.log(row);
            }
        }

        await conn.close();
    });
});