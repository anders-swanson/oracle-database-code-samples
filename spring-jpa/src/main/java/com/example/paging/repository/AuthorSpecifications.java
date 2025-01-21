package com.example.paging.repository;

import com.example.paging.model.Author;
import com.example.paging.model.Book;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

public final class AuthorSpecifications {
    public static Specification<Author> hasBookCountGreaterThanX(Long countThreshold) {
        return (root, query, criteriaBuilder) -> {
            Join<Author, Book> bookJoin = root.join("books", JoinType.LEFT);
            query.groupBy(root);
            query.having(criteriaBuilder.gt(criteriaBuilder.count(bookJoin), countThreshold));

            return query.getRestriction();
        };
    }
}
