package com.example.uuid.jpa;

import com.example.uuid.JdbcUuidSample;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class JpaUuidSample {
    private final JpaOrderRepository orderRepository;

    public JpaUuidSample(JpaOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public List<JpaOrder> resetAndLoadSampleData() {
        orderRepository.deleteAllInBatch();
        orderRepository.save(new JpaOrder(
                JdbcUuidSample.ORDER_ONE_ID,
                "ORD-JPA-1001",
                "Avery Stone",
                new BigDecimal("42.50")
        ));
        orderRepository.save(new JpaOrder(
                JdbcUuidSample.ORDER_TWO_ID,
                "ORD-JPA-1002",
                "Mina Rao",
                new BigDecimal("125.00")
        ));
        orderRepository.flush();
        return orderRepository.findAllByOrderByOrderNumber();
    }
}
