package testcontainers

import (
	"context"
	"crypto/tls"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"testing"
	"time"

	"github.com/stretchr/testify/require"
	"github.com/testcontainers/testcontainers-go"
	tcexec "github.com/testcontainers/testcontainers-go/exec"
	tcnetwork "github.com/testcontainers/testcontainers-go/network"
	"github.com/testcontainers/testcontainers-go/wait"
	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

const (
	ordsDatabaseImage      = "gvenzl/oracle-free:23.26.1-slim-faststart"
	ordsDatabaseAlias      = "ordsdb"
	ordsAdminPassword      = "Welcome12345"
	ordsDatabaseConnection = "jdbc:oracle:thin:@ordsdb:1521/freepdb1"
	ordsSchemaConnection   = "ordsdb:1521/freepdb1"
	ordsInitSQL            = `
WHENEVER OSERROR EXIT FAILURE;
WHENEVER SQLERROR EXIT SQL.SQLCODE;
ALTER SESSION SET CONTAINER = freepdb1;
CREATE USER ordsuser IDENTIFIED BY "ordsuserpwd" QUOTA UNLIMITED ON users;
GRANT connect, pdb_dba TO ordsuser;
CREATE USER mongouser IDENTIFIED BY "mongouserpwd" QUOTA UNLIMITED ON users;
GRANT create session, create table, soda_app TO mongouser;
EXIT;
`

	dbAPIAdminUsername = "ordsuser"
	dbAPIAdminPassword = "ordsuserpwd"
	mongoUsername      = "mongouser"
	mongoPassword      = "mongouserpwd"
)

type databaseVersionResponse struct {
	InstanceName    string            `json:"instance_name"`
	InstanceVersion []instanceVersion `json:"instance_version"`
}

type instanceVersion struct {
	Banner string `json:"banner"`
}

func TestOrdsContainerIntegration(t *testing.T) {
	ctx := context.Background()
	nw, err := tcnetwork.New(ctx)
	require.NoError(t, err)
	t.Cleanup(func() {
		require.NoError(t, nw.Remove(ctx))
	})

	// Create Oracle AI Database container
	oracleContainer, err := testcontainers.Run(
		ctx,
		ordsDatabaseImage,
		testcontainers.WithExposedPorts(containerPort),
		testcontainers.WithWaitStrategy(
			wait.ForListeningPort(containerPort),
			wait.ForLog(containerLogReady).WithStartupTimeout(5*time.Minute),
		),
		testcontainers.WithEnv(map[string]string{
			"ORACLE_PASSWORD":     ordsAdminPassword,
			appUserEnvVar:         "testuser",
			appUserPasswordEnvVar: "testpwd",
		}),
		tcnetwork.WithNetwork([]string{ordsDatabaseAlias}, nw),
	)
	require.NoError(t, err)
	t.Cleanup(func() {
		require.NoError(t, oracleContainer.Terminate(ctx))
	})

	initializeOrdsDatabase(t, ctx, oracleContainer)

	// Create ORDS container
	ordsContainer, err := RunOrdsContainer(
		ctx,
		WithOrdsDatabaseConnectionString(ordsDatabaseConnection),
		WithOrdsOraclePassword(ordsAdminPassword),
		WithOrdsSchema(dbAPIAdminUsername, dbAPIAdminPassword, ordsSchemaConnection),
		WithOrdsSchema(mongoUsername, mongoPassword, ordsSchemaConnection),
		WithOrdsContainerCustomizers(tcnetwork.WithNetwork(nil, nw)),
	)
	require.NoError(t, err)
	t.Cleanup(func() {
		require.NoError(t, ordsContainer.Terminate(ctx))
	})

	t.Run("starts ORDS against Oracle AI Database", func(t *testing.T) {
		baseURL, err := ordsContainer.BaseURL(ctx)
		require.NoError(t, err)

		response, err := http.Get(baseURL)
		require.NoError(t, err)
		defer response.Body.Close()

		require.Less(t, response.StatusCode, http.StatusBadRequest)
		mongoAPIPort, err := ordsContainer.MongoDBAPIPort(ctx)
		require.NoError(t, err)
		require.NotEmpty(t, mongoAPIPort.Port())
	})

	t.Run("gets database version from ORDS API", func(t *testing.T) {
		baseURL, err := ordsContainer.BaseURL(ctx)
		require.NoError(t, err)
		req, err := http.NewRequestWithContext(
			ctx,
			http.MethodGet,
			fmt.Sprintf("%s/ords/%s/_/db-api/stable/database/version", baseURL, dbAPIAdminUsername),
			nil,
		)
		require.NoError(t, err)
		req.Header.Set("Authorization", basicAuth(dbAPIAdminUsername, dbAPIAdminPassword))

		response, err := http.DefaultClient.Do(req)
		require.NoError(t, err)
		defer response.Body.Close()

		require.Equal(t, http.StatusOK, response.StatusCode)
		require.Contains(t, response.Header.Get("Content-Type"), "application/json")

		var version databaseVersionResponse
		require.NoError(t, json.NewDecoder(response.Body).Decode(&version))
		require.NotEmpty(t, version.InstanceName)
		require.NotEmpty(t, version.InstanceVersion)
		require.NotEmpty(t, version.InstanceVersion[0].Banner)
	})

	t.Run("supports MongoDB client CRUD operations", func(t *testing.T) {
		mongoAPIPort, err := ordsContainer.MongoDBAPIPort(ctx)
		require.NoError(t, err)
		host, err := ordsContainer.Host(ctx)
		require.NoError(t, err)

		uri := fmt.Sprintf(
			"mongodb://%s:%s@%s:%s/%s?authMechanism=PLAIN&authSource=%%24external&tls=true&retryWrites=false&loadBalanced=true",
			mongoUsername,
			mongoPassword,
			host,
			mongoAPIPort.Port(),
			mongoUsername,
		)
		client, err := mongo.Connect(options.Client().
			ApplyURI(uri).
			SetTLSConfig(&tls.Config{InsecureSkipVerify: true})) // For test purposes only. Use a real TLS config in production.
		require.NoError(t, err)
		t.Cleanup(func() {
			require.NoError(t, client.Disconnect(ctx))
		})

		collection := client.Database(mongoUsername).Collection("compat_go")
		t.Cleanup(func() {
			require.NoError(t, collection.Drop(ctx))
		})

		documentID := "go-ords-document"
		_, err = collection.InsertOne(ctx, bson.M{
			"_id":     documentID,
			"name":    "Alice",
			"credits": int32(12),
			"active":  true,
		})
		require.NoError(t, err)

		var inserted bson.M
		require.NoError(t, collection.FindOne(ctx, bson.M{"_id": documentID}).Decode(&inserted))
		require.Equal(t, "Alice", inserted["name"])
		require.Equal(t, int32(12), inserted["credits"])
		require.Equal(t, true, inserted["active"])

		updateResult, err := collection.UpdateOne(ctx, bson.M{"_id": documentID}, bson.M{"$set": bson.M{"credits": int32(15)}})
		require.NoError(t, err)
		require.Equal(t, int64(1), updateResult.ModifiedCount)

		var updated bson.M
		require.NoError(t, collection.FindOne(ctx, bson.M{"_id": documentID}).Decode(&updated))
		require.Equal(t, int32(15), updated["credits"])

		deleteResult, err := collection.DeleteOne(ctx, bson.M{"_id": documentID})
		require.NoError(t, err)
		require.Equal(t, int64(1), deleteResult.DeletedCount)
		require.ErrorIs(t, collection.FindOne(ctx, bson.M{"_id": documentID}).Err(), mongo.ErrNoDocuments)
	})
}

func initializeOrdsDatabase(t *testing.T, ctx context.Context, container testcontainers.Container) {
	t.Helper()

	command := fmt.Sprintf(
		"printf %%s %s | sqlplus -s '/ as sysdba'",
		shellQuote(ordsInitSQL),
	)
	exitCode, outputReader, err := container.Exec(ctx, []string{"bash", "-lc", command}, tcexec.Multiplexed())
	require.NoError(t, err)
	output, readErr := io.ReadAll(outputReader)
	require.NoError(t, readErr)
	require.Equal(t, 0, exitCode, "Database initialization failed.\noutput:\n%s", string(output))
}

func basicAuth(username, password string) string {
	return "Basic " + base64.StdEncoding.EncodeToString([]byte(username+":"+password))
}
