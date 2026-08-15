package uz.finbank.finbankauthservice.entity.base;

import org.junit.jupiter.api.Test;
import uz.finbank.finbankauthservice.entity.SessionEntity;
import uz.finbank.finbankauthservice.entity.UserEntity;

import static org.assertj.core.api.Assertions.assertThat;

class BaseEntityTest {

    @Test
    void equals_shouldReturnTrue_when_sameInstance() {
        UserEntity user = new UserEntity();

        assertThat(user).isEqualTo(user);
    }

    @Test
    void equals_shouldReturnFalse_when_bothInstancesAreTransientWithNoId() {
        UserEntity first = new UserEntity();
        UserEntity second = new UserEntity();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void equals_shouldReturnTrue_when_sameIdAndSameType() {
        UserEntity first = new UserEntity();
        first.setId("user-1");
        UserEntity second = new UserEntity();
        second.setId("user-1");

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test
    void equals_shouldReturnFalse_when_idsDiffer() {
        UserEntity first = new UserEntity();
        first.setId("user-1");
        UserEntity second = new UserEntity();
        second.setId("user-2");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void equals_shouldReturnFalse_when_sameIdButDifferentEntityType() {
        UserEntity user = new UserEntity();
        user.setId("shared-id");
        SessionEntity session = new SessionEntity();
        session.setId("shared-id");

        assertThat(user).isNotEqualTo(session);
    }

    @Test
    void hashCode_shouldStayStable_when_mutableFieldsChangeAfterIdIsAssigned() {
        UserEntity user = new UserEntity();
        user.setId("user-1");
        user.setEmail("first@test.local");

        int hashBefore = user.hashCode();
        user.setEmail("changed@test.local");
        int hashAfter = user.hashCode();

        assertThat(hashBefore).isEqualTo(hashAfter);
    }

    @Test
    void toString_shouldNotThrow_when_lazyUserAssociationIsUninitialized() {
        SessionEntity session = new SessionEntity();
        session.setId("session-1");

        assertThat(session.toString()).doesNotContain("password");
    }
}
