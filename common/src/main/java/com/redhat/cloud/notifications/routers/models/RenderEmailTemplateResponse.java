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
        private final String[] result;

        public Success(String[] result) {
            this.result = result;
        }

        public String[] getResult() {
            return result;
        }
    }

    private RenderEmailTemplateResponse() {

    }
}
