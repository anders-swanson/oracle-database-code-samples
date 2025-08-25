package main

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"github.com/anders-swanson/oracle-database-java-samples/golang/connection"
	"github.com/godror/godror"
	"log"
	"time"
)

type Student struct {
	Metadata  *Metadata `json:"metadata,omitempty"`
	ID        *int      `json:"_id,omitempty"`
	FirstName string    `json:"firstName"`
	LastName  string    `json:"lastName"`
	Email     string    `json:"email"`
	Major     string    `json:"major"`
	Credits   int       `json:"credits"`
	Gpa       float32   `json:"gpa"`
}

type Metadata struct {
	ASOF []byte `json:"asof"`
	ETAG []byte `json:"etag"`
}

func main() {
	db := connection.DefaultLocalhostConnection()

	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	if err := studentDualityViewSimple(ctx, db); err != nil {
		log.Fatalf("student duality view example failed: %v", err)
	}
}

func studentDualityViewSimple(ctx context.Context, db *sql.DB) error {
	createStudentTableSQL := `
CREATE TABLE IF NOT EXISTS student (
    id         NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_name VARCHAR2(50) NOT NULL,
    last_name  VARCHAR2(50) NOT NULL,
    email      VARCHAR2(100),
    major      VARCHAR2(20),
    credits    NUMBER(10),
    gpa        NUMBER(3,2)
        CHECK (gpa BETWEEN 0.00 AND 5.00)
)`

	fmt.Println("Creating 'student' table.")
	if _, err := db.QueryContext(ctx, createStudentTableSQL); err != nil {
		return fmt.Errorf("failed to created student table: %v", err)
	}

	createStudentDVSQL := `
create or replace force editionable json relational duality view student_dv 
 as student @insert @update @delete {
    _id : id
    firstName: first_name 
    lastName : last_name
    email
    major
    credits
    gpa
 }`

	fmt.Println("Creating 'student_dv' duality view.")
	if _, err := db.QueryContext(ctx, createStudentDVSQL); err != nil {
		return fmt.Errorf("failed to create student duality view: %v", err)
	}

	s := &Student{
		FirstName: "Alice",
		LastName:  "Johnson",
		Email:     "alice.johnson@example.edu",
		Major:     "Computer Science",
		Credits:   120,
		Gpa:       3.88,
	}

	studentBytes, err := json.Marshal(s)
	if err != nil {
		return err
	}

	insertStudentDVSQL := `
INSERT INTO student_dv (data) values (:1)
`
	fmt.Println("Insert into 'student_dv' duality view")
	if _, err := db.QueryContext(ctx, insertStudentDVSQL, studentBytes); err != nil {
		return fmt.Errorf("failed to insert into student dv: %v", err)
	}

	selectStudentSQL := `
SELECT * FROM student_dv s
`

	fmt.Println("Query 'student_dv' duality view")
	result, err := db.QueryContext(ctx, selectStudentSQL)
	if err != nil {
		return err
	}
	if result.Next() {
		jsonResult := &godror.JSON{}
		if err := result.Scan(jsonResult); err != nil {
			return err
		}

		value, err := jsonResult.GetValue(godror.JSONOptDefault)
		if err != nil {
			return err
		}
		fmt.Println(value)
	}

	return nil
}
