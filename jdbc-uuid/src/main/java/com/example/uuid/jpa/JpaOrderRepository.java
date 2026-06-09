package com.example.uuid.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaOrderRepository extends JpaRepository<JpaOrder, UUID> {
    List<JpaOrder> findAllByOrderByOrderNumber();
}
