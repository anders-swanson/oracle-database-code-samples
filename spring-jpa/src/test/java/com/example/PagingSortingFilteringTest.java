package com.example;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.example.paging.model.Author;
import com.example.paging.model.Book;
import com.example.paging.repository.AuthorRepository;
import com.example.paging.repository.BookRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import static com.example.paging.repository.AuthorSpecifications.hasBookCountGreaterThanX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
public class PagingSortingFilteringTest {
    @Container
    @ServiceConnection
    static OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.8-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(2))
            .withUsername("testuser")
            .withPassword("testpwd")
            .withInitScript("paging.sql");

    @Autowired
    private AuthorRepository authorRepository;
    @Autowired
    private BookRepository bookRepository;

    @Test
    void pagingExample() {
        // Request the first page of the dataset, with two records per page.
        // As the query page is iterated through, hibernate issues one query per page until
        // the record set has been fully traversed.
        Pageable prFirstPage = PageRequest.of(0, 2);
        Page<Book> books = bookRepository.findAll(prFirstPage);
        // 16 books in the sample dataset, should be 8 pages
        assertThat(books.getTotalPages()).isEqualTo(books.getTotalElements() / 2);
        // Should be on the first page (0-indexed)
        assertThat(books.getNumber()).isEqualTo(0);

        // Request the second page, using the same page size.
        Pageable prSecondPage = PageRequest.of(1, 2);
        books = bookRepository.findAll(prSecondPage);
        // Should be on the second page
        assertThat(books.getNumber()).isEqualTo(1);

        // What happens if we access non-existent page?
        Pageable prOutOfBounds = PageRequest.of(9, 5);
        books = bookRepository.findAll(prOutOfBounds);
        // Empty page is returned
        assertThat(books.getNumberOfElements()).isEqualTo(0);
    }

    @Test
    // For Lazy Loading on joined Author when sorting by author name
    @Transactional
    void sortingExample() {
        // Sort by book title in ascending order
        PageRequest prSortedByTitle = PageRequest.of(
                0,
                10,
                Sort.by(Sort.Order.asc("title"))
        );
        Page<Book> sortedByTitle = bookRepository.findAll(prSortedByTitle);

        // Verify sorted according to title
        assertThat(sortedByTitle.getContent()).isSortedAccordingTo(
                Comparator.comparing(Book::getTitle)
        );

        // Sort by multiple properties
        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by(
                Sort.Order.asc("author.name"),
                Sort.Order.desc("publishedYear")
        ));
        Page<Book> books = bookRepository.findAll(pageRequest);

        // Verify sorted by author name and descending published year
        assertThat(books.getContent()).isSortedAccordingTo(
                Comparator.comparing((Book b) -> b.getAuthor().getName())
                        .thenComparing(Book::getPublishedYear, Comparator.reverseOrder())
        );
    }

    @Test
    void methodQueryExample() {
        // Use a method name query generator to get the first book sorted by title
        Optional<Book> book = bookRepository.findFirstByOrderByTitleAsc();

        assertTrue(book.isPresent());
        // Equal Rites is the first book in alphabetical order from the sample dataset
        assertThat(book.get().getTitle()).isEqualTo("Equal Rites");
    }

    @Test
    void jpaSQLQueryExample() {
        List<Author> authors = authorRepository.findAuthorsWithMoreThanXBooksJPASyntax(4);
        assertThat(authors).hasSize(2);

        authors = authorRepository.findAuthorsWithMoreThanXBooksSQLSyntax(4);
        assertThat(authors).hasSize(2);
    }

    @Test
    @Transactional
    void specificationQueryExample() {
        // Specification query for authors with 4 or more books
        List<Author> authors = authorRepository.findAll(hasBookCountGreaterThanX(4L));
        assertThat(authors).hasSize(2);

        // We may take an existing specification query and modify it with additional criteria,
        // combining paging, sorting, and filtering.
        // The authors with 2 or more books are fetched with the following conditions:
        // - at most 5 authors per page
        // - authors are sorted by birth year
        // - authors are filtered by name beginning with 'T'
        Pageable pageable = PageRequest.of(0, 5, Sort.by("birthYear"));
        Page<Author> authorsSortedByBirthYearNameStartingWithT = authorRepository.findAll(
                hasBookCountGreaterThanX(2L).and((root, query, cb) ->
                        cb.like(root.get("name"), "T%")),
                pageable
        );

        // Verify the predicates from the specification query
        assertThat(authorsSortedByBirthYearNameStartingWithT.getContent()).allMatch(author ->
                author.getBooks().size() > 2 && author.getName().startsWith("T"));
        assertThat(authorsSortedByBirthYearNameStartingWithT.getContent())
                .isSortedAccordingTo(Comparator.comparing(Author::getName));
    }

    @Test
    @Transactional
    void paginationLiteralQueryExample() {
        // The more complex specification query in 'specificationQueryExample' may also be expressed by
        // a literal JPA @Query, using pagination. While not as flexible programmatically, it achieves
        // the same effect.
        Pageable pageable = PageRequest.of(0, 5);
        Page<Author> authors = authorRepository.moreThanXBooksNameStartsWithTSortedByBirthYear(2, pageable);

        // Verify the predicates from the JPA @Query
        assertThat(authors.getContent()).allMatch(author ->
                author.getBooks().size() > 2 && author.getName().startsWith("T"));
        assertThat(authors.getContent())
                .isSortedAccordingTo(Comparator.comparing(Author::getName));
    }
}
