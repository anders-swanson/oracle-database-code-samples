package com.example.uuid.jpa;

import com.example.uuid.JdbcUuidSample;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.HexFormat;
import java.util.List;

@SpringBootApplication
public class JpaUuidApplication {
    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    public static void main(String[] args) {
        try (ConfigurableApplicationContext context = SpringApplication.run(JpaUuidApplication.class, args)) {
            printOrders(context.getBean(JpaUuidSample.class).resetAndLoadSampleData());
        }
    }

    private static void printOrders(List<JpaOrder> orders) {
        System.out.println("Stored JPA UUID primary keys as RAW(16):");
        for (JpaOrder order : orders) {
            System.out.printf(
                    "%s | bytes=%s | order=%s | customer=%s | total=%s%n",
                    order.getId(),
                    HEX.formatHex(JdbcUuidSample.uuidToBytes(order.getId())),
                    order.getOrderNumber(),
                    order.getCustomerName(),
                    order.getTotalAmount().setScale(2)
            );
        }
    }
}
