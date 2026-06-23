package com.example.web;

public record OracleErrorResponse(
        String code,
        String message,
        String detail
) {
}
