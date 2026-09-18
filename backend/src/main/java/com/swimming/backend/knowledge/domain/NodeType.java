package com.swimming.backend.knowledge.domain;

public enum NodeType {

    /** 사용자가 저장한 문서. */
    SOURCE,

    /** 문서가 다루는 개념·대상. */
    SUBJECT,

    /** 문서가 설명·지원하는 적용 목적·작업·문제 해결 맥락. */
    TOPIC,

    /**
     * Folder의 Source를 나눈 묶음.
     *
     * <p>SUBJECT / TOPIC과 달리 Folder에 매인다. 소속 폴더는 {@code knowledge_category}에
     * 둔다.
     */
    CATEGORY
}
