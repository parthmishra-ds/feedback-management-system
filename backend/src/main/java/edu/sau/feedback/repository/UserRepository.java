package edu.sau.feedback.repository;

import edu.sau.feedback.entity.Role;
import edu.sau.feedback.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByInstitutionalId(String institutionalId);
    boolean existsByInstitutionalId(String institutionalId);
    boolean existsByRole(Role role);
    List<User> findByRole(Role role);

    /** Updates only the two contact columns (the database user may not touch any other user column). */
    @Modifying
    @Query("update User u set u.email = :email, u.phone = :phone where u.userId = :id")
    int updateContact(@Param("id") Long id, @Param("email") String email, @Param("phone") String phone);
}
