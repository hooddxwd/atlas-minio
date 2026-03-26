package org.apache.atlas.minio.classification;

import org.apache.atlas.minio.model.MinioBucket;
import org.apache.atlas.minio.model.MinioObject;
import org.apache.atlas.minio.model.ClassificationRule;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for ClassificationRules
 */
@RunWith(MockitoJUnitRunner.class)
public class ClassificationRulesTest {

    private ClassificationRules classificationRules;
    private MinioObject testObject;
    private MinioBucket testBucket;
    private File tempRulesFile;

    @Before
    public void setUp() throws IOException {
        classificationRules = new ClassificationRules();

        // Setup test object
        testObject = new MinioObject();
        testObject.setBucketName("data-bucket");
        testObject.setPath("production/data.csv");
        testObject.setSize(1024L);
        testObject.setContentType("text/csv");

        // Setup test bucket
        testBucket = new MinioBucket();
        testBucket.setName("production-bucket");

        // Create temporary rules file
        tempRulesFile = File.createTempFile("classification-rules", ".json");
        tempRulesFile.deleteOnExit();
    }

    @Test
    public void testEvaluate_PathRule_Matches() {
        // Arrange
        ClassificationRule rule = new ClassificationRule(
                "Production Data",
                ClassificationRule.RuleType.PATH,
                "/data-bucket/production/.*",
                "PRODUCTION_DATA"
        );
        rule.addAttribute("environment", "production");
        classificationRules.addRule(rule);

        // Act
        Map<String, Map<String, Object>> result = classificationRules.evaluate(testObject);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("PRODUCTION_DATA"));
        assertEquals("production", result.get("PRODUCTION_DATA").get("environment"));
    }

    @Test
    public void testEvaluate_PathRule_NoMatch() {
        // Arrange
        ClassificationRule rule = new ClassificationRule(
                "Test Data",
                ClassificationRule.RuleType.PATH,
                "/test-bucket/.*",
                "TEST_DATA"
        );
        classificationRules.addRule(rule);

        // Act
        Map<String, Map<String, Object>> result = classificationRules.evaluate(testObject);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void testEvaluate_ContentTypeRule_Matches() {
        // Arrange
        ClassificationRule rule = new ClassificationRule(
                "CSV Document",
                ClassificationRule.RuleType.CONTENT_TYPE,
                "text/.*",
                "DOCUMENT"
        );
        classificationRules.addRule(rule);

        // Act
        Map<String, Map<String, Object>> result = classificationRules.evaluate(testObject);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("DOCUMENT"));
    }

    @Test
    public void testEvaluate_MetadataRule_Matches() {
        // Arrange
        testObject.addUserMetadata("x-amz-meta-sensitive", "true");

        ClassificationRule rule = new ClassificationRule();
        rule.setName("Sensitive Data");
        rule.setType(ClassificationRule.RuleType.METADATA);
        rule.setKey("x-amz-meta-sensitive");
        rule.setValue("true");
        rule.setClassification("SENSITIVE_DATA");
        rule.addAttribute("data_sensitivity", "high");

        classificationRules.addRule(rule);

        // Act
        Map<String, Map<String, Object>> result = classificationRules.evaluate(testObject);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("SENSITIVE_DATA"));
        assertEquals("high", result.get("SENSITIVE_DATA").get("data_sensitivity"));
    }

    @Test
    public void testEvaluate_MetadataRule_MatchesAnyValue() {
        // Arrange
        testObject.addUserMetadata("x-amz-meta-classified", "yes");

        ClassificationRule rule = new ClassificationRule();
        rule.setName("Classified Data");
        rule.setType(ClassificationRule.RuleType.METADATA);
        rule.setKey("x-amz-meta-classified");
        // No value set - should match any value
        rule.setClassification("CLASSIFIED");

        classificationRules.addRule(rule);

        // Act
        Map<String, Map<String, Object>> result = classificationRules.evaluate(testObject);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("CLASSIFIED"));
    }

    @Test
    public void testEvaluate_MetadataRule_NoMatch() {
        // Arrange
        testObject.addUserMetadata("x-amz-meta-other", "value");

        ClassificationRule rule = new ClassificationRule();
        rule.setName("Sensitive Data");
        rule.setType(ClassificationRule.RuleType.METADATA);
        rule.setKey("x-amz-meta-sensitive");
        rule.setValue("true");
        rule.setClassification("SENSITIVE_DATA");

        classificationRules.addRule(rule);

        // Act
        Map<String, Map<String, Object>> result = classificationRules.evaluate(testObject);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void testEvaluate_SizeRule_Matches() {
        // Arrange
        testObject.setSize(5000L);

        ClassificationRule rule = new ClassificationRule();
        rule.setName("Large File");
        rule.setType(ClassificationRule.RuleType.SIZE);
        rule.setSizeMin(1024L);
        rule.setSizeMax(10000L);
        rule.setClassification("LARGE_FILE");

        classificationRules.addRule(rule);

        // Act
        Map<String, Map<String, Object>> result = classificationRules.evaluate(testObject);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("LARGE_FILE"));
    }

    @Test
    public void testEvaluate_SizeRule_TooSmall() {
        // Arrange
        testObject.setSize(500L);

        ClassificationRule rule = new ClassificationRule();
        rule.setName("Large File");
        rule.setType(ClassificationRule.RuleType.SIZE);
        rule.setSizeMin(1024L);
        rule.setClassification("LARGE_FILE");

        classificationRules.addRule(rule);

        // Act
        Map<String, Map<String, Object>> result = classificationRules.evaluate(testObject);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void testEvaluate_SizeRule_TooLarge() {
        // Arrange
        testObject.setSize(20000L);

        ClassificationRule rule = new ClassificationRule();
        rule.setName("Medium File");
        rule.setType(ClassificationRule.RuleType.SIZE);
        rule.setSizeMax(10000L);
        rule.setClassification("MEDIUM_FILE");

        classificationRules.addRule(rule);

        // Act
        Map<String, Map<String, Object>> result = classificationRules.evaluate(testObject);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void testEvaluate_MultipleRules_MultipleMatches() {
        // Arrange
        ClassificationRule pathRule = new ClassificationRule(
                "Production Data",
                ClassificationRule.RuleType.PATH,
                "/data-bucket/production/.*",
                "PRODUCTION_DATA"
        );
        classificationRules.addRule(pathRule);

        ClassificationRule contentTypeRule = new ClassificationRule(
                "CSV Document",
                ClassificationRule.RuleType.CONTENT_TYPE,
                "text/.*",
                "DOCUMENT"
        );
        classificationRules.addRule(contentTypeRule);

        // Act
        Map<String, Map<String, Object>> result = classificationRules.evaluate(testObject);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.containsKey("PRODUCTION_DATA"));
        assertTrue(result.containsKey("DOCUMENT"));
    }

    @Test
    public void testEvaluate_DisabledRule() {
        // Arrange
        ClassificationRule rule = new ClassificationRule(
                "Production Data",
                ClassificationRule.RuleType.PATH,
                "/data-bucket/production/.*",
                "PRODUCTION_DATA"
        );
        rule.setEnabled(false);
        classificationRules.addRule(rule);

        // Act
        Map<String, Map<String, Object>> result = classificationRules.evaluate(testObject);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void testEvaluate_Bucket_PathRule() {
        // Arrange
        ClassificationRule rule = new ClassificationRule(
                "Production Bucket",
                ClassificationRule.RuleType.PATH,
                "/production-bucket",
                "PRODUCTION_BUCKET"
        );
        classificationRules.addRule(rule);

        // Act
        Map<String, Map<String, Object>> result = classificationRules.evaluate(testBucket);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("PRODUCTION_BUCKET"));
    }

    @Test
    public void testEvaluate_NullObject() {
        // Act
        Map<String, Map<String, Object>> result = classificationRules.evaluate((MinioObject) null);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void testEvaluate_NullBucket() {
        // Act
        Map<String, Map<String, Object>> result = classificationRules.evaluate((MinioBucket) null);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void testLoadRules_ValidJson() throws IOException {
        // Arrange
        String jsonContent = "{\n" +
                "  \"rules\": [\n" +
                "    {\n" +
                "      \"name\": \"Production Data\",\n" +
                "      \"type\": \"path\",\n" +
                "      \"pattern\": \"/production/.*\",\n" +
                "      \"classification\": \"PRODUCTION_DATA\",\n" +
                "      \"attributes\": {\n" +
                "        \"environment\": \"production\"\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        try (FileWriter writer = new FileWriter(tempRulesFile)) {
            writer.write(jsonContent);
        }

        // Act
        classificationRules.loadRules(tempRulesFile.getAbsolutePath());

        // Assert
        List<ClassificationRule> rules = classificationRules.getRules();
        assertNotNull(rules);
        assertEquals(1, rules.size());
        assertEquals("Production Data", rules.get(0).getName());
        assertEquals(ClassificationRule.RuleType.PATH, rules.get(0).getType());
        assertEquals("PRODUCTION_DATA", rules.get(0).getClassification());
    }

    @Test
    public void testLoadRules_FileNotFound() {
        // Act
        classificationRules.loadRules("/non/existent/path/rules.json");

        // Assert - should not throw exception, just use empty rules
        List<ClassificationRule> rules = classificationRules.getRules();
        assertNotNull(rules);
        assertEquals(0, rules.size());
    }

    @Test
    public void testGetRulesByType() {
        // Arrange
        ClassificationRule pathRule = new ClassificationRule(
                "Path Rule",
                ClassificationRule.RuleType.PATH,
                "/test/.*",
                "TEST"
        );
        classificationRules.addRule(pathRule);

        ClassificationRule contentTypeRule = new ClassificationRule(
                "Content Type Rule",
                ClassificationRule.RuleType.CONTENT_TYPE,
                "text/.*",
                "TEXT"
        );
        classificationRules.addRule(contentTypeRule);

        // Act
        List<ClassificationRule> pathRules = classificationRules.getRulesByType(ClassificationRule.RuleType.PATH);
        List<ClassificationRule> contentTypeRules = classificationRules.getRulesByType(ClassificationRule.RuleType.CONTENT_TYPE);

        // Assert
        assertEquals(1, pathRules.size());
        assertEquals("Path Rule", pathRules.get(0).getName());

        assertEquals(1, contentTypeRules.size());
        assertEquals("Content Type Rule", contentTypeRules.get(0).getName());
    }

    @Test
    public void testAddRule() {
        // Arrange
        ClassificationRule rule = new ClassificationRule(
                "Test Rule",
                ClassificationRule.RuleType.PATH,
                "/test/.*",
                "TEST"
        );

        // Act
        classificationRules.addRule(rule);

        // Assert
        List<ClassificationRule> rules = classificationRules.getRules();
        assertEquals(1, rules.size());
        assertEquals("Test Rule", rules.get(0).getName());
    }

    @Test
    public void testRemoveRule() {
        // Arrange
        ClassificationRule rule = new ClassificationRule(
                "Test Rule",
                ClassificationRule.RuleType.PATH,
                "/test/.*",
                "TEST"
        );
        classificationRules.addRule(rule);

        // Act
        classificationRules.removeRule("Test Rule");

        // Assert
        List<ClassificationRule> rules = classificationRules.getRules();
        assertEquals(0, rules.size());
    }

    @Test
    public void testClearRules() {
        // Arrange
        classificationRules.addRule(new ClassificationRule(
                "Rule 1",
                ClassificationRule.RuleType.PATH,
                "/test1/.*",
                "TEST1"
        ));
        classificationRules.addRule(new ClassificationRule(
                "Rule 2",
                ClassificationRule.RuleType.PATH,
                "/test2/.*",
                "TEST2"
        ));

        // Act
        classificationRules.clearRules();

        // Assert
        List<ClassificationRule> rules = classificationRules.getRules();
        assertEquals(0, rules.size());
    }

    @Test
    public void testEvaluate_InvalidRegexPattern() {
        // Arrange
        ClassificationRule rule = new ClassificationRule(
                "Invalid Pattern",
                ClassificationRule.RuleType.PATH,
                "[invalid(regex",  // Invalid regex
                "TEST"
        );
        classificationRules.addRule(rule);

        // Act - should not throw exception, just skip invalid rule
        Map<String, Map<String, Object>> result = classificationRules.evaluate(testObject);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void testEvaluate_BackupFileRule() {
        // Arrange
        testObject.setPath("data.backup");

        ClassificationRule rule = new ClassificationRule(
                "Backup File",
                ClassificationRule.RuleType.PATH,
                ".*\\.backup$",
                "BACKUP"
        );
        classificationRules.addRule(rule);

        // Act
        Map<String, Map<String, Object>> result = classificationRules.evaluate(testObject);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("BACKUP"));
    }

    @Test
    public void testEvaluate_TempFileRule() {
        // Arrange
        testObject.setPath("tmp/temp-file.txt");

        ClassificationRule rule = new ClassificationRule(
                "Temporary File",
                ClassificationRule.RuleType.PATH,
                "/tmp/.*",
                "TEMPORARY"
        );
        classificationRules.addRule(rule);

        // Act
        Map<String, Map<String, Object>> result = classificationRules.evaluate(testObject);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("TEMPORARY"));
    }
}
