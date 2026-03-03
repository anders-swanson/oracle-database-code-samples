package com.example.json.jdbc;

import jakarta.json.bind.annotation.JsonbProperty;

import java.util.List;
import java.util.Objects;

public class ProductAttributes {
    private String name;
    private String category;
    private Double price;
    @JsonbProperty("tags")
    private List<String> tags;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    @Override
    public String toString() {
        return "ProductAttributes{" +
                "name='" + name + '\'' +
                ", category='" + category + '\'' +
                ", price=" + price +
                ", tags=" + tags +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ProductAttributes that)) {
            return false;
        }
        return Objects.equals(getName(), that.getName())
                && Objects.equals(getCategory(), that.getCategory())
                && Objects.equals(getPrice(), that.getPrice())
                && Objects.equals(getTags(), that.getTags());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), getCategory(), getPrice(), getTags());
    }
}
