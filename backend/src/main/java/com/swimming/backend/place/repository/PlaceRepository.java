package com.swimming.backend.place.repository;

import com.swimming.backend.place.repository.entity.PlaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlaceRepository extends JpaRepository<PlaceEntity, Long> {

    @Query("""
            select place
            from PlaceEntity place
            join fetch place.city city
            order by city.id asc, place.id asc
            """)
    List<PlaceEntity> findAllWithCityOrderByCityIdAscIdAsc();

    @Query("""
            select place
            from PlaceEntity place
            join fetch place.city
            where place.id = :placeId
            """)
    Optional<PlaceEntity> findByIdWithCity(@Param("placeId") Long placeId);
}
