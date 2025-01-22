package com.example.paging.repository;

import java.util.List;

import com.example.paging.model.Author;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthorRepository extends JpaRepository<Author, Long>, JpaSpecificationExecutor<Author> {

    @Query("select a from Author a where size(a.books) > :bookCount")
    List<Author> findAuthorsWithMoreThanXBooksJPASyntax(@Param("bookCount") int bookCount);

    @Query("""
        select distinct a from Author a
        join Book b on b.author.authorId = a.authorId
        group by a
        having count(b) > :bookCount
    """)
    List<Author> findAuthorsWithMoreThanXBooksSQLSyntax(@Param("bookCount") int bookCount);


    @Query("""
        select distinct a from Author a
        join Book b on b.author.authorId = a.authorId
        where a.name like 'T%'
        group by a
        having count(b) > :bookCount
        order by a.birthYear
    """)
    Page<Author> moreThanXBooksNameStartsWithTSortedByBirthYear(
            @Param("bookCount") int bookCount,
            Pageable pageable);
}
