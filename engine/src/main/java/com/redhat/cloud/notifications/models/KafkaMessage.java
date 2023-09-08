package com.redhat.cloud.notifications.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "kafka_message")
public class KafkaMessage extends CreationTimestamped {

    @Id
    private UUID id;

    public KafkaMessage() {
    }

    public KafkaMessage(UUID id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof KafkaMessage) {
            KafkaMessage other = (KafkaMessage) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
