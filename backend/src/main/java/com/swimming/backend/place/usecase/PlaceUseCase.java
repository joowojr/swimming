package com.swimming.backend.place.usecase;

import com.swimming.backend.place.domain.Place;
import com.swimming.backend.place.dto.CityResponse;
import com.swimming.backend.place.dto.PlaceResponse;
import com.swimming.backend.place.service.PlaceService;
import com.swimming.backend.place.service.PlaceVideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlaceUseCase {

    private final PlaceService placeService;
    private final PlaceVideoService placeVideoService;

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<CityResponse> getPlaces() {
        Map<Long, List<Place>> placesByCityId = placeService.getPlaces()
                .stream()
                .collect(Collectors.groupingBy(Place::getCityId));

        return placeService.getCitiesAndPlaces()
                .stream()
                .map(city -> CityResponse.from(
                        city,
                        placesByCityId.getOrDefault(city.getId(), List.of())
                                .stream()
                                .map(place -> PlaceResponse.from(
                                        place,
                                        placeVideoService.resolveBackgroundUrl(
                                                place.getBackgroundAssetKey()
                                        )
                                ))
                                .toList()
                ))
                .toList();
    }
}
