package com.swimming.backend.knowledge.service.graph;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.knowledge.domain.NodeTitleNormalizer;
import com.swimming.backend.knowledge.dto.in.CategoryPreviewResponse;
import com.swimming.backend.knowledge.dto.out.CategorySuggestionInput;
import com.swimming.backend.knowledge.dto.out.CategorySuggestionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 모델이 돌려준 묶음을 사용자에게 보여 줘도 되는 구성으로 만든다.
 *
 * <p>어긴 부분만 걷어내고 나머지는 살린다. 묶음 하나가 잘못됐다고 전체를 버리면 제대로
 * 나온 나머지까지 사라지고, 사용자는 다시 실행하는 것 말고 할 수 있는 일이 없다.
 * 걷어낸 뒤 남는 것이 없으면 빈 초안이 된다 — 그것도 유효한 답이다.
 *
 * <p>여기서 거르는 것은 모두 <b>확정했을 때 저장할 수 없는</b> 것들이다. 무엇을 한 묶음으로
 * 볼지의 판단은 프롬프트가 소유하고, 여기서 다시 따지지 않는다.
 */
@Slf4j
@Component
public class CategorySuggestionValidator {

    public CategoryPreviewResponse validate(
            CategorySuggestionInput input,
            CategorySuggestionResult result
    ) {
        // 구조화 출력이 스키마를 지켰다면 여기에 null이 올 수 없다. 오면 호출 자체가
        // 실패한 것이므로 빈 초안으로 덮지 않고 실패로 알린다.
        if (result == null || result.categories() == null) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_CATEGORY_PREVIEW_FAILURE);
        }

        List<CategoryPreviewResponse.Category> categories = new ArrayList<>();
        Set<String> usedTitles = new HashSet<>();
        Set<UUID> usedSourceIds = new HashSet<>();

        for (CategorySuggestionResult.Category category : result.categories()) {
            CategoryPreviewResponse.Category validated =
                    validateOne(category, input, usedTitles, usedSourceIds);

            if (validated != null) {
                categories.add(validated);
            }
        }

        return new CategoryPreviewResponse(categories);
    }

    private CategoryPreviewResponse.Category validateOne(
            CategorySuggestionResult.Category category,
            CategorySuggestionInput input,
            Set<String> usedTitles,
            Set<UUID> usedSourceIds
    ) {
        if (category == null || category.title() == null || category.title().isBlank()) {
            log.warn("[category-suggestion] dropped a category without a name");
            return null;
        }

        String title = category.title().strip();

        // 이름이 겹치면 확정할 때 어느 쪽이 무엇인지 알 수 없다. 먼저 온 것을 남긴다.
        if (!usedTitles.add(NodeTitleNormalizer.normalize(title))) {
            log.warn("[category-suggestion] dropped a duplicate category name");
            return null;
        }

        List<UUID> sourceIds = sourceIdsOf(category.documentIndexes(), input, usedSourceIds);

        // 남은 것이 없으면 묶음이 아니다. 빈 묶음은 담긴 Source에서 폴더를 파생할 수 없어
        // 확정해도 어느 폴더에서도 조회되지 않는다.
        if (sourceIds.isEmpty()) {
            log.warn("[category-suggestion] dropped an empty category");
            usedTitles.remove(NodeTitleNormalizer.normalize(title));
            return null;
        }

        return new CategoryPreviewResponse.Category(title, sourceIds);
    }

    private List<UUID> sourceIdsOf(
            List<Integer> documentIndexes,
            CategorySuggestionInput input,
            Set<UUID> usedSourceIds
    ) {
        if (documentIndexes == null) {
            return List.of();
        }

        List<UUID> sourceIds = new ArrayList<>();

        for (Integer index : documentIndexes) {
            UUID sourceId = input.sourceIdAt(index);

            // 입력에 없던 번호를 지어냈다. 그런 문서는 존재하지 않으므로 뺀다.
            if (sourceId == null) {
                log.warn("[category-suggestion] dropped an out-of-range document index");
                continue;
            }

            // 한 문서가 두 묶음에 들면 "이 폴더가 무엇들로 이루어져 있는가"에 답할 수 없다.
            if (!usedSourceIds.add(sourceId)) {
                log.warn("[category-suggestion] dropped a source assigned twice");
                continue;
            }

            sourceIds.add(sourceId);
        }

        return sourceIds;
    }
}
