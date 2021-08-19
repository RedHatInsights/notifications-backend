package com.redhat.cloud.notifications.routers.models;

public class RenderEmailTemplateResponse {

    public static class Error {
        private final String message;

        public Error(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class Success {
        private final String subject;
        private final String body;

        public Success(String subject, String body) {
            this.subject = subject;
            this.body = body;
        }

        public String getSubject() {
            return subject;
        }

        public String getBody() {
            return body;
        }
    }

    private RenderEmailTemplateResponse() {

    }
}
