package ru.shatrev.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/** Неудачная попытка входа / запрос для rate limiting; user_id может быть NULL (неизвестный email). */
@Entity
@Table(name = "login_attempts")
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address", nullable = false)
    private String ipAddress;

    /** login, register, forgot-password, resend-confirmation. */
    @Column(nullable = false)
    private String endpoint;

    @Column(name = "failed_at", nullable = false)
    private LocalDateTime failedAt;

    @PrePersist
    void onCreate() {
        if (failedAt == null) {
            failedAt = LocalDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public LocalDateTime getFailedAt() {
        return failedAt;
    }
}
