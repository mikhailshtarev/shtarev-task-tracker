package ru.shatrev.auth.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Единица оценки трудозатрат (раздел 3 бизнес-описания F-3). На wire — в нижнем регистре. */
public enum EstimationUnit {
    HOURS("hours"),
    POMODOROS("pomodoros");

    private final String value;

    EstimationUnit(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static EstimationUnit fromValue(String value) {
        return valueOf(value.toUpperCase());
    }
}
