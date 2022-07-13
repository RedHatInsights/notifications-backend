package com.redhat.cloud.notifications.openbridge;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * {
 *   "kind": "string",
 *   "id": "string",
 *   "name": "string",
 *   "href": "string",
 *   "submitted_at": "2022-04-12T09:36:51.410Z",
 *   "published_at": "2022-04-12T09:36:51.410Z",
 *   "status": "ACCEPTED",
 *   "filters": [
 *     {
 *       "type": "string",
 *       "key": "string",
 *       "value": "string"
 *     }
 *   ],
 *   "transformationTemplate": "string",
 *   "action": {
 *     "type": "string",
 *     "parameters": {
 *       "additionalProp1": "string"
 *     }
 *   }
 * }
 */
public class Processor {

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public String kind;
    public String name;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public String id;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public String status;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public String href;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public String submitted_at;

    public List<Filter> filters;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String transformationTemplate;
    public Action action;

    @SuppressWarnings("unused")
    Processor() { }

    public Processor(String name) {
        this.name = name;
        filters = new ArrayList<>();
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public String getSubmitted_at() {
        return submitted_at;
    }

    public void setSubmitted_at(String submitted_at) {
        this.submitted_at = submitted_at;
    }

    public List<Filter> getFilters() {
        return filters;
    }

    public void setFilters(List<Filter> filters) {
        this.filters = filters;
    }

    public String getTransformationTemplate() {
        return transformationTemplate;
    }

    public void setTransformationTemplate(String transformationTemplate) {
        this.transformationTemplate = transformationTemplate;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public static class Filter {
        public String type;
        public String key;
        public String value;

        public Filter() {
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Filter{");
            sb.append("'").append(key).append('\'');
            sb.append(" '").append(type).append('\'');
            sb.append(" '").append(value).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }

    public static class Action {
        public String type;
        public Map<String, String> parameters;

        public Action() {
        }

        public Action(String actionType) {
            this.type = actionType;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Map<String, String> getParameters() {
            if (parameters == null) {
                parameters = new HashMap<>();
            }
            return parameters;
        }

        public void setParameters(Map<String, String> parameters) {
            this.parameters = parameters;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Action{");
            sb.append("type='").append(type).append('\'');
            sb.append(", parameters=").append(parameters);
            sb.append('}');
            return sb.toString();
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Processor{");
        sb.append("name='").append(name).append('\'');
        sb.append(", id='").append(id).append('\'');
        sb.append(", status='").append(status).append('\'');
        sb.append(", filters=").append(filters);
        sb.append(", transformationTemplate='").append(transformationTemplate).append('\'');
        sb.append(", action=").append(action);
        sb.append('}');
        return sb.toString();
    }
}
