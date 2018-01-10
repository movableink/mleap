package main

import (
	"bytes"
	"database/sql"
	"encoding/json"
	"fmt"
	"io"
	"io/ioutil"
	"net/http"
	"os"
	"time"

	"github.com/gocql/gocql"
	_ "github.com/lib/pq"
	common "github.com/movableink/go-go-common-go"
	"github.com/movableink/go-go-common-go/stats"
)

var validEvents = map[string]int{
	"pageview":          1,
	"cart_add":          1,
	"conversion":        1,
	"open|glance|visit": 1,
	"open|glance":       1,
	"open|visit":        1,
	"skim":              1,
	"read":              1,
	"click":             1,
	"click|u_click":     1,
}

type PG struct {
	*sql.DB
}

func NewPG(host string) (*PG, error) {
	db, err := sql.Open("postgres", host)
	if err != nil {
		return nil, err
	}

	return &PG{db}, nil
}

func (pg *PG) GetUsers(companyID string, limit int) ([]string, error) {
	var users []string

	query := `
		SELECT
			user_uuid
		FROM
			user_profile
		WHERE
			company_id = $1
		AND
			updated_at >= now()::date - interval '15 days'
		LIMIT
			$2;
	`

	rows, err := pg.Query(query, companyID, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	for rows.Next() {
		var user string

		err := rows.Scan(&user)
		if err != nil {
			return nil, err
		}

		users = append(users, user)
	}

	return users, rows.Err()
}

type Cassandra struct {
	session *gocql.Session
}

func NewCassandra(hosts ...string) (*Cassandra, error) {
	cluster := gocql.NewCluster(hosts...)
	cluster.Timeout = 5 * time.Second
	cluster.Keyspace = "user_events_production"
	cluster.Consistency = gocql.LocalQuorum
	cluster.NumConns = 24
	cluster.ProtoVersion = 4

	session, err := cluster.CreateSession()
	if err != nil {
		return nil, err
	}

	return &Cassandra{session: session}, nil
}

func (cassandra *Cassandra) GetUserEvents(key string, limit int) ([]*common.UserEvent, error) {
	var events []*common.UserEvent
	var message []byte

	query := `SELECT message FROM users WHERE user_uuid = ? ORDER BY time DESC LIMIT ?`

	results := cassandra.session.Query(query, key, limit)
	iterator := results.Iter()
	defer results.Release()

	for iterator.Scan(&message) {
		event := new(common.UserEvent)

		err := json.Unmarshal(message, event)
		if err != nil {
			return nil, err
		}

		_, ok := validEvents[event.EventType]
		if ok {
			events = append(events, event)
		}
	}

	return events, iterator.Close()
}

func (cassandra *Cassandra) Close() {
	cassandra.session.Close()
}

type Input struct {
	Schema *Schema          `json:"schema"`
	Rows   [][2]interface{} `json:"rows"`
}

func NewInput(rows []*Row) *Input {
	input := &Input{
		Schema: &Schema{
			Fields: []*Field{
				{Name: "user_uuid", Type: "string"},
				{Name: "events", Type: &ComplexType{Type: "list", Base: "string"}},
			},
		},
	}

	for _, row := range rows {
		input.Rows = append(input.Rows, row.Encode())
	}

	return input
}

type Schema struct {
	Fields []*Field `json:"fields"`
}

type Field struct {
	Name string      `json:"name"`
	Type interface{} `json:"type"`
}

type ComplexType struct {
	Type string `json:"type"`
	Base string `json:"base"`
}

type Row struct {
	UserID string
	Events []*Event
}

func (row *Row) Encode() [2]interface{} {
	events := make([]string, len(row.Events))
	for idx, event := range row.Events {
		events[idx] = event.String()
	}

	return [2]interface{}{
		row.UserID,
		events,
	}
}

func UserEventsToRow(user string, events []*common.UserEvent) *Row {
	row := &Row{UserID: user}

	for _, event := range events {
		row.Events = append(row.Events, &Event{
			Type: event.EventType,
			Time: event.Timestamp,
		})
	}

	return row
}

type Event struct {
	Type string
	Time string
}

func (event *Event) String() string {
	when, err := time.Parse("Mon Jan 02 2006 15:04:05 MST-0700 (MST)", event.Time)
	if err != nil {
		when = time.Now()
	}

	return fmt.Sprintf("%d;%s", when.Unix(), event.Type)
}

func main() {
	companyID := "4894"
	if len(os.Args) >= 2 {
		companyID = os.Args[1]
	}

	pg, err := NewPG("postgres://deploy@basket.misrv.net:5432/blotter_production?sslmode=disable")
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}

	stats.SetupStatsd("beagle.iad.mattbook.misrv.net", "trivial.misrv.net", 8125)

	cassandra, err := NewCassandra([]string{
		"10.1.1.42",
		"10.1.1.152",
		"10.1.2.87",
		"10.1.4.221",
		"10.1.2.167",
		"10.1.1.183",
		"10.1.5.61",
		"10.1.5.197",
		"10.1.2.79",
		"10.1.5.195",
		"10.1.4.178",
		"10.1.4.69",
	}...)
	if err != nil {
		pg.Close()

		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}

	inputs, err := getInputs(companyID, 1000, pg, cassandra)
	if err != nil {
		pg.Close()
		cassandra.Close()

		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}

	// Warm up the service
	for _, input := range inputs {
		time, err := testTransform(companyID, input)
		if err != nil {
			pg.Close()
			cassandra.Close()

			fmt.Println(os.Stderr, err)
			os.Exit(1)
		}
		fmt.Printf("user: %v_%v, events: %v, mleap time: %v\n", input.Rows[0][0], companyID, len(input.Rows[0][1].([]string)), time)
	}

	stats.Close()
	pg.Close()
	cassandra.Close()
}

func getInputs(companyID string, limit int, pg *PG, cassandra *Cassandra) ([]*Input, error) {
	var inputs []*Input

	users, err := pg.GetUsers(companyID, limit)
	if err != nil {
		return nil, err
	}

	fmt.Println("Retrieved", len(users), "users")
	for _, user := range users {
		events, err := cassandra.GetUserEvents(user+"_"+companyID, limit)
		if err != nil {
			return nil, err
		}

		fmt.Println("Retrieved", len(events), "events for user", user)

		input := NewInput([]*Row{UserEventsToRow(user, events)})
		inputs = append(inputs, input)
	}

	return inputs, nil
}

func testTransform(companyID string, input *Input) (time.Duration, error) {
	endpoint := "http://localhost:65327/transform_" + companyID

	var buf bytes.Buffer
	encoder := json.NewEncoder(&buf)
	err := encoder.Encode(input)
	if err != nil {
		return 0, err
	}

	req, err := http.NewRequest("POST", endpoint, &buf)
	if err != nil {
		return 0, err
	}

	req.Header.Set("Accept", "application/json")
	req.Header.Set("Content-Type", "application/json")

	start := time.Now()

	res, err := http.DefaultClient.Do(req)
	if err != nil {
		return 0, err
	}
	defer res.Body.Close()

	_, err = io.Copy(ioutil.Discard, res.Body)
	if err != nil {
		return 0, err
	}

	end := time.Since(start)
	duration := end.Nanoseconds()

	stats.ReportGauge("mleap.gauge", duration, 1.0)
	return end, nil
}
