package com.example.reference.book;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateBookRequest(
        @NotBlank @Pattern(regexp = "[0-9]{13}") String isbn,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 10) List<@NotNull @Positive Long> authorIds,
        @Positive Long publisherId) {
}
