package com.example.uuid.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "JPA_UUID_ORDERS")
public class JpaOrder {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "ID", columnDefinition = "RAW(16)", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "ORDER_NUMBER", nullable = false, unique = true, length = 40)
    private String orderNumber;

    @Column(name = "CUSTOMER_NAME", nullable = false, length = 100)
    private String customerName;

    @Column(name = "TOTAL_AMOUNT", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    protected JpaOrder() {
    }

    public JpaOrder(UUID id, String orderNumber, String customerName, BigDecimal totalAmount) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.customerName = customerName;
        this.totalAmount = totalAmount;
    }

    public UUID getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
}
