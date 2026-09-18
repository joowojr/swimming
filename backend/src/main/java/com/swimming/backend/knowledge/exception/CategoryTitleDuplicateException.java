package com.swimming.backend.knowledge.exception;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.knowledge.dto.in.NodeRef;
import lombok.Getter;

/** 중복 이름으로 이동할 수 있는 기존 카테고리를 전달한다. */
@Getter
public class CategoryTitleDuplicateException extends BusinessException {
    private final NodeRef targetCategory;

    public CategoryTitleDuplicateException(NodeRef targetCategory) {
        super(ErrorCode.KNOWLEDGE_CATEGORY_TITLE_DUPLICATE);
        this.targetCategory = targetCategory;
    }
}
