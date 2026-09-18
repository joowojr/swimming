package com.swimming.backend.knowledge.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.folder.dto.FolderReference;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.knowledge.domain.*;
import com.swimming.backend.knowledge.dto.in.*;
import com.swimming.backend.knowledge.dto.out.CategorySuggestionInput;
import com.swimming.backend.knowledge.dto.out.CategorySuggestionResult;
import com.swimming.backend.knowledge.service.SourceGraphReader;
import com.swimming.backend.knowledge.service.data.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.data.KnowledgeRelationService;
import com.swimming.backend.knowledge.service.data.KnowledgeSourceService;
import com.swimming.backend.knowledge.service.graph.CategorySuggestionValidator;
import com.swimming.backend.knowledge.service.llm.SourceCategorySuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Folder에 모인 Source를 Category로 묶는다.
 *
 * <p>Category는 {@code knowledge_node}의 {@code CATEGORY} 노드이고, 소속은
 * {@code CATEGORY -CONTAINS-> SOURCE} 관계다. Subject·Topic처럼 전용 테이블이 없다.
 * 어느 Folder의 것인지는 담긴 Source의 {@code folder_id}에서 파생한다.
 *
 * <p>링크 하나를 소화할 때 Category를 정하지 않는다. 아직 축이 없을 때 문서 하나만 보고
 * 정한 묶음은 다음 문서가 들어오면 흔들린다. 폴더의 분석 가능한 Source가 6개 이상 모였을
 * 때 전체 맥락을 함께 보고 초안을 만든다.
 *
 * <p>확정은 PUT으로 전체 교체한다. 살아 있던 Category는 soft delete하고 요청 항목을
 * 전부 새로 만든다. {@code knowledge_relation}에는 soft delete가 없으므로 CONTAINS
 * 행은 남기고, 죽은 Category는 조회에서 빠진다.
 */
@Service
@RequiredArgsConstructor
public class KnowledgeCategoryUseCase {

    /** 이보다 적으면 나눌 것이 없어 문서 하나짜리 묶음만 나온다. */
    private static final int MIN_SOURCE_COUNT = 6;

    private final FolderService folderService;
    private final KnowledgeSourceService sourceService;
    private final KnowledgeNodeService nodeService;
    private final KnowledgeRelationService relationService;
    private final SourceGraphReader conceptReader;
    private final SourceCategorySuggestionService suggestionService;
    private final CategorySuggestionValidator suggestionValidator;

