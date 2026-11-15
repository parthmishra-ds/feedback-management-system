package edu.sau.feedback.repository;

import edu.sau.feedback.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("select n from Notification n where n.user.userId = :uid order by n.createdAt desc, n.notificationId desc")
    List<Notification> latest(@Param("uid") Long uid, Pageable page);

    @Query("select count(n) from Notification n where n.user.userId = :uid and n.seen = false")
    long unread(@Param("uid") Long uid);

    @Modifying
    @Query("update Notification n set n.seen = true where n.user.userId = :uid and n.seen = false")
    int markAllSeen(@Param("uid") Long uid);

    /** The user id is part of the condition, so nobody can mark someone else's alert. */
    @Modifying
    @Query("update Notification n set n.seen = true where n.notificationId = :id and n.user.userId = :uid")
    int markSeen(@Param("id") Long id, @Param("uid") Long uid);
}
