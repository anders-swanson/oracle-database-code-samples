from src.python_oracle.testcontainers_sample.oracle_database_container import OracleDatabaseContainer


def testcontainers_example():
    app_user = "testuser"
    app_password = "testpwd12345"

    # Start the container and try to query the database version
    with OracleDatabaseContainer(
            app_user=app_user,
            app_user_password=app_password
    ) as oracledb:
        cursor = oracledb.get_connection().cursor()
        for row in cursor.execute("select * from V$VERSION"):
            if row is None:
                print("No result from query!")
            print(row)

if __name__ == "__main__":
    testcontainers_example()