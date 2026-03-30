package com.nexhub.backend.model;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "project_tags")
@Data // Si usas Lombok
public class Tag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name; // Ej: "Spring Boot", "Tailwind", "Python"

    // Relación inversa (opcional, para saber qué proyectos tienen este tag)
    @ManyToMany(mappedBy = "tags")
    @JsonIgnore // Para evitar bucles infinitos en el JSON
    private Set<Project> projects = new HashSet<>();
}