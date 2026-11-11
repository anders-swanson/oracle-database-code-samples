package com.example.reference.book;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "publishers")
public class Publisher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    protected Publisher() {
    }

    Publisher(String name, String countryCode) {
        this.name = name;
        this.countryCode = countryCode;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCountryCode() { return countryCode; }
}
