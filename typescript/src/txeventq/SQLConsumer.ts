import {BIND_OUT, type BindParameters, type Connection, DB_TYPE_JSON} from "oracledb";

export class SQLConsumer {
    constructor(private readonly conn: Connection) {
    }

    public async poll(): Promise<any> {
        const params: BindParameters = {
            oson: {
                type: DB_TYPE_JSON,
                dir: BIND_OUT,
            }
        }
        let result = await this.conn.execute(
            `BEGIN
              :oson := consume_json_event();
            END;`,
            params
        );
        if (result.outBinds) {
            // @ts-ignore
            return this.conn.decodeOSON(result.outBinds.oson)
        }
        return undefined
    }
}