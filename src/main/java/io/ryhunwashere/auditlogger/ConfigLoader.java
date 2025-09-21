package io.ryhunwashere.auditlogger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

public class ConfigLoader {
    private final String secret;
    private final String issuer;

    public ConfigLoader(String filePath) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode jsonNode = mapper.readTree(new File(filePath));
            secret = jsonNode.get("secret").asText();
            issuer = jsonNode.get("issuer").asText();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getSecret() {
        return secret;
    }

    public String getIssuer() {
        return issuer;
    }
}
