package com.swimming.backend.knowledge.service.data;

import com.swimming.backend.knowledge.domain.KnowledgeBranch;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.repository.KnowledgeBranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KnowledgeBranchService {

    private final KnowledgeBranchRepository branchRepository;

    /**
     * 이미 같은 자리에 있으면 그대로 둔다. 같은 문서를 여러 Folder에 둘 수 있으므로
     * 중복 Source라도 이 Folder와의 연결은 새로 만들어야 할 수 있다.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public KnowledgeBranch attach(KnowledgeNode parent, KnowledgeNode child) {
        return branchRepository.find(parent.getId(), child.getId())
                .orElseGet(() -> branchRepository.save(
                        KnowledgeBranch.create(parent, child, nextPosition(parent.getId()))
                ));
    }

    private int nextPosition(UUID parentNodeId) {
        return branchRepository.findAllByParentNodeId(parentNodeId).size();
    }
}
