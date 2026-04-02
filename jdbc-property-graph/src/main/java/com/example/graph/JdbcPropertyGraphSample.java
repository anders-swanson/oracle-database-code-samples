package com.example.graph;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import javax.sql.DataSource;

import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

public class JdbcPropertyGraphSample {
    static final String PERSONS_TABLE = "PERSONS";
    static final String FRIENDSHIPS_TABLE = "FRIENDSHIPS";
    static final String GRAPH_NAME = "SOCIAL_GRAPH";

    private final DataSource dataSource;

    public JdbcPropertyGraphSample(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
    }

    /**
     * Recreates the relational tables and removes any graph definitions from previous runs.
     */
    public void resetSchema() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            dropPropertyGraphIfPresent(connection, statement, GRAPH_NAME);
            dropTableIfPresent(statement, FRIENDSHIPS_TABLE);
            dropTableIfPresent(statement, PERSONS_TABLE);

            statement.execute("""
                    create table %s (
                        person_id number primary key,
                        name varchar2(50) not null unique,
                        hometown varchar2(50) not null
                    )
                    """.formatted(PERSONS_TABLE));

            statement.execute("""
                    create table %s (
                        friendship_id number primary key,
                        person1_id number not null references %s(person_id),
                        person2_id number not null references %s(person_id),
                        since_year number(4) not null,
                        strength number(3) not null,
                        constraint friendship_not_self check (person1_id <> person2_id)
                    )
                    """.formatted(FRIENDSHIPS_TABLE, PERSONS_TABLE, PERSONS_TABLE));

            statement.execute("""
                    create index friendships_source_idx
                    on %s(person1_id, person2_id)
                    """.formatted(FRIENDSHIPS_TABLE));

