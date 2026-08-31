package com.swimming.backend.place.repository.entity;

import com.swimming.backend.common.entity.BaseTimeEntity;
import com.swimming.backend.place.domain.City;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CityEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "country_code", nullable = false)
    private String countryCode;

    @Column(nullable = false)
    private String timezone;

    @Builder
    private CityEntity(String name, String countryCode, String timezone) {
        this.name = name;
        this.countryCode = countryCode;
        this.timezone = timezone;
    }

    public static CityEntity create(String name, String countryCode, String timezone) {
        return CityEntity.builder()
                .name(name)
                .countryCode(countryCode)
                .timezone(timezone)
                .build();
    }

    public City toDomain() {
        return City.restore(id, name, countryCode, timezone);
    }
}
