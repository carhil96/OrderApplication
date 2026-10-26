package com.example.app.dto;

public record CustomerSnapshot(Long id, String name, long version, boolean deleted) {}
