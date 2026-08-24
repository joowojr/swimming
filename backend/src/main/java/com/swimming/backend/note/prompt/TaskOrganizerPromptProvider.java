package com.swimming.backend.note.prompt;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class TaskOrganizerPromptProvider {

    private final String prompt;

    public TaskOrganizerPromptProvider() {
        try {
            var resource =
                    new ClassPathResource(
                            "prompts/task-organizer.md"
                    );

            this.prompt = resource
                    .getContentAsString(StandardCharsets.UTF_8);

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load task organizer prompt.",
                    e
            );
        }
    }

    public String get() {
        return prompt;
    }
}
