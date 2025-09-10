import oracledb
from testcontainers.core.generic import DbContainer

# OracleDatabaseContainer implements a Testcontainers DbContainer for the
# gvenzl/oracle-free image variants.
class OracleDatabaseContainer(DbContainer):
    def __init__(self,
                 app_user,
                 app_user_password,
                 image="gvenzl/oracle-free:23.9-slim-faststart",
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

        self.with_env("ORACLE_RANDOM_PASSWORD", "y")
        self.with_env("APP_USER", app_user)
        self.with_env("APP_USER_PASSWORD", app_user_password)
        self.with_exposed_ports(container_port)

    def get_connection(self):
        bind_port = self.get_exposed_port(self.container_port)
        return oracledb.connect(user=self.app_user, password=self.app_user_password,
                                host=self.host, port=bind_port, service_name=self.db_name)

    def get_connection_url(self):
        bind_port = self.get_exposed_port(self.container_port)
        return f'oracle+oracledb://{self.app_user}:{self.app_user_password}@{self.host}:{bind_port}/?service_name={self.db_name}'

    def _configure(self):
        pass