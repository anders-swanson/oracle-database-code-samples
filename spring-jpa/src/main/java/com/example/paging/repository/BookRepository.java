package com.example.paging.repository;

import java.util.List;
import java.util.Optional;

import com.example.paging.model.Author;
import com.example.paging.model.Book;
import com.example.paging.model.BookGenre;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<Book, Long> {
    Optional<Book> findFirstByOrderByTitleAsc();


    // The following methods aren't used in the PagingSortingFiltering test,
    // but are provided as an example of JPA Query Methods.

    List<Book> findByGenre(BookGenre bookGenre, Pageable pageable);

    Page<Book> findByTitleContaining(String title, Pageable pageable);

    Page<Book> findByAuthor_AuthorId(Long author_authorId, Pageable pageable);

    Page<Book> findByAuthorAndGenre(Author author, BookGenre genre, Pageable pageable);

}
