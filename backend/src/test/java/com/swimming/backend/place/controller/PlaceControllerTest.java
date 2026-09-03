package com.swimming.backend.place.controller;

import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.place.domain.BackgroundAssetType;
import com.swimming.backend.place.dto.BackgroundAssetResponse;
import com.swimming.backend.place.dto.CityResponse;
import com.swimming.backend.place.dto.PlaceResponse;
import com.swimming.backend.place.usecase.PlaceUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlaceControllerTest {

    private PlaceUseCase placeUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        placeUseCase = mock(PlaceUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PlaceController(placeUseCase))
                .setCustomArgumentResolvers(new AuthUserArgumentResolver(
                        new AuthUser(1L, "user@example.com")
                ))
                .build();
    }

    @Test
    @DisplayName("도시와 제공 공간 목록을 반환한다")
    void getsCities() throws Exception {
        when(placeUseCase.getPlaces()).thenReturn(List.of(new CityResponse(
                1L,
                "Lisbon",
                "PT",
                List.of(new PlaceResponse(
                        11L,
                        "Alfama Cafe",
                        new BackgroundAssetResponse(
                                BackgroundAssetType.VIDEO,
                                "places/lisbon/alfama.mp4",
                                "https://cdn.example.com/places/lisbon/alfama.mp4"
                        ),
                        "https://youtu.be/default"
                ))
        )));

        mockMvc.perform(get("/api/public/places"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Lisbon"))
                .andExpect(jsonPath("$[0].places[0].id").value(11))
                .andExpect(jsonPath("$[0].places[0].name").value("Alfama Cafe"))
                .andExpect(jsonPath("$[0].places[0].defaultMusicUrl")
                        .value("https://youtu.be/default"))
                .andExpect(jsonPath("$[0].places[0].backgroundAsset.type").value("VIDEO"))
                .andExpect(jsonPath("$[0].places[0].backgroundAsset.url")
                        .value("https://cdn.example.com/places/lisbon/alfama.mp4"));

        verify(placeUseCase).getPlaces();
    }

    private record AuthUserArgumentResolver(AuthUser authUser)
            implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType() == AuthUser.class
                    && parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory
        ) {
            return authUser;
        }
    }
}
