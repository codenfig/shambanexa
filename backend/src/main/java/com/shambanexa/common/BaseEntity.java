package com.shambanexa.common;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    private UUID id;

    @PrePersist
    void ensureId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
