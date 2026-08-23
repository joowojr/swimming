package com.swimming.backend.place.repository;

import com.swimming.backend.place.repository.entity.PlaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlaceRepository extends JpaRepository<PlaceEntity, Long> {

    List<PlaceEntity> findAllByOrderByCityIdAscIdAsc();
}
