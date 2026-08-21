package com.swimming.backend.place.repository;

import com.swimming.backend.place.repository.entity.CityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CityRepository extends JpaRepository<CityEntity, Long> {

    List<CityEntity> findAllByOrderByIdAsc();
}
