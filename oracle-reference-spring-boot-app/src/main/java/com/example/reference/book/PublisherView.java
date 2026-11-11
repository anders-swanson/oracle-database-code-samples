package com.example.reference.book;

public record PublisherView(Long id, String name, String countryCode) {
    static PublisherView from(Publisher publisher) {
        return new PublisherView(publisher.getId(), publisher.getName(), publisher.getCountryCode());
    }
}
