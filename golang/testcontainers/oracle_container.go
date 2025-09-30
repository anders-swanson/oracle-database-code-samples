package testcontainers

import (
	"context"
	"database/sql"
	"fmt"
	"github.com/anders-swanson/oracle-database-java-samples/golang/connection"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/wait"
)

const (
	containerPort     = "1521/tcp"
	containerLogReady = "DATABASE IS READY TO USE!"

	appUserEnvVar              = "APP_USER"
	appUserPasswordEnvVar      = "APP_USER_PASSWORD"
	oracleRandomPasswordEnvVar = "ORACLE_RANDOM_PASSWORD"
)

type OracleContainer struct {
	username string
	password string
	*testcontainers.DockerContainer
}

func NewOracleContainer(ctx context.Context, image, appUser, appUserPassword string, opts ...testcontainers.ContainerCustomizer) (*OracleContainer, error) {
	// Configure Oracle Container with default options
	opts = append(opts, testcontainers.WithExposedPorts(containerPort),
		testcontainers.WithWaitStrategy(
			wait.ForListeningPort(containerPort),
			wait.ForLog(containerLogReady),
		),
		testcontainers.WithEnv(map[string]string{
			oracleRandomPasswordEnvVar: "y",
			appUserEnvVar:              appUser,
			appUserPasswordEnvVar:      appUserPassword,
		}),
	)

	// Start the container
	container, err := testcontainers.Run(ctx,
		image,
		opts...,
	)
	if err != nil {
		return nil, err
	}
	return &OracleContainer{
		username:        appUser,
		password:        appUserPassword,
		DockerContainer: container,
	}, nil
}

func (o *OracleContainer) GetDB(ctx context.Context) (*sql.DB, error) {
	// Either set DYLD_LIBRARY_PATH to point to the Oracle client libraries,
	// or have the client libraries present on your system PATH.
	endpoint, err := o.Endpoint(ctx, "")
	if err != nil {
		return nil, err
	}
	return connection.NewDatabase(
		o.username,
		o.password,
		fmt.Sprintf("%s/freepdb1", endpoint),
	), nil
}