            statement.execute("""
                    create index friendships_destination_idx
                    on %s(person2_id, person1_id)
                    """.formatted(FRIENDSHIPS_TABLE));
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to initialize the property graph schema", exception);
        }
    }

    /**
     * Loads a small directed social graph. Reciprocal rows model an undirected friendship.
     */
    public void loadSampleData() {
        insertPerson(1, "Alice", "Seattle");
        insertPerson(2, "Bob", "Seattle");
        insertPerson(3, "Cara", "Portland");
        insertPerson(4, "Diego", "San Francisco");
        insertPerson(5, "Emma", "Seattle");

        insertMutualFriendship(101, 102, 1, 2, 2021, 9);
        insertMutualFriendship(103, 104, 1, 3, 2022, 8);
        insertMutualFriendship(105, 106, 2, 4, 2023, 7);
        insertMutualFriendship(107, 108, 2, 5, 2020, 10);
        insertMutualFriendship(109, 110, 3, 5, 2024, 6);
    }

    /**
     * Creates a property graph so JDBC clients can query it with GRAPH_TABLE.
     */
    public void createPropertyGraph() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            dropPropertyGraphIfPresent(connection, statement, GRAPH_NAME);
            // PERSONS becomes the vertex table, and each FRIENDSHIPS row becomes
            // one directed Friend edge from person1_id to person2_id.
            statement.execute("""
                    create property graph %s
                    vertex tables (
                        %s
                            key (person_id)
                            label person
                            properties (person_id, name, hometown)
                    )
                    edge tables (
                        %s
                            key (friendship_id)
                            source key (person1_id) references %s(person_id)
                            destination key (person2_id) references %s(person_id)
                            label friend
                            properties (friendship_id, person1_id, person2_id, since_year, strength)
                    )
                    """.formatted(
                    GRAPH_NAME,
                    PERSONS_TABLE,
                    FRIENDSHIPS_TABLE,
                    PERSONS_TABLE,
                    PERSONS_TABLE
            ));
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to create the property graph", exception);
        }
    }

    /**
     * Returns the direct friends of one person using a SQL GRAPH_TABLE query.
     */
    public List<String> listDirectFriends(String personName) {
        // GRAPH_TABLE lets us query the named property graph with a graph pattern.
        // This pattern starts at one Person vertex, follows one outgoing Friend edge,
        // and returns the name property from the destination Person vertex.
        String sql = """
                select friend_name
                from graph_table (%s
                    match
                    (start_person is person where start_person.name = ?)
                        -[e is friend]->
                    (friend is person)
                    columns (friend.name as friend_name)
                )
                order by friend_name
                """.formatted(GRAPH_NAME);
        return querySingleStringColumn(sql, personName);
    }

    /**
     * Returns everyone reachable within one or two friendship hops using SQL graph pattern matching.
     */
    public List<String> listFriendsWithinTwoHops(String personName) {
        // The {1,2} quantifier means "follow this edge pattern one or two times".
        // In practice, that gives us both direct friends and friends-of-friends
        // in one graph pattern, and the outer SELECT DISTINCT removes duplicates.
        String sql = """
                select distinct candidate_name
                from graph_table (%s
                    match
                    (start_person is person where start_person.name = ?)
                        -[e is friend]->{1,2}
                    (candidate is person)
                    where start_person.person_id <> candidate.person_id
                    columns (candidate.name as candidate_name)
                )
                order by candidate_name
                """.formatted(GRAPH_NAME);
        return querySingleStringColumn(sql, personName);
    }

    /**
     * Finds friends-of-friends who are not already direct friends.
     */
    public List<String> listRecommendedFriends(String personName) {
        // The inner GRAPH_TABLE query finds two-hop paths:
        // person -> common_friend -> candidate.
        // That gives us friends-of-friends, but it still includes people who are already
        // direct friends, so the NOT IN subquery removes anyone reachable in one hop.
        // The outer GROUP BY then counts how many common friends led to each candidate,
        // which gives us a simple ranking for the recommendations.
        String sql = """
                select candidate_name
                from (
                    select candidate_name, count(*) as common_friend_count
                    from graph_table (%s
                        match
                        (person is person where person.name = ?)
                            -[first_hop is friend]->
                        (common_friend is person)
                            -[second_hop is friend]->
                        (candidate is person)
                        where person.person_id <> candidate.person_id
                        columns (candidate.name as candidate_name)
                    )
                    where candidate_name not in (
                        select friend_name
                        from graph_table (%s
                            match
                            (person is person where person.name = ?)
                                -[direct_edge is friend]->
                            (friend is person)
                            columns (friend.name as friend_name)
                        )
                    )
                    group by candidate_name
                )
                order by common_friend_count desc, candidate_name
                """.formatted(GRAPH_NAME, GRAPH_NAME);
        return querySingleStringColumn(sql, personName, personName);
    }

    private void insertPerson(int personId, String name, String hometown) {
        String sql = """
                insert into %s (person_id, name, hometown)
                values (?, ?, ?)
                """.formatted(PERSONS_TABLE);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, personId);
            statement.setString(2, name);
            statement.setString(3, hometown);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to insert person " + name, exception);
        }
    }

    /**
     * Stores the friendship in both directions because the sample models
     * a social connection as bidirectional while each graph edge is directed.
     */
    private void insertMutualFriendship(
            int forwardFriendshipId,
            int reverseFriendshipId,
            int firstPersonId,
            int secondPersonId,
            int sinceYear,
            int strength
    ) {
        insertFriendship(forwardFriendshipId, firstPersonId, secondPersonId, sinceYear, strength);
        insertFriendship(reverseFriendshipId, secondPersonId, firstPersonId, sinceYear, strength);
    }

    private void insertFriendship(int friendshipId, int person1Id, int person2Id, int sinceYear, int strength) {
        String sql = """
                insert into %s (friendship_id, person1_id, person2_id, since_year, strength)
                values (?, ?, ?, ?, ?)
                """.formatted(FRIENDSHIPS_TABLE);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, friendshipId);
            statement.setInt(2, person1Id);
            statement.setInt(3, person2Id);
            statement.setInt(4, sinceYear);
            statement.setInt(5, strength);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to insert friendship " + friendshipId, exception);
        }
    }

    /**
     * Executes one of the sample's name-returning graph queries.
     * This helper is intentionally narrow: it expects a single string column in position 1.
     */
    private List<String> querySingleStringColumn(String sql, String... parameters) {
        List<String> names = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setString(index + 1, parameters[index]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    names.add(resultSet.getString(1));
                }
            }
            return names;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to run the graph query", exception);
        }
    }

    private void dropPropertyGraphIfPresent(Connection connection, Statement statement, String graphName) throws SQLException {
        try (PreparedStatement lookup = connection.prepareStatement("""
                select count(*)
                from user_property_graphs
                where graph_name = ?
                """)) {
            lookup.setString(1, graphName.toUpperCase(Locale.US));
            try (ResultSet resultSet = lookup.executeQuery()) {
                resultSet.next();
                if (resultSet.getInt(1) > 0) {
                    statement.execute("drop property graph " + graphName);
                }
            }
        }
    }

    private void dropTableIfPresent(Statement statement, String tableName) throws SQLException {
        try {
            statement.execute("drop table " + tableName + " purge");
        } catch (SQLException exception) {
            if (exception.getErrorCode() != 942) {
                throw exception;
            }
        }
    }

    public static void main(String[] args) throws SQLException {
        if (args.length != 3) {
            throw new IllegalArgumentException("Expected arguments: <jdbc-url> <jdbc-user> <jdbc-password>");
        }

        PoolDataSource dataSource = PoolDataSourceFactory.getPoolDataSource();
        dataSource.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        dataSource.setConnectionPoolName("PROPERTY_GRAPH_SAMPLE_" + UUID.randomUUID().toString().replace("-", ""));
        dataSource.setURL(args[0]);
        dataSource.setUser(args[1]);
        dataSource.setPassword(args[2]);

        JdbcPropertyGraphSample sample = new JdbcPropertyGraphSample(dataSource);
        sample.resetSchema();
        sample.loadSampleData();
        sample.createPropertyGraph();

        List<String> directFriends = sample.listDirectFriends("Alice");
        List<String> twoHopFriends = sample.listFriendsWithinTwoHops("Alice");
        List<String> recommendations = sample.listRecommendedFriends("Alice");
        Path diagramOutput = defaultDiagramOutput();

        System.out.println("Direct friends of Alice: " + directFriends);
        System.out.println("Friends within two hops of Alice: " + twoHopFriends);
        System.out.println("Recommended friends for Alice: " + recommendations);
        try {
            PropertyGraphDiagramGenerator.writeSvg(sample, diagramOutput);
            System.out.println("Property graph diagram written to: " + diagramOutput.toAbsolutePath());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write the property graph diagram", exception);
        }
    }

    /**
     * Resolves the default diagram output to the jdbc-property-graph module root.
     */
    static Path defaultDiagramOutput() {
        try {
            Path classesDirectory = Path.of(JdbcPropertyGraphSample.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return classesDirectory.getParent().getParent().resolve("property-graph-diagram.svg").normalize();
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Unable to determine the module root for the diagram output", exception);
        }
    }

    /**
     * The methods below are only used by the SVG generator. They expose the
     * stored relational rows so the diagram can render a stable teaching view.
     */
    List<Person> listPeople() {
        List<Person> people = new ArrayList<>();
        String sql = """
                select person_id, name, hometown
                from %s
                order by person_id
                """.formatted(PERSONS_TABLE);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                people.add(new Person(
                        resultSet.getInt("person_id"),
                        resultSet.getString("name"),
                        resultSet.getString("hometown")
                ));
            }
            return people;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load people for the graph diagram", exception);
        }
    }

    List<Friendship> listFriendships() {
        List<Friendship> friendships = new ArrayList<>();
        String sql = """
                select friendship_id, person1_id, person2_id, since_year, strength
                from %s
                order by friendship_id
                """.formatted(FRIENDSHIPS_TABLE);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                friendships.add(new Friendship(
                        resultSet.getInt("friendship_id"),
                        resultSet.getInt("person1_id"),
                        resultSet.getInt("person2_id"),
                        resultSet.getInt("since_year"),
                        resultSet.getInt("strength")
                ));
            }
            return friendships;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load friendships for the graph diagram", exception);
        }
    }

    record Person(int personId, String name, String hometown) {
    }

    record Friendship(int friendshipId, int person1Id, int person2Id, int sinceYear, int strength) {
    }
}
