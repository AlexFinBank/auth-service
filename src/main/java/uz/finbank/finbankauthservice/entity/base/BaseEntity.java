package uz.finbank.finbankauthservice.entity.base;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Objects;
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    @CreatedBy
    @Column(name = "created_by",updatable = false)
    private String createdBy;
    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;
    @CreatedDate
    @Column(name = "created_at",updatable = false)
    private LocalDateTime createdAt;
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ID-based identity: mutable-field equals/hashCode (what Lombok @Data would generate)
    // breaks in Sets/Maps as soon as any field changes after insertion, and recurses into
    // lazy associations on subclasses. Two transient (id == null) instances are never equal,
    // even to themselves-by-value, since they have no persistent identity yet.
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BaseEntity other)) {
            return false;
        }
        return id != null && id.equals(other.id) && getClass().equals(other.getClass());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}
