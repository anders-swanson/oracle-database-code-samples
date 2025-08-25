package connection

import (
	"context"
	"database/sql"
	"fmt"
	"log"
	"os"
	"time"

	"github.com/godror/godror"
)

func DefaultLocalhostConnection() *sql.DB {
	return NewDatabase("testuser", "testpwd", "localhost:1521/freepdb1")
}

func NewDatabase(username, password, url string) *sql.DB {
	// Either set DYLD_LIBRARY_PATH to point to the Oracle client libraries,
	// or have the client libraries present on your system PATH.
	ldLibraryPath := os.Getenv("DYLD_LIBRARY_PATH")
	if ldLibraryPath == "" {
		log.Fatalf("DYLD_LIBRARY_PATH unset, cannot find Oracle client libraries")
	}

	var P godror.ConnectionParams
	// If password is not specified, externalAuth will be true, and we'll ignore user input
	isExternalAuth := password == ""
	externalAuth := sql.NullBool{
		Bool:  isExternalAuth,
		Valid: true,
	}
	if isExternalAuth {
		username = ""
	}

	// Setup connection parameters
	P.Username = username
	P.Password = godror.NewPassword(password)
	P.ConnectString = url
	P.ExternalAuth = externalAuth

	// Connection pooling parameters
	P.PoolParams.WaitTimeout = time.Second * 5

	// Recommended to use minimal pooling,
	// otherwise you will manage a single connection
	P.PoolParams.SessionIncrement = 1
	P.PoolParams.MinSessions = 1
	P.PoolParams.MaxSessions = 15
	// See P.PoolParams for additional pooling configuration

	// Database TNS Admin
	tnsAdmin := os.Getenv("TNS_ADMIN")
	if tnsAdmin != "" {
		P.ConfigDir = tnsAdmin
	}

	// Optionally set database role
	// P.AdminRole = dsn.SysDBA

	// Create new database using connection parameters
	db := sql.OpenDB(godror.NewConnector(P))
	db.SetMaxOpenConns(15)
	db.SetMaxIdleConns(15)
	db.SetConnMaxLifetime(0)

	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	if _, err := db.ExecContext(ctx, `
			begin
	       		dbms_application_info.set_client_info('oracledb_exporter');
			end;`); err != nil {
		fmt.Println("Could not set CLIENT_INFO.")
	}

	return db
}
