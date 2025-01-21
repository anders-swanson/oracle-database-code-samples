package com.example.paging.repository;

import com.example.paging.model.BookGenre;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookGenreRepository extends JpaRepository<BookGenre, Long> {
}
