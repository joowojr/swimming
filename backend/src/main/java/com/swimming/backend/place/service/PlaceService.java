package com.swimming.backend.place.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.place.domain.City;
import com.swimming.backend.place.domain.Place;
import com.swimming.backend.place.dto.CityResponse;
import com.swimming.backend.place.dto.PlaceReference;
import com.swimming.backend.place.dto.PlaceResponse;
import com.swimming.backend.place.repository.CityRepository;
import com.swimming.backend.place.repository.PlaceRepository;
import com.swimming.backend.place.repository.entity.CityEntity;
import com.swimming.backend.place.repository.entity.PlaceEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlaceService {

    private final CityRepository cityRepository;
    private final PlaceRepository placeRepository;

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<CityResponse> getCities() {
        Map<Long, List<Place>> placesByCityId = placeRepository
                .findAllByOrderByCityIdAscIdAsc()
                .stream()
                .map(PlaceEntity::toDomain)
                .collect(Collectors.groupingBy(Place::getCityId));

        return cityRepository.findAllByOrderByIdAsc()
                .stream()
                .map(CityEntity::toDomain)
                .map(city -> CityResponse.from(
                        city,
                        placesByCityId.getOrDefault(city.getId(), List.of())
                                .stream()
                                .map(PlaceResponse::from)
                                .toList()
                ))
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public PlaceReference getReference(Long placeId) {
        Place place = placeRepository.findById(placeId)
                .map(PlaceEntity::toDomain)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
        City city = cityRepository.findById(place.getCityId())
                .map(CityEntity::toDomain)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
        return PlaceReference.from(place, city);
    }
}
