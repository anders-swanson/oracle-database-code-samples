 package com.example.clientinfo;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

 @RestController
@RequestMapping("/books")
public class BooksController {

    private final DataSource dataSource;

    public BooksController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private Connection getConnectionWithClientInfo(String action) throws SQLException {
        Connection conn = dataSource.getConnection();
        conn.setClientInfo("OCSID.MODULE", "Books");
        conn.setClientInfo("OCSID.ACTION", action);
        return conn;
    }

    record Book(Long id, String title, String author, String isbn, Date publishedDate) {}

    @GetMapping
    public List<Book> getAllBooks() {
        List<Book> books = new ArrayList<>();
        try (Connection conn = getConnectionWithClientInfo("getAllBooks");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM books")) {
            while (rs.next()) {
                books.add(new Book(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("author"),
                        rs.getString("isbn"),
                        rs.getDate("published_date")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return books;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Book> getBookById(@PathVariable Long id) {
        try (Connection conn = getConnectionWithClientInfo("getBookById");
             PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM books WHERE id = ?")) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Book book = new Book(
                            rs.getLong("id"),
                            rs.getString("title"),
                            rs.getString("author"),
                            rs.getString("isbn"),
                            rs.getDate("published_date")
                    );
                    return ResponseEntity.ok(book);
                } else {
                    return ResponseEntity.notFound().build();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @PostMapping
    public ResponseEntity<Book> createBook(@RequestBody Book book) {
        try (Connection conn = getConnectionWithClientInfo("createBook");
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO books (title, author, isbn, published_date) VALUES (?, ?, ?, ?) RETURNING id",
                     new String[]{"id"})) {
            pstmt.setString(1, book.title());
            pstmt.setString(2, book.author());
            pstmt.setString(3, book.isbn());
            pstmt.setDate(4, book.publishedDate());
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    Long generatedId = rs.getLong(1);
                    Book createdBook = new Book(generatedId, book.title(), book.author(), book.isbn(), book.publishedDate());
                    return ResponseEntity.status(HttpStatus.CREATED).body(createdBook);
                } else {
                    throw new SQLException("Creating book failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Book> updateBook(@PathVariable Long id, @RequestBody Book book) {
        try (Connection conn = getConnectionWithClientInfo("updateBook");
             PreparedStatement pstmt = conn.prepareStatement(
                     "UPDATE books SET title = ?, author = ?, isbn = ?, published_date = ? WHERE id = ?")) {
            pstmt.setString(1, book.title());
            pstmt.setString(2, book.author());
            pstmt.setString(3, book.isbn());
            pstmt.setDate(4, book.publishedDate());
            pstmt.setLong(5, id);
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected == 0) {
                return ResponseEntity.notFound().build();
            }
            Book updatedBook = new Book(id, book.title(), book.author(), book.isbn(), book.publishedDate());
            return ResponseEntity.ok(updatedBook);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(@PathVariable Long id) {
        try (Connection conn = getConnectionWithClientInfo("deleteBook");
             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM books WHERE id = ?")) {
            pstmt.setLong(1, id);
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected == 0) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.noContent().build();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