    /**
     * 사용자가 검토할 묶음 초안을 만든다. 노드도 관계도 저장하지 않는다.
     *
     * <p>트랜잭션을 걸지 않는다. 가운데에 수 초가 걸리는 LLM 호출이 있어, 여기서 묶으면
     * 그동안 커넥션이 잡혀 있다. 읽기만 하고 아무것도 쓰지 않으므로 묶을 이유도 없다.
     */
    public CategoryPreviewResponse preview(Long userId, Long folderId, List<UUID> sourceIds) {
        FolderReference folder = folderService.getReference(userId, folderId);
        List<KnowledgeSource> sources =
                sourceService.getAllCategorizationTargets(userId, folderId, sourceIds);

        // 요청 개수는 통과했어도 그중 소화가 끝나지 않았거나 다른 폴더의 것이 섞여 있으면
        // 여기서 줄어든다. 이미 읽어 온 행을 세는 것이라 조회가 늘지 않는다.
        if (sources.size() < MIN_SOURCE_COUNT) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_CATEGORY_SOURCE_COUNT_INSUFFICIENT);
        }

        Map<UUID, SourceConcepts> conceptsBySourceId = conceptReader.readAll(sources);
        CategorySuggestionInput input = inputOf(folder, sources, conceptsBySourceId);

        CategorySuggestionResult result = suggestionService.suggest(input);

        return suggestionValidator.validate(input, result);
    }

    /**
     * 폴더의 Category 구성을 요청 값으로 통째로 바꾼다.
     *
     * <p>기존 노드를 유지·rename하지 않는다. 살아 있던 Category는 soft delete하고 요청
     * 항목마다 새 노드를 만든다. Preview를 거쳤든 맨손으로 만들었든 같은 경로다.
     *
     * <p>빈 목록이면 Category를 모두 soft delete하고 끝난다. 개수 조건은 없다.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public CategoryReplaceResponse replace(
            Long userId,
            Long folderId,
            CategoryReplaceRequest request
    ) {
        folderService.lockOwned(userId, folderId);
        validateAssignment(request.categories());

        Set<UUID> requestedSourceIds = flattenSourceIds(request.categories());
        Map<UUID, KnowledgeSource> sourceById =
                indexOwnedSourcesInFolder(userId, folderId, requestedSourceIds);

        List<KnowledgeNode> existingCategories = aliveCategoriesInFolder(userId, folderId);
        if (!existingCategories.isEmpty()) {
            nodeService.deleteAll(
                    userId,
                    existingCategories.stream().map(KnowledgeNode::getId).toList()
            );
        }

        List<CategoryReplaceResponse.Category> results = new ArrayList<>();
        for (CategoryReplaceRequest.Category item : request.categories()) {
            KnowledgeNode category = nodeService.create(
                    userId,
                    NodeType.CATEGORY,
                    item.title().strip(),
                    null
            );
            List<KnowledgeNode> sourceNodes = item.sourceIds().stream()
                    .map(sourceById::get)
                    .map(KnowledgeSource::getNode)
                    .toList();
            relationService.connectAll(category, sourceNodes, RelationOrigin.USER);
            results.add(new CategoryReplaceResponse.Category(
                    category.getId(),
                    category.getTitle(),
                    List.copyOf(item.sourceIds())
            ));
        }

        return new CategoryReplaceResponse(results);
    }

    /**
     * 원문은 보내지 않는다. Folder 이름과 각 Source의 제목·요약·목적·개념이면 묶는 판단에
     * 충분하고, 본문은 이미 소화 단계에서 한 번 읽었다.
     */
    private CategorySuggestionInput inputOf(
            FolderReference folder,
            List<KnowledgeSource> sources,
            Map<UUID, SourceConcepts> conceptsBySourceId
    ) {
        List<CategorySuggestionInput.Item> items = new ArrayList<>();

        // 자리가 곧 모델에게 주는 번호다. 조회가 오래된 순으로 고정돼 있어 같은 입력이면
        // 같은 번호가 붙는다.
        for (KnowledgeSource source : sources) {
            SourceConcepts concepts = conceptsBySourceId.getOrDefault(
                    source.getId(), SourceConcepts.empty()
            );

            items.add(new CategorySuggestionInput.Item(
                    source.getId(),
                    source.getNode().getTitle(),
                    source.getSummary(),
                    concepts.topic() == null ? null : concepts.topic().title(),
                    concepts.subjects().stream().map(NodeRef::title).toList()
            ));
        }

        return new CategorySuggestionInput(folder.name(), items);
    }

    /**
     * 저장할 수 없는 구성을 DB 보기 전에 거절한다.
     *
     * <p>Preview validator는 어긴 묶음만 걷어내지만, 확정은 사용자가 보낸 구성 그대로
     * 저장해야 하므로 하나라도 어기면 전체를 거부한다.
     */
    private void validateAssignment(List<CategoryReplaceRequest.Category> categories) {
        Set<String> usedTitles = new HashSet<>();
        Set<UUID> usedSourceIds = new HashSet<>();

        for (CategoryReplaceRequest.Category category : categories) {
            String title = category.title() == null ? "" : category.title().strip();
            if (title.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_KNOWLEDGE_CATEGORY_ASSIGNMENT);
            }
            if (!usedTitles.add(NodeTitleNormalizer.normalize(title))) {
                throw new BusinessException(ErrorCode.INVALID_KNOWLEDGE_CATEGORY_ASSIGNMENT);
            }
            if (category.sourceIds() == null || category.sourceIds().isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_KNOWLEDGE_CATEGORY_ASSIGNMENT);
            }
            for (UUID sourceId : category.sourceIds()) {
                if (sourceId == null || !usedSourceIds.add(sourceId)) {
                    throw new BusinessException(ErrorCode.INVALID_KNOWLEDGE_CATEGORY_ASSIGNMENT);
                }
            }
        }
    }

    private Set<UUID> flattenSourceIds(List<CategoryReplaceRequest.Category> categories) {
        Set<UUID> sourceIds = new LinkedHashSet<>();
        for (CategoryReplaceRequest.Category category : categories) {
            sourceIds.addAll(category.sourceIds());
        }
        return sourceIds;
    }

    /**
     * 요청한 Source가 모두 이 Folder의 살아 있는 내 문서인지 확인한다.
     *
     * <p>조회 결과 수가 요청과 다르면 없거나·남의·다른 폴더·지운 Source가 섞인 것이다.
     * 부분 저장 없이 전체를 거부한다.
     */
    private Map<UUID, KnowledgeSource> indexOwnedSourcesInFolder(
            Long userId,
            Long folderId,
            Set<UUID> sourceIds
    ) {
        if (sourceIds.isEmpty()) {
            return Map.of();
        }

        List<KnowledgeSource> sources =
                sourceService.getOwnedAllInFolder(userId, folderId, sourceIds);
        if (sources.size() != sourceIds.size()) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND);
        }

        return sources.stream().collect(Collectors.toMap(
                KnowledgeSource::getId,
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new
        ));
    }

    /**
     * 이 Folder Source를 담은 살아 있는 Category.
     *
     * <p>폴더 소속은 Category에 없고 담긴 Source에서 파생한다. soft-deleted 노드는
     * {@link KnowledgeNodeService#findAllByIds}에서 빠진다.
     */
    private List<KnowledgeNode> aliveCategoriesInFolder(Long userId, Long folderId) {
        return nodeService.findCategoriesInFolder(userId, folderId);
    }
}
