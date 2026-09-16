package ru.shatrev.auth.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Хранит значение единицы оценки в формате БД (нижний регистр). */
@Converter
public class EstimationUnitConverter implements AttributeConverter<EstimationUnit, String> {

    @Override
    public String convertToDatabaseColumn(EstimationUnit attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public EstimationUnit convertToEntityAttribute(String value) {
        return value == null ? null : EstimationUnit.fromValue(value);
    }
}
