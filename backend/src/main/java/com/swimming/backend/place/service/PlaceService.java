package com.swimming.backend.place.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.place.domain.City;
import com.swimming.backend.place.domain.Place;
import com.swimming.backend.place.repository.CityRepository;
import com.swimming.backend.place.repository.PlaceRepository;
import com.swimming.backend.place.repository.entity.CityEntity;
import com.swimming.backend.place.repository.entity.PlaceEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlaceService {

    private final CityRepository cityRepository;
    private final PlaceRepository placeRepository;

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<City> getCitiesAndPlaces() {
        return cityRepository.findAllByOrderByIdAsc()
                .stream()
                .map(CityEntity::toDomain)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<Place> getPlaces() {
        return placeRepository.findAllWithCityOrderByCityIdAscIdAsc()
                .stream()
                .map(PlaceEntity::toDomain)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Place getOne(Long placeId) {
        return placeRepository.findByIdWithCity(placeId)
                .map(PlaceEntity::toDomain)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
    }
}
