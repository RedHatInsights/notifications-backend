package com.redhat.cloud.notifications.oapi;

import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.Components;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Paths;
import org.eclipse.microprofile.openapi.models.media.Schema;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Modify the path in the openapi document to not
 * have the prefix, which is already in the
 * servers part of the document. See POL-358
 * This also allows to do more filtering and
 * rewriting of the document.
 *
 * Also provides an operationId for all operations.
 */
public class OASModifier implements OASFilter {

    @Override
    public void filterOpenAPI(OpenAPI openAPI) {
        Paths paths = openAPI.getPaths();
        Set<String> keySet = paths.getPathItems().keySet();
        Map<String, PathItem> replacementItems = new HashMap<>();
        for (String key : keySet) {
            PathItem p = paths.getPathItem(key);
            String mangledName = mangleName(key);
            replacementItems.put(mangledName, p);

            Map<PathItem.HttpMethod, Operation> operations = p.getOperations();
            for (Map.Entry<PathItem.HttpMethod, Operation> entry : operations.entrySet()) {
                Operation op = entry.getValue();

                if (op.getOperationId() == null || op.getOperationId().isEmpty()) {

                    String id = toCamelCase(mangledName);
                    id = entry.getKey().toString().toLowerCase() + id;
                    op.setOperationId(id);
                }
            }
        }
        paths.setPathItems(replacementItems);

        // Settings values should not be shown in openapi export
        Components components = openAPI.getComponents();
        Map<String, Schema> existingSchemas = components.getSchemas();
        Map<String, Schema> modifiedSchemas = new HashMap<>(existingSchemas);
        // TODO Add modifications here
        components.setSchemas(modifiedSchemas);
    }

    private String toCamelCase(String mangledName) {
        StringBuilder sb = new StringBuilder();
        boolean needsUpper = false;
        for (char c : mangledName.toCharArray()) {
            if (c == '/') {
                needsUpper = true;
                continue;
            }
            if (c == '}') {
                continue;
            }
            if (c == '{') {
                sb.append("By");
                needsUpper = true;
                continue;
            }
            if (needsUpper) {
                sb.append(Character.toUpperCase(c));
                needsUpper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String mangleName(String key) {
        // Base Filler - would report as empty otherwise
        if (key.equals("/api/notifications/v1.0")) {
            return "/";
        }
        return key.replace("/api/notifications/v1.0", "");
    }
}
