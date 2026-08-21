package com.swimming.backend.place.controller;

import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.place.domain.BackgroundAssetType;
import com.swimming.backend.place.dto.BackgroundAssetResponse;
import com.swimming.backend.place.dto.CityResponse;
import com.swimming.backend.place.dto.PlaceResponse;
import com.swimming.backend.place.service.PlaceService;
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

class CityControllerTest {

    private PlaceService placeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        placeService = mock(PlaceService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CityController(placeService))
                .setCustomArgumentResolvers(new AuthUserArgumentResolver(
                        new AuthUser(1L, "user@example.com")
                ))
                .build();
    }

    @Test
    @DisplayName("도시와 제공 공간 목록을 반환한다")
    void getsCities() throws Exception {
        when(placeService.getCities()).thenReturn(List.of(new CityResponse(
                1L,
                "Lisbon",
                "PT",
                List.of(new PlaceResponse(
                        11L,
                        "Alfama Cafe",
                        new BackgroundAssetResponse(
                                BackgroundAssetType.VIDEO,
                                "https://cdn.example.com/alfama.webm"
                        ),
                        "https://youtu.be/default"
                ))
        )));

        mockMvc.perform(get("/api/cities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Lisbon"))
                .andExpect(jsonPath("$[0].places[0].id").value(11))
                .andExpect(jsonPath("$[0].places[0].backgroundAsset.type").value("VIDEO"));

        verify(placeService).getCities();
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
