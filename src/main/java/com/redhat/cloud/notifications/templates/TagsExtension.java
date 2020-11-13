package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.ingress.Tag;
import io.quarkus.qute.TemplateExtension;

import java.util.List;
import java.util.stream.Collectors;

@TemplateExtension
public class TagsExtension {

    static String getFirst(List<Tag> tags, String name) {
        return tags.stream().filter(t -> t.getName().equals(name)).findFirst().get().getValue();
    }

    static List<String> get(List<Tag> tags, String name) {
        return tags.stream().filter(t -> t.getName().equals(name)).map(Tag::getValue).collect(Collectors.toList());
    }
}
