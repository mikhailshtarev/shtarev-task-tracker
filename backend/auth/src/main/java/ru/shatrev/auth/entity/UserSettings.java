package ru.shatrev.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Персональные настройки пользователя, 1:1 с users (миграции V2/V3).
 * Дефолты совпадают с разделом 6 бизнес-описания F-3; диапазоны
 * закреплены CHECK-ограничениями на уровне БД.
 */
@Entity
@Table(name = "user_settings")
public class UserSettings {

    @Id
    private UUID userId;

    @MapsId
    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Convert(converter = EstimationUnitConverter.class)
    @Column(name = "estimation_unit", nullable = false)
    private EstimationUnit estimationUnit = EstimationUnit.HOURS;

    @Column(name = "pomodoro_minutes", nullable = false)
    private int pomodoroMinutes = 25;

    @Column(name = "game_mode_enabled", nullable = false)
    private boolean gameModeEnabled = false;

    /** Стоимость часа лёгкой задачи для личного бюджета (z). */
    @Column(name = "budget_hour_cost", nullable = false)
    private int budgetHourCost = 5;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public EstimationUnit getEstimationUnit() {
        return estimationUnit;
    }

    public void setEstimationUnit(EstimationUnit estimationUnit) {
        this.estimationUnit = estimationUnit;
    }

    public int getPomodoroMinutes() {
        return pomodoroMinutes;
    }

    public void setPomodoroMinutes(int pomodoroMinutes) {
        this.pomodoroMinutes = pomodoroMinutes;
    }

    public boolean isGameModeEnabled() {
        return gameModeEnabled;
    }

    public void setGameModeEnabled(boolean gameModeEnabled) {
        this.gameModeEnabled = gameModeEnabled;
    }

    public int getBudgetHourCost() {
        return budgetHourCost;
    }

    public void setBudgetHourCost(int budgetHourCost) {
        this.budgetHourCost = budgetHourCost;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
