import oracledb
from testcontainers.core.container import DockerContainer
from testcontainers.core.wait_strategies import LogMessageWaitStrategy


APP_USER = "testuser"
APP_USER_PASSWORD = "welcome12345"

# OracleDatabaseContainer implements a Testcontainers DbContainer for the
# gvenzl/oracle-free image variants.
class OracleDatabaseContainer(DockerContainer):
    def __init__(self,
                 app_user=APP_USER,
                 app_user_password=APP_USER_PASSWORD,
                 image="gvenzl/oracle-free:23.26.0-slim-faststart",
                 db_name="freepdb1",
                 container_port=1521,
                 host="localhost",
                 **kwargs):
        super(OracleDatabaseContainer, self).__init__(image=image, **kwargs)
        self.container_port = container_port
        self.app_user = app_user
        self.app_user_password = app_user_password
        self.db_name=db_name
        self.host = host

        # Configure the container via environment variables
        self.with_env("ORACLE_RANDOM_PASSWORD", "y")
        self.with_env("APP_USER", app_user)
        self.with_env("APP_USER_PASSWORD", app_user_password)
        # Database listens on 1521 by default
        self.with_exposed_ports(container_port)
        # Wait for the database to be ready
        self.waiting_for(LogMessageWaitStrategy("DATABASE IS READY TO USE!"))

    def get_connection(self) -> oracledb.Connection:
        bind_port = self.get_exposed_port(self.container_port)
        return oracledb.connect(user=self.app_user, password=self.app_user_password,
                                host=self.host, port=bind_port, service_name=self.db_name)

    def get_connection_url(self) -> str:
        bind_port = self.get_exposed_port(self.container_port)
        return f'oracle+oracledb://{self.app_user}:{self.app_user_password}@{self.host}:{bind_port}/?service_name={self.db_name}'

    def _configure(self):
        pass