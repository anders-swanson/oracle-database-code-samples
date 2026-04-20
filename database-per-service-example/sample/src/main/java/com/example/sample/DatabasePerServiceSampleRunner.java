package com.example.sample;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DatabasePerServiceSampleRunner {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClient httpClient;

    public DatabasePerServiceSampleRunner(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public static void main(String[] args) throws Exception {
        String studentsBaseUrl = args.length > 0 ? args[0] : systemValue("STUDENTS_BASE_URL", "http://localhost:8081");
        String coursesBaseUrl = args.length > 1 ? args[1] : systemValue("COURSES_BASE_URL", "http://localhost:8082");
        DatabasePerServiceSampleRunner runner = new DatabasePerServiceSampleRunner(HttpClient.newHttpClient());
        SampleReport report = runner.run(studentsBaseUrl, coursesBaseUrl);
        System.out.println(report.fullReport());
    }

    public SampleReport run(String studentsBaseUrl, String coursesBaseUrl) throws IOException, InterruptedException {
        return run(studentsBaseUrl, coursesBaseUrl, ScenarioRequest.eligible("demo"));
    }

    public SampleReport run(String studentsBaseUrl, String coursesBaseUrl, ScenarioRequest scenario)
            throws IOException, InterruptedException {
        StudentResponse student = createStudent(studentsBaseUrl, scenario);
        createCompletedCourses(studentsBaseUrl, student.id(), scenario);

        createCourse(coursesBaseUrl, scenario.prerequisiteCourseCode(), scenario.prerequisiteCourseTitle());
        createCourse(coursesBaseUrl, scenario.targetCourseCode(), scenario.targetCourseTitle());
        addPrerequisite(
                coursesBaseUrl,
                scenario.targetCourseCode(),
                scenario.prerequisiteCourseCode()
        );
        createOffering(coursesBaseUrl, scenario);

        StudentResponse fetchedStudent = get(studentsBaseUrl + "/students/" + student.id(), StudentResponse.class);
        List<CompletedCourseResponse> fetchedCompletedCourses = getList(
                studentsBaseUrl + "/students/" + student.id() + "/completed-courses",
                new TypeReference<>() { }
        );
        CourseResponse fetchedCourse = get(coursesBaseUrl + "/courses/" + encode(scenario.targetCourseCode()), CourseResponse.class);
        List<CoursePrerequisiteResponse> prerequisites = getList(
                coursesBaseUrl + "/courses/" + encode(scenario.targetCourseCode()) + "/prerequisites",
                new TypeReference<>() { }
        );
        List<CourseOfferingResponse> offerings = getList(
                coursesBaseUrl + "/course-offerings/" + encode(scenario.targetCourseCode()) + "?termCode=" + encode(scenario.termCode()),
                new TypeReference<>() { }
        );
        DatabaseInfoResponse studentsDb = get(studentsBaseUrl + "/db-info", DatabaseInfoResponse.class);
        DatabaseInfoResponse coursesDb = get(coursesBaseUrl + "/db-info", DatabaseInfoResponse.class);

        return new SampleReport(
                scenario,
                fetchedStudent,
                fetchedCompletedCourses,
                fetchedCourse,
                prerequisites,
                offerings,
                studentsDb,
                coursesDb,
                evaluateEligibility(fetchedStudent, fetchedCompletedCourses, prerequisites, offerings)
        );
    }

    private StudentResponse createStudent(String baseUrl, ScenarioRequest scenario) throws IOException, InterruptedException {
        return post(
                baseUrl + "/students",
                new CreateStudentRequest(
                        scenario.universityId(),
                        scenario.firstName(),
                        scenario.lastName(),
                        scenario.email(),
                        scenario.programCode(),
                        scenario.academicLevel(),
                        scenario.status(),
                        scenario.holdsFlag()
                ),
                StudentResponse.class
        );
    }

    private List<CompletedCourseResponse> createCompletedCourses(String baseUrl, Long studentId, ScenarioRequest scenario)
            throws IOException, InterruptedException {
        List<CompletedCourseResponse> completedCourses = new ArrayList<>();
        for (CompletedCourseSeed completedCourse : scenario.completedCourses()) {
            completedCourses.add(post(
                    baseUrl + "/students/" + studentId + "/completed-courses",
                    new CreateCompletedCourseRequest(
                            completedCourse.courseCode(),
                            completedCourse.termCode(),
                            completedCourse.grade()
                    ),
                    CompletedCourseResponse.class
            ));
        }
        return completedCourses;
    }

    private CourseResponse createCourse(String baseUrl, String courseCode, String title) throws IOException, InterruptedException {
        return post(
                baseUrl + "/courses",
                new CreateCourseRequest(courseCode, title, "Computer Science", 4, true),
                CourseResponse.class
        );
    }

    private CoursePrerequisiteResponse addPrerequisite(String baseUrl, String courseCode, String requiredCourseCode)
            throws IOException, InterruptedException {
        return post(
                baseUrl + "/courses/" + encode(courseCode) + "/prerequisites",
                new CreateCoursePrerequisiteRequest(requiredCourseCode),
                CoursePrerequisiteResponse.class
        );
    }

    private CourseOfferingResponse createOffering(String baseUrl, ScenarioRequest scenario)
            throws IOException, InterruptedException {
        return post(
                baseUrl + "/course-offerings",
                new CreateCourseOfferingRequest(
                        scenario.targetCourseCode(),
                        scenario.termCode(),
                        1,
                        scenario.capacity(),
                        scenario.enrolledCount(),
                        "IN_PERSON"
                ),
                CourseOfferingResponse.class
        );
    }

    private EligibilityDecision evaluateEligibility(
            StudentResponse student,
            List<CompletedCourseResponse> completedCourses,
            List<CoursePrerequisiteResponse> prerequisites,
            List<CourseOfferingResponse> offerings
    ) {
        Set<String> completedCourseCodes = completedCourses.stream()
                .map(CompletedCourseResponse::courseCode)
                .collect(Collectors.toSet());
        boolean studentActive = "ACTIVE".equalsIgnoreCase(student.status());
        boolean holdsClear = !student.holdsFlag();
        boolean prerequisitesSatisfied = prerequisites.stream()
                .allMatch(prerequisite -> completedCourseCodes.contains(prerequisite.requiredCourseCode()));
        CourseOfferingResponse selectedOffering = offerings.isEmpty() ? null : offerings.getFirst();
        boolean offeringAvailable = selectedOffering != null;
        boolean seatsAvailable = offeringAvailable && selectedOffering.capacity() > selectedOffering.enrolledCount();

        List<String> reasons = new ArrayList<>();
        if (!studentActive) {
            reasons.add("Student status is not ACTIVE");
        }
        if (!holdsClear) {
            reasons.add("Student has an academic hold");
        }
        if (!offeringAvailable) {
            reasons.add("No offering exists for the requested term");
        }
        if (offeringAvailable && !seatsAvailable) {
            reasons.add("No seats are available in the requested offering");
        }
        if (!prerequisitesSatisfied) {
            reasons.add("Student is missing at least one required prerequisite");
        }

        return new EligibilityDecision(
                studentActive,
                holdsClear,
                prerequisitesSatisfied,
                offeringAvailable,
                seatsAvailable,
                reasons.isEmpty(),
                reasons,
                selectedOffering
        );
    }

    private <T> T get(String url, Class<T> responseType) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("GET " + url + " failed with " + response.statusCode() + ": " + response.body());
        }
        return OBJECT_MAPPER.readValue(response.body(), responseType);
    }

    private <T> T getList(String url, TypeReference<T> typeReference) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("GET " + url + " failed with " + response.statusCode() + ": " + response.body());
        }
        return OBJECT_MAPPER.readValue(response.body(), typeReference);
    }

    private <T> T post(String url, Object payload, Class<T> responseType) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("POST " + url + " failed with " + response.statusCode() + ": " + response.body());
        }
        return OBJECT_MAPPER.readValue(response.body(), responseType);
    }

    private static String systemValue(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            value = System.getenv(key);
        }
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    record CreateStudentRequest(
            String universityId,
            String firstName,
            String lastName,
            String email,
            String programCode,
            String academicLevel,
            String status,
            boolean holdsFlag
    ) {
    }

    record CreateCompletedCourseRequest(String courseCode, String termCode, String grade) {
    }

    record CreateCourseRequest(
            String courseCode,
            String title,
            String department,
            Integer creditHours,
            boolean activeFlag
    ) {
    }

    record CreateCoursePrerequisiteRequest(String requiredCourseCode) {
    }

    record CreateCourseOfferingRequest(
            String courseCode,
            String termCode,
            Integer sectionNumber,
            Integer capacity,
            Integer enrolledCount,
            String deliveryMode
    ) {
    }

    public record StudentResponse(
            Long id,
            String universityId,
            String firstName,
            String lastName,
            String email,
            String programCode,
            String academicLevel,
            String status,
            boolean holdsFlag
    ) {
    }

    public record CompletedCourseResponse(Long id, Long studentId, String courseCode, String termCode, String grade) {
    }

    public record CourseResponse(
            Long id,
            String courseCode,
            String title,
            String department,
            Integer creditHours,
            boolean activeFlag
    ) {
    }

    public record CoursePrerequisiteResponse(Long id, String courseCode, String requiredCourseCode) {
    }

    public record CourseOfferingResponse(
            Long id,
            String courseCode,
            String termCode,
            Integer sectionNumber,
            Integer capacity,
            Integer enrolledCount,
            String deliveryMode
    ) {
        public int availableSeats() {
            return capacity - enrolledCount;
        }
    }

    public record DatabaseInfoResponse(String application, String schema, String container, long rowCount) {
    }

    public record CompletedCourseSeed(String courseCode, String termCode, String grade) {
    }

    public record ScenarioRequest(
            String scenarioName,
            String universityId,
            String firstName,
            String lastName,
            String email,
            String programCode,
            String academicLevel,
            String status,
            boolean holdsFlag,
            String prerequisiteCourseCode,
            String prerequisiteCourseTitle,
            String targetCourseCode,
            String targetCourseTitle,
            String termCode,
            int capacity,
            int enrolledCount,
            List<CompletedCourseSeed> completedCourses
    ) {
        public static ScenarioRequest eligible(String scenarioKey) {
            String normalized = normalizeScenarioKey(scenarioKey);
            return new ScenarioRequest(
                    normalized,
                    "U-" + normalized + "-1001",
                    "Alice",
                    "Nguyen",
                    normalized.toLowerCase() + ".alice@example.edu",
                    "CS",
                    "UNDERGRAD",
                    "ACTIVE",
                    false,
                    normalized + "-CS201",
                    "Data Structures",
                    normalized + "-CS404",
                    "Distributed Systems",
                    "2026FALL",
                    30,
                    24,
                    List.of(
                            new CompletedCourseSeed(normalized + "-CS201", "2025SPRING", "A"),
                            new CompletedCourseSeed(normalized + "-MATH101", "2024FALL", "B+")
                    )
            );
        }

        public static ScenarioRequest academicHold(String scenarioKey) {
            ScenarioRequest eligible = eligible(scenarioKey);
            return new ScenarioRequest(
                    eligible.scenarioName(),
                    eligible.universityId(),
                    eligible.firstName(),
                    eligible.lastName(),
                    eligible.email(),
                    eligible.programCode(),
                    eligible.academicLevel(),
                    eligible.status(),
                    true,
                    eligible.prerequisiteCourseCode(),
                    eligible.prerequisiteCourseTitle(),
                    eligible.targetCourseCode(),
                    eligible.targetCourseTitle(),
                    eligible.termCode(),
                    eligible.capacity(),
                    eligible.enrolledCount(),
                    eligible.completedCourses()
            );
        }

        public static ScenarioRequest missingPrerequisite(String scenarioKey) {
            ScenarioRequest eligible = eligible(scenarioKey);
            return new ScenarioRequest(
                    eligible.scenarioName(),
                    eligible.universityId(),
                    eligible.firstName(),
                    eligible.lastName(),
                    eligible.email(),
                    eligible.programCode(),
                    eligible.academicLevel(),
                    eligible.status(),
                    eligible.holdsFlag(),
                    eligible.prerequisiteCourseCode(),
                    eligible.prerequisiteCourseTitle(),
                    eligible.targetCourseCode(),
                    eligible.targetCourseTitle(),
                    eligible.termCode(),
                    eligible.capacity(),
                    eligible.enrolledCount(),
                    List.of(new CompletedCourseSeed(eligible.scenarioName() + "-MATH101", "2024FALL", "A-"))
            );
        }

        private static String normalizeScenarioKey(String scenarioKey) {
            return scenarioKey.toUpperCase().replaceAll("[^A-Z0-9]+", "");
        }
    }

    public record EligibilityDecision(
            boolean studentActive,
            boolean holdsClear,
            boolean prerequisitesSatisfied,
            boolean offeringAvailable,
            boolean seatsAvailable,
            boolean eligible,
            List<String> reasons,
            CourseOfferingResponse selectedOffering
    ) {
    }

    public record SampleReport(
            ScenarioRequest scenario,
            StudentResponse student,
            List<CompletedCourseResponse> completedCourses,
            CourseResponse course,
            List<CoursePrerequisiteResponse> prerequisites,
            List<CourseOfferingResponse> offerings,
            DatabaseInfoResponse studentsDatabase,
            DatabaseInfoResponse coursesDatabase,
            EligibilityDecision decision
    ) {
        public String render() {
            String offeringSummary = decision.selectedOffering() == null
                    ? "No offering available"
                    : "%s section %d, available seats %d".formatted(
                            decision.selectedOffering().termCode(),
                            decision.selectedOffering().sectionNumber(),
                            decision.selectedOffering().availableSeats()
                    );
            String prerequisiteSummary = prerequisites.isEmpty()
                    ? "No prerequisites"
                    : prerequisites.stream()
                            .map(CoursePrerequisiteResponse::requiredCourseCode)
                            .collect(Collectors.joining(", "));
            String reasons = decision.reasons().isEmpty()
                    ? "All eligibility checks passed"
                    : String.join("; ", decision.reasons());
            return """
                    Registration eligibility for %s %s
                    Requested course: %s - %s
                    Student status: %s
                    Academic hold: %s
                    Prerequisites: %s
                    Offering: %s
                    Decision: %s
                    Reasons: %s
                    Students database: %s (%s)
                    Courses database: %s (%s)
                    """
                    .formatted(
                            student.firstName(),
                            student.lastName(),
                            course.courseCode(),
                            course.title(),
                            student.status(),
                            student.holdsFlag(),
                            prerequisiteSummary,
                            offeringSummary,
                            decision.eligible() ? "ELIGIBLE" : "INELIGIBLE",
                            reasons,
                            studentsDatabase.container(),
                            studentsDatabase.schema(),
                            coursesDatabase.container(),
                            coursesDatabase.schema()
                    );
        }

        public String fullReport() {
            return """
                    Sample report
                    Scenario: %s
                    Student:
                      id=%s
                      universityId=%s
                      firstName=%s
                      lastName=%s
                      email=%s
                      programCode=%s
                      academicLevel=%s
                      status=%s
                      holdsFlag=%s
                    Completed courses:
                    %s
                    Target course:
                      id=%s
                      courseCode=%s
                      title=%s
                      department=%s
                      creditHours=%s
                      activeFlag=%s
                    Prerequisites:
                    %s
                    Offerings:
                    %s
                    Eligibility checks:
                      studentActive=%s
                      holdsClear=%s
                      prerequisitesSatisfied=%s
                      offeringAvailable=%s
                      seatsAvailable=%s
                      eligible=%s
                      reasons=%s
                    Students database:
                      application=%s
                      schema=%s
                      container=%s
                      rowCount=%s
                    Courses database:
                      application=%s
                      schema=%s
                      container=%s
                      rowCount=%s
                    Summary:
                    %s
                    """
                    .formatted(
                            scenario.scenarioName(),
                            student.id(),
                            student.universityId(),
                            student.firstName(),
                            student.lastName(),
                            student.email(),
                            student.programCode(),
                            student.academicLevel(),
                            student.status(),
                            student.holdsFlag(),
                            formatCompletedCourses(),
                            course.id(),
                            course.courseCode(),
                            course.title(),
                            course.department(),
                            course.creditHours(),
                            course.activeFlag(),
                            formatPrerequisites(),
                            formatOfferings(),
                            decision.studentActive(),
                            decision.holdsClear(),
                            decision.prerequisitesSatisfied(),
                            decision.offeringAvailable(),
                            decision.seatsAvailable(),
                            decision.eligible(),
                            decision.reasons(),
                            studentsDatabase.application(),
                            studentsDatabase.schema(),
                            studentsDatabase.container(),
                            studentsDatabase.rowCount(),
                            coursesDatabase.application(),
                            coursesDatabase.schema(),
                            coursesDatabase.container(),
                            coursesDatabase.rowCount(),
                            render()
                    );
        }

        private String formatCompletedCourses() {
            if (completedCourses.isEmpty()) {
                return "  (none)";
            }
            return completedCourses.stream()
                    .map(course -> "  - %s (%s, grade=%s)".formatted(course.courseCode(), course.termCode(), course.grade()))
                    .collect(Collectors.joining("\n"));
        }

        private String formatPrerequisites() {
            if (prerequisites.isEmpty()) {
                return "  (none)";
            }
            return prerequisites.stream()
                    .map(prerequisite -> "  - %s requires %s".formatted(
                            prerequisite.courseCode(),
                            prerequisite.requiredCourseCode()
                    ))
                    .collect(Collectors.joining("\n"));
        }

        private String formatOfferings() {
            if (offerings.isEmpty()) {
                return "  (none)";
            }
            return offerings.stream()
                    .map(offering -> "  - %s section %d, capacity=%d, enrolled=%d, deliveryMode=%s".formatted(
                            offering.termCode(),
                            offering.sectionNumber(),
                            offering.capacity(),
                            offering.enrolledCount(),
                            offering.deliveryMode()
                    ))
                    .collect(Collectors.joining("\n"));
        }
    }
}
