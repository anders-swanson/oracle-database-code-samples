from dataclasses import dataclass
from datetime import timedelta
from shlex import quote

from testcontainers.core.container import DockerContainer
from testcontainers.core.wait_strategies import HttpWaitStrategy


DEFAULT_ORDS_IMAGE = "container-registry.oracle.com/database/ords:latest"
HTTP_PORT = 8080
HTTPS_PORT = 8443
MONGODB_API_PORT = 27017

_CONNECTION_STRING_ENV = "CONN_STRING"
_ORACLE_PASSWORD_ENV = "ORACLE_PWD"
_SQL_ERROR_EXIT = "WHENEVER SQLERROR EXIT SQL.SQLCODE"


@dataclass(frozen=True)
class SchemaConfiguration:
    username: str
    password: str
    connect_descriptor: str

    @property
    def connection_string(self) -> str:
        return f"{self.username}/{self.password}@{self.connect_descriptor}"


class OrdsContainer(DockerContainer):
    def __init__(
            self,
            image: str = DEFAULT_ORDS_IMAGE,
            http_port: int = HTTP_PORT,
            https_port: int = HTTPS_PORT,
            mongodb_api_port: int = MONGODB_API_PORT,
            **kwargs
    ):
        super().__init__(image=image, **kwargs)
        self.http_port = http_port
        self.https_port = https_port
        self.mongodb_api_port = mongodb_api_port
        self._schemas: list[SchemaConfiguration] = []

        self.with_exposed_ports(http_port, https_port, mongodb_api_port)
        self.waiting_for(
            HttpWaitStrategy(http_port, "/")
            .for_status_code_matching(lambda status: 200 <= status < 500)
            .with_startup_timeout(timedelta(minutes=5))
        )

    def with_database_connection_string(self, connection_string: str) -> "OrdsContainer":
        return self.with_env(
            _CONNECTION_STRING_ENV,
            _require_non_blank(connection_string, "Oracle AI Database connection string cannot be null or empty")
        )

    def with_oracle_password(self, oracle_password: str) -> "OrdsContainer":
        return self.with_env(
            _ORACLE_PASSWORD_ENV,
            _require_non_blank(oracle_password, "Oracle AI Database password cannot be null or empty")
        )

    def with_schema(self, username: str, password: str, connect_descriptor: str) -> "OrdsContainer":
        self._schemas.append(
            SchemaConfiguration(
                username=_require_non_blank(username, "Schema username is required"),
                password=_require_non_blank(password, "Schema password is required"),
                connect_descriptor=_require_non_blank(connect_descriptor, "Schema connect descriptor is required")
            )
        )
        return self

    def start(self) -> "OrdsContainer":
        self._validate_required_env(_CONNECTION_STRING_ENV)
        self._validate_required_env(_ORACLE_PASSWORD_ENV)
        super().start()
        try:
            for schema in self._schemas:
                self._enable_schema(schema)
        except Exception:
            self.stop()
            raise
        return self

    def get_base_url(self) -> str:
        return f"http://{self.get_container_host_ip()}:{self.get_http_port()}"

    def get_http_port(self) -> int:
        return self.get_exposed_port(self.http_port)

    def get_https_port(self) -> int:
        return self.get_exposed_port(self.https_port)

    def get_mongodb_api_port(self) -> int:
        return self.get_exposed_port(self.mongodb_api_port)

    def _enable_schema(self, schema: SchemaConfiguration) -> None:
        script = "\n".join((_SQL_ERROR_EXIT, "EXECUTE ORDS.ENABLE_SCHEMA;", "EXIT;", ""))
        command = (
            f"printf %s {quote(script)} | "
            f"sql -s {quote(schema.connection_string)}"
        )
        result = self.exec(["bash", "-lc", command])
        _raise_for_failed_exec(result, "ORDS schema enablement failed")

    def _validate_required_env(self, env_name: str) -> None:
        if _is_blank(self.env.get(env_name)):
            raise ValueError(f"{env_name} must be configured before starting ORDS")


def _require_non_blank(value: str, message: str) -> str:
    if _is_blank(value):
        raise ValueError(message)
    return value


def _is_blank(value: str | None) -> bool:
    return value is None or value.strip() == ""


def _raise_for_failed_exec(result, message: str) -> None:
    if result.exit_code == 0:
        return
    raise RuntimeError(f"{message}.\noutput:\n{_decode_exec_output(result.output)}")


def _decode_exec_output(output) -> str:
    return output.decode(errors="replace") if isinstance(output, bytes) else str(output)
