package com.aaron.issue.configurator.issues;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.quarkiverse.githubapp.event.Issue;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHEventPayload;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

public class IssueConfiguration {

    void commentOnOpen(@Issue.Opened @Issue.Edited GHEventPayload.Issue issuePayload) throws IOException {
        updateIssueComment("Validating configuration request.", issuePayload);
        var issueBody = issuePayload.getIssue().getBody();
        System.out.println("issueBody = " + issueBody);
        System.out.println("------------------");
        var issueProperties = new HashMap<String, Map<String, Map<String, String>>>();
        var objectMapper = getObjectMapper();
        issueProperties = objectMapper.readValue(issueBody, new TypeReference<>(){});
        System.out.println("Issue Mapped from IssueBody: "+issueProperties);

        issueProperties.entrySet().stream()
                .forEach(entry -> checkFile(entry.getKey(), entry.getValue(), issuePayload));

        updateIssueComment("Parameter configuration has been updated.", issuePayload);
        issuePayload.getIssue().close();
    }

    void checkFile(String parameterKey,  Map<String, Map<String, String>> parameterValue, GHEventPayload.Issue issuePayload) {
        System.out.println("Repository: "+issuePayload.getRepository().getName());
        try {
            var file = issuePayload.getRepository()
                    .getDirectoryContent("./").stream()
                    .filter(ghContent -> ghContent.getName().endsWith(".yml"))
                    .filter(ghContent -> parameterKey.contains(ghContent.getName().split("\\.")[0]))
                    .peek(ghContent -> System.out.println("%s isFile:%s".formatted(ghContent.getName(), ghContent.isFile())))
                    .findFirst().orElseThrow();
            var fileProperties = getPropertiesFromFile(file, issuePayload);

            System.out.println("File Properties Before: "+fileProperties);

            //Add new properties into FileMap
            parameterValue.keySet().stream()
                    .filter(Predicate.not(fileProperties::containsKey))
                    .forEach(key -> fileProperties.put(key, new TreeMap<>()));

            parameterValue.entrySet().stream()
                    .forEach(entry -> {
                        var fileValues = fileProperties.get(entry.getKey());
                        fileValues.putAll(entry.getValue());
                    });

            System.out.println("File Properties After: "+fileProperties);

            var outputString = getObjectMapper().writeValueAsString(fileProperties);
            System.out.println("Output String: "+outputString);

            file.update(outputString, issuePayload.getIssue().getTitle());
        } catch (IOException e) {
            updateIssueComment("There was an issue with parsing the configuration provided. Unable to merge requested changes. Please validate formatting.", issuePayload);
            e.printStackTrace();
        }
    }

    TreeMap<String, TreeMap<String, String>> getPropertiesFromFile(GHContent file, GHEventPayload.Issue issuePayload) {
        var mapper = getObjectMapper();
        var properties = new TreeMap<String, TreeMap<String, String>>();
        try(InputStream inputStream = file.read()) {
            System.out.println("File Looking at: " +file.getName());
            properties = mapper.readValue(inputStream, new TypeReference<>() {});
            System.out.println("Properties from File: "+properties);
        } catch (NullPointerException | IOException e) {
            updateIssueComment("There was an issue reading requested file. Unable to merge requested changes. Please validate formatting.", issuePayload);
            e.printStackTrace();
        }
        return properties;
    }

    private void updateIssueComment(String comment, GHEventPayload.Issue issuePayload) {
        try {
            issuePayload.getIssue().comment(comment);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private ObjectMapper getObjectMapper() {
        return new ObjectMapper(new YAMLFactory());
    }
}
