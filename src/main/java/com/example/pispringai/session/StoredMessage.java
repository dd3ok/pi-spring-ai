package com.example.pispringai.session;

import com.fasterxml.jackson.databind.JsonNode;

record StoredMessage(String kind, JsonNode json) {
}
