package testcontainers

import (
	"context"
	"errors"
	"fmt"
	"io"
	"strings"
	"time"

	tcexec "github.com/testcontainers/testcontainers-go/exec"

	"github.com/docker/go-connections/nat"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/wait"
)

const (
	DefaultOrdsImage = "container-registry.oracle.com/database/ords:latest"

	ordsHTTPPort       = "8080/tcp"
	ordsHTTPSPort      = "8443/tcp"
	ordsMongoDBAPIPort = "27017/tcp"
	ordsStartupTimeout = 5 * time.Minute

	ordsConnectionStringEnvVar = "CONN_STRING"
	ordsOraclePasswordEnvVar   = "ORACLE_PWD"
	ordsJavaOptionsEnvVar      = "_JAVA_OPTIONS"
	defaultOrdsJavaOptions     = "-Xms128m -Xmx768m"

	ordsEnableSchemaSQL = `WHENEVER SQLERROR EXIT SQL.SQLCODE
EXECUTE ORDS.ENABLE_SCHEMA;
EXIT;
`
)

type OrdsSchemaConfiguration struct {
	Username          string
	Password          string
	ConnectDescriptor string
}

type OrdsContainer struct {
	schemas []OrdsSchemaConfiguration
	*testcontainers.DockerContainer
}

type ordsContainerConfig struct {
	image            string
	connectionString string
	oraclePassword   string
	schemas          []OrdsSchemaConfiguration
	customizers      []testcontainers.ContainerCustomizer
}

type OrdsContainerOption func(*ordsContainerConfig) error

func NewOrdsContainer(
	ctx context.Context,
	image string,
	connectionString string,
	oraclePassword string,
	opts ...testcontainers.ContainerCustomizer,
) (*OrdsContainer, error) {
	return RunOrdsContainer(
		ctx,
		WithOrdsImage(image),
		WithOrdsDatabaseConnectionString(connectionString),
		WithOrdsOraclePassword(oraclePassword),
		WithOrdsContainerCustomizers(opts...),
	)
}

func RunOrdsContainer(ctx context.Context, opts ...OrdsContainerOption) (*OrdsContainer, error) {
	config := &ordsContainerConfig{image: DefaultOrdsImage}
	for _, opt := range opts {
		if err := opt(config); err != nil {
			return nil, err
		}
	}

	if isBlank(config.connectionString) {
		return nil, fmt.Errorf("%s must be configured before starting ORDS", ordsConnectionStringEnvVar)
	}
	if isBlank(config.oraclePassword) {
		return nil, fmt.Errorf("%s must be configured before starting ORDS", ordsOraclePasswordEnvVar)
	}

	customizers := defaultOrdsContainerCustomizers(config)
	customizers = append(customizers, config.customizers...)

	container, err := testcontainers.Run(ctx, config.image, customizers...)
	if err != nil {
		return nil, err
	}

	ordsContainer := &OrdsContainer{
		schemas:         config.schemas,
		DockerContainer: container,
	}

	if err := ordsContainer.enableSchemas(ctx); err != nil {
		_ = container.Terminate(ctx)
		return nil, err
	}

	return ordsContainer, nil
}

func defaultOrdsContainerCustomizers(config *ordsContainerConfig) []testcontainers.ContainerCustomizer {
	return []testcontainers.ContainerCustomizer{
		testcontainers.WithExposedPorts(ordsHTTPPort, ordsHTTPSPort, ordsMongoDBAPIPort),
		testcontainers.WithWaitStrategyAndDeadline(
			ordsStartupTimeout,
			wait.ForHTTP("/").
				WithPort(nat.Port(ordsHTTPPort)).
				WithStatusCodeMatcher(func(status int) bool {
					return status >= 200 && status < 500
				}).
				WithStartupTimeout(ordsStartupTimeout),
		),
		testcontainers.WithEnv(map[string]string{
			ordsConnectionStringEnvVar: config.connectionString,
			ordsOraclePasswordEnvVar:   config.oraclePassword,
			ordsJavaOptionsEnvVar:      defaultOrdsJavaOptions,
		}),
	}
}

func WithOrdsImage(image string) OrdsContainerOption {
	return func(config *ordsContainerConfig) error {
		image, err := requireNonBlank(image, "ORDS image cannot be empty")
		if err != nil {
			return err
		}
		config.image = image
		return nil
	}
}

