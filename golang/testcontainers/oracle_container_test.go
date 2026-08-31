package testcontainers

import (
	"context"
	"fmt"
	"github.com/stretchr/testify/assert"
	"testing"
	"time"
)

func TestOracleContainer(t *testing.T) {
	// Start an Oracle AI Database container
	ctx := context.Background()
	container, err := NewOracleContainer(ctx,
		"gvenzl/oracle-free:23.26.3-slim-faststart",
		"testuser",
		"testpwd",
	)
	assert.Nil(t, err)

	// Get a database connection for the container
	db, err := container.GetDB(ctx)
	assert.Nil(t, err)

	// Query the version from the database
	queryCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	var banner string
	err = db.QueryRowContext(queryCtx, "SELECT banner FROM v$version WHERE ROWNUM = 1").Scan(&banner)
	assert.Nil(t, err)
	assert.Equal(t, "Oracle Database 23ai Free Release 23.0.0.0.0 - Develop, Learn, and Run for Free", banner)
	fmt.Println(banner)
}
