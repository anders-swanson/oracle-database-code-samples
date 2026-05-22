import base64
import json
import unittest
from contextlib import suppress
from shlex import quote
from urllib import request

from pymongo import MongoClient
from testcontainers.core.network import Network

from src.python_oracle.testcontainers_sample.oracle_database_container import OracleDatabaseContainer
from src.python_oracle.testcontainers_sample.ords_container import OrdsContainer


DATABASE_IMAGE = "gvenzl/oracle-free:23.26.1-slim-faststart"
DATABASE_ALIAS = "ordsdb"
ADMIN_PASSWORD = "Welcome12345"
DATABASE_CONNECTION = "jdbc:oracle:thin:@ordsdb:1521/freepdb1"
SCHEMA_CONNECTION = "ordsdb:1521/freepdb1"
DB_API_ADMIN_USERNAME = "ordsuser"
DB_API_ADMIN_PASSWORD = "ordsuserpwd"
MONGO_USERNAME = "mongouser"
MONGO_PASSWORD = "mongouserpwd"

ORDS_INIT_SQL = """
WHENEVER SQLERROR EXIT SQL.SQLCODE;
ALTER SESSION SET CONTAINER = freepdb1;
CREATE USER ordsuser IDENTIFIED BY ordsuserpwd QUOTA UNLIMITED ON users;
GRANT connect, pdb_dba TO ordsuser;
CREATE USER mongouser IDENTIFIED BY mongouserpwd QUOTA UNLIMITED ON users;
GRANT create session, create table, soda_app TO mongouser;
EXIT;
"""


class OrdsContainerIntegrationTest(unittest.TestCase):
    network: Network
    oracle_container: OracleDatabaseContainer
    ords_container: OrdsContainer

    @classmethod
    def setUpClass(cls) -> None:
        cls.network = Network().create()
        try:
            cls.oracle_container = OracleDatabaseContainer(
                image=DATABASE_IMAGE,
                app_user="testuser",
                app_user_password="testpwd12345",
                oracle_password=ADMIN_PASSWORD,
                network=cls.network,
                network_aliases=[DATABASE_ALIAS]
            )
            cls.oracle_container.start()
            cls._initialize_database()

            cls.ords_container = OrdsContainer(network=cls.network) \
                .with_database_connection_string(DATABASE_CONNECTION) \
                .with_oracle_password(ADMIN_PASSWORD) \
                .with_schema(DB_API_ADMIN_USERNAME, DB_API_ADMIN_PASSWORD, SCHEMA_CONNECTION) \
                .with_schema(MONGO_USERNAME, MONGO_PASSWORD, SCHEMA_CONNECTION)
            cls.ords_container.start()
        except Exception:
            cls._cleanup_containers()
            raise

    @classmethod
    def tearDownClass(cls) -> None:
        cls._cleanup_containers()

    def test_starts_ords_against_oracle_database(self) -> None:
        with request.urlopen(self.ords_container.get_base_url(), timeout=30) as response:
            self.assertLess(response.status, 400)
        self.assertGreater(self.ords_container.get_mongodb_api_port(), 0)

    def test_gets_database_version_from_ords_api(self) -> None:
        api_request = request.Request(self._ords_database_api_url("database/version"))
        api_request.add_header("Authorization", self._basic_auth(DB_API_ADMIN_USERNAME, DB_API_ADMIN_PASSWORD))

        with request.urlopen(api_request, timeout=30) as response:
            self.assertEqual(200, response.status)
            content_type = response.headers.get("Content-Type", "")
            self.assertIn("application/json", content_type)
            payload = json.loads(response.read())

        self.assertIsNotNone(payload.get("instance_name"))
        instance_version = payload.get("instance_version")
        self.assertTrue(instance_version)
        self.assertIsNotNone(instance_version[0].get("banner"))

    def test_supports_mongo_client_crud_operations(self) -> None:
        collection_name = "compat_python"
        document_id = "python-ords-document"
        client = MongoClient(self._mongo_connection_string(), tlsAllowInvalidCertificates=True)
        self.addCleanup(client.close)
        collection = client[MONGO_USERNAME][collection_name]
        self.addCleanup(lambda: collection.drop())

        collection.insert_one({"_id": document_id, "name": "Alice", "credits": 12, "active": True})

        inserted_document = collection.find_one({"_id": document_id})
        self.assertIsNotNone(inserted_document)
        self.assertEqual("Alice", inserted_document["name"])
        self.assertEqual(12, inserted_document["credits"])
        self.assertTrue(inserted_document["active"])

        updated = collection.update_one({"_id": document_id}, {"$set": {"credits": 15}})
        self.assertEqual(1, updated.modified_count)
        updated_document = collection.find_one({"_id": document_id})
        self.assertIsNotNone(updated_document)
        self.assertEqual(15, updated_document["credits"])

        deleted = collection.delete_one({"_id": document_id})
        self.assertEqual(1, deleted.deleted_count)
        self.assertIsNone(collection.find_one({"_id": document_id}))

    @classmethod
    def _initialize_database(cls) -> None:
        cls._run_sqlplus_script(
            script=ORDS_INIT_SQL,
            connect_string="/ as sysdba",
            failure_message="Database initialization failed"
        )

        cls._run_sqlplus_script(
            script="SELECT username FROM user_users;\nEXIT;\n",
            connect_string="ordsuser/ordsuserpwd@localhost:1521/freepdb1",
            failure_message="ORDS test user verification failed"
        )

    @classmethod
    def _run_sqlplus_script(cls, script: str, connect_string: str, failure_message: str) -> None:
        command = f"printf %s {quote(script)} | sqlplus -s {quote(connect_string)}"
        result = cls.oracle_container.exec(["bash", "-lc", command])
        if result.exit_code != 0:
            raise RuntimeError(f"{failure_message}.\noutput:\n{_decode_exec_output(result.output)}")

    def _ords_database_api_url(self, relative_path: str) -> str:
        return f"{self.ords_container.get_base_url()}/ords/{DB_API_ADMIN_USERNAME}/_/db-api/stable/{relative_path}"

    def _mongo_connection_string(self) -> str:
        return (
            f"mongodb://{MONGO_USERNAME}:{MONGO_PASSWORD}"
            f"@{self.ords_container.get_container_host_ip()}:{self.ords_container.get_mongodb_api_port()}"
            f"/{MONGO_USERNAME}?authMechanism=PLAIN&authSource=%24external"
            "&tls=true&retryWrites=false&loadBalanced=true"
        )

    @staticmethod
    def _basic_auth(username: str, password: str) -> str:
        credentials = base64.b64encode(f"{username}:{password}".encode()).decode()
        return f"Basic {credentials}"

    @classmethod
    def _cleanup_containers(cls) -> None:
        for name, method_name in (
                ("ords_container", "stop"),
                ("oracle_container", "stop"),
                ("network", "remove")
        ):
            resource = getattr(cls, name, None)
            if resource is None:
                continue
            with suppress(Exception):
                getattr(resource, method_name)()
            with suppress(AttributeError):
                delattr(cls, name)


def _decode_exec_output(output) -> str:
    return output.decode(errors="replace") if isinstance(output, bytes) else str(output)


if __name__ == "__main__":
    unittest.main()
