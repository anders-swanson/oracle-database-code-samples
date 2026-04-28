package com.example.sample;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.example.courses.Course;
import com.example.students.Student;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DatabasePerServiceSampleRunner {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SCENARIOS_RESOURCE = "scenarios.json";

    private final StudentsClient studentsClient;
    private final CoursesClient coursesClient;

    public DatabasePerServiceSampleRunner(String studentsBaseUrl, String coursesBaseUrl) {
        this.studentsClient = new StudentsClient(studentsBaseUrl);
        this.coursesClient = new CoursesClient(coursesBaseUrl);
    }

    public static void main(String[] args) throws Exception {
        String studentsBaseUrl = args.length > 0 ? args[0] : systemValue("STUDENTS_BASE_URL", "http://localhost:8081");
        String coursesBaseUrl = args.length > 1 ? args[1] : systemValue("COURSES_BASE_URL", "http://localhost:8082");
        DatabasePerServiceSampleRunner runner = new DatabasePerServiceSampleRunner(studentsBaseUrl, coursesBaseUrl);

        for (Scenario scenario : loadScenarios()) {
            runner.runScenario(scenario);
        }
    }

    void runScenario(Scenario scenario) throws IOException, InterruptedException {
        Student student = studentsClient.createStudent(scenario);

        coursesClient.createCourse(scenario.prerequisiteCourseCode(), scenario.prerequisiteCourseTitle());
        Course targetCourse = coursesClient.createCourse(scenario);

        ScenarioVerification verification = verifyScenario(student, targetCourse, scenario.termCode());

        printScenario(scenario, student, targetCourse, verification);

        assertEquals(scenario.scenarioName(), "eligible", scenario.expectedEligible(), verification.eligible());

        System.out.println("  Assertions passed");
    }

    private static ScenarioVerification verifyScenario(Student student, Course targetCourse, String termCode) {
        List<String> completedCourseCodes = student.getCompletedCourses().stream()
                .map(Student.CompletedCourse::getCourseCode)
                .toList();

        boolean prerequisitesSatisfied = targetCourse.getPrerequisites().stream()
                .map(Course.Prerequisite::getRequiredCourseCode)
                .allMatch(completedCourseCodes::contains);
        Course.Offering offering = targetCourse.getOfferings().stream()
                .filter(candidate -> candidate.getTermCode().equals(termCode))
                .findFirst()
                .orElse(null);
        boolean seatsAvailable = offering != null && offering.getCapacity() > offering.getEnrolledCount();

        List<String> reasons = new ArrayList<>();
        if (!"ACTIVE".equalsIgnoreCase(student.getStatus())) {
            reasons.add("Student status is not ACTIVE");
        }
        if (offering == null) {
            reasons.add("No offering exists for the requested term");
        } else if (!seatsAvailable) {
            reasons.add("No seats are available in the requested offering");
        }
        if (!prerequisitesSatisfied) {
            reasons.add("Student is missing at least one required prerequisite");
        }

        return new ScenarioVerification(completedCourseCodes, prerequisitesSatisfied, seatsAvailable, reasons);
    }

    private static void printScenario(
            Scenario scenario,
            Student student,
            Course targetCourse,
            ScenarioVerification verification
    ) {
        System.out.println("Scenario: " + scenario.scenarioName());
        System.out.println("  Student: " + student.getFirstName() + " " + student.getLastName()
                + " (" + student.getStatus() + ")");
        System.out.println("  Course: " + targetCourse.getCourseCode() + " - " + targetCourse.getTitle());
        System.out.println("  Completed courses: " + String.join(", ", verification.completedCourseCodes()));
        System.out.println("  Prerequisites satisfied: " + verification.prerequisitesSatisfied());
        System.out.println("  Seats available: " + verification.seatsAvailable());
        System.out.println("  Eligible: " + verification.eligible());
        System.out.println("  Reasons: " + verification.reasonsText());
    }

    static List<Scenario> loadScenarios() {
        try (InputStream inputStream = DatabasePerServiceSampleRunner.class.getClassLoader()
                .getResourceAsStream(SCENARIOS_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing classpath resource " + SCENARIOS_RESOURCE);
            }

            List<Scenario> scenarios = OBJECT_MAPPER.readValue(inputStream, new TypeReference<List<Scenario>>() { });
            if (scenarios.isEmpty()) {
                throw new IllegalStateException("No scenarios found in " + SCENARIOS_RESOURCE);
            }
            return scenarios;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load scenarios from " + SCENARIOS_RESOURCE, exception);
        }
    }

    private static void assertEquals(String scenarioName, String label, boolean expected, boolean actual) {
        if (expected != actual) {
            throw new IllegalStateException(
                    "Scenario %s expected %s=%s but was %s".formatted(scenarioName, label, expected, actual)
            );
        }
    }

    private static String systemValue(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            value = System.getenv(key);
        }
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private record ScenarioVerification(
            List<String> completedCourseCodes,
            boolean prerequisitesSatisfied,
            boolean seatsAvailable,
            List<String> reasons
    ) {
        ScenarioVerification {
            completedCourseCodes = List.copyOf(completedCourseCodes);
            reasons = List.copyOf(reasons);
        }

        boolean eligible() {
            return reasons.isEmpty();
        }

        String reasonsText() {
            return reasons.isEmpty() ? "Registration checks passed" : String.join("; ", reasons);
        }
    }

    record Scenario(
            String scenarioName,
            String universityId,
            String firstName,
            String lastName,
            String status,
            String prerequisiteCourseCode,
            String prerequisiteCourseTitle,
            String targetCourseCode,
            String targetCourseTitle,
            String termCode,
            int capacity,
            int enrolledCount,
            List<String> completedCourseCodes,
            boolean expectedEligible
    ) {
    }
}
