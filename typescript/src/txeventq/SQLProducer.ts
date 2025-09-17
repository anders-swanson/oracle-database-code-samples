import {type Result, type Connection, DB_TYPE_JSON, BIND_IN, type BindParameters} from "oracledb";

export class SQLProducer {
    constructor(private readonly conn: Connection) {
    }

    public async send(event: any): Promise<Result<unknown>> {
        // serialize the event to OSON
        const oson = this.conn.encodeOSON(event);
        const params: BindParameters = {
            oson: {
                type: DB_TYPE_JSON,
                dir: BIND_IN,
                val: oson
            }
        }
        return await this.conn.execute(
            `BEGIN
              produce_json_event(:oson);
            END;`,
            params
        )
    }
}