package com.example.pispringai.session;

import com.fasterxml.jackson.databind.JsonNode;

record StoredEvent(String type, JsonNode json) {
}