func WithOrdsDatabaseConnectionString(connectionString string) OrdsContainerOption {
	return func(config *ordsContainerConfig) error {
		connectionString, err := requireNonBlank(connectionString, "Oracle AI Database connection string cannot be empty")
		if err != nil {
			return err
		}
		config.connectionString = connectionString
		return nil
	}
}

func WithOrdsOraclePassword(oraclePassword string) OrdsContainerOption {
	return func(config *ordsContainerConfig) error {
		oraclePassword, err := requireNonBlank(oraclePassword, "Oracle AI Database password cannot be empty")
		if err != nil {
			return err
		}
		config.oraclePassword = oraclePassword
		return nil
	}
}

func WithOrdsSchema(username, password, connectDescriptor string) OrdsContainerOption {
	return func(config *ordsContainerConfig) error {
		username, err := requireNonBlank(username, "Schema username is required")
		if err != nil {
			return err
		}
		password, err = requireNonBlank(password, "Schema password is required")
		if err != nil {
			return err
		}
		connectDescriptor, err = requireNonBlank(connectDescriptor, "Schema connect descriptor is required")
		if err != nil {
			return err
		}

		config.schemas = append(config.schemas, OrdsSchemaConfiguration{
			Username:          username,
			Password:          password,
			ConnectDescriptor: connectDescriptor,
		})
		return nil
	}
}

func WithOrdsContainerCustomizers(customizers ...testcontainers.ContainerCustomizer) OrdsContainerOption {
	return func(config *ordsContainerConfig) error {
		config.customizers = append(config.customizers, customizers...)
		return nil
	}
}

func (o *OrdsContainer) BaseURL(ctx context.Context) (string, error) {
	host, err := o.Host(ctx)
	if err != nil {
		return "", err
	}
	port, err := o.HTTPPort(ctx)
	if err != nil {
		return "", err
	}
	return fmt.Sprintf("http://%s:%s", host, port.Port()), nil
}

func (o *OrdsContainer) HTTPPort(ctx context.Context) (nat.Port, error) {
	return o.MappedPort(ctx, nat.Port(ordsHTTPPort))
}

func (o *OrdsContainer) HTTPSPort(ctx context.Context) (nat.Port, error) {
	return o.MappedPort(ctx, nat.Port(ordsHTTPSPort))
}

func (o *OrdsContainer) MongoDBAPIPort(ctx context.Context) (nat.Port, error) {
	return o.MappedPort(ctx, nat.Port(ordsMongoDBAPIPort))
}

func (o *OrdsContainer) enableSchemas(ctx context.Context) error {
	for _, schema := range o.schemas {
		if err := o.enableSchema(ctx, schema); err != nil {
			return err
		}
	}
	return nil
}

func (o *OrdsContainer) enableSchema(ctx context.Context, schema OrdsSchemaConfiguration) error {
	command := enableSchemaCommand(schema)
	exitCode, outputReader, err := o.Exec(ctx, []string{"bash", "-lc", command}, tcexec.Multiplexed())
	if err != nil {
		return fmt.Errorf("failed to run ORDS schema enablement command: %w", err)
	}

	output, readErr := io.ReadAll(outputReader)
	if readErr != nil {
		return fmt.Errorf("failed to read ORDS schema enablement output: %w", readErr)
	}
	if exitCode == 0 {
		return nil
	}

	return fmt.Errorf("ORDS schema enablement failed with exit code %d\noutput:\n%s", exitCode, string(output))
}

func enableSchemaCommand(schema OrdsSchemaConfiguration) string {
	return fmt.Sprintf(
		"printf %%s %s | sql -s %s",
		shellQuote(ordsEnableSchemaSQL),
		shellQuote(schemaConnectionString(schema)),
	)
}

func schemaConnectionString(schema OrdsSchemaConfiguration) string {
	return fmt.Sprintf(
		"%s/%s@%s",
		schema.Username,
		sqlclQuotedPassword(schema.Password),
		schema.ConnectDescriptor,
	)
}

func requireNonBlank(value, message string) (string, error) {
	if isBlank(value) {
		return "", errors.New(message)
	}
	return value, nil
}

func isBlank(value string) bool {
	return strings.TrimSpace(value) == ""
}

func shellQuote(value string) string {
	return "'" + strings.ReplaceAll(value, "'", "'\"'\"'") + "'"
}

func sqlclQuotedPassword(password string) string {
	return `"` + strings.ReplaceAll(password, `"`, `""`) + `"`
}
