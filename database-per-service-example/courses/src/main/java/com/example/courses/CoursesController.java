package com.example.courses;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CoursesController {
    private final CourseRepository courseRepository;
    private final CoursePrerequisiteRepository prerequisiteRepository;
    private final CourseOfferingRepository offeringRepository;
    private final CoursesDatabaseInfoService databaseInfoService;

    public CoursesController(
            CourseRepository courseRepository,
            CoursePrerequisiteRepository prerequisiteRepository,
            CourseOfferingRepository offeringRepository,
            CoursesDatabaseInfoService databaseInfoService
    ) {
        this.courseRepository = courseRepository;
        this.prerequisiteRepository = prerequisiteRepository;
        this.offeringRepository = offeringRepository;
        this.databaseInfoService = databaseInfoService;
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    public CourseResponse createCourse(@RequestBody CreateCourseRequest request) {
        Course course = new Course();
        course.setCourseCode(request.courseCode());
        course.setTitle(request.title());
        course.setDepartment(request.department());
        course.setCreditHours(request.creditHours());
        course.setActiveFlag(request.activeFlag() ? 1 : 0);
        return CourseResponse.from(courseRepository.save(course));
    }

    @PostMapping("/courses/{courseCode}/prerequisites")
    @ResponseStatus(HttpStatus.CREATED)
    public CoursePrerequisiteResponse addPrerequisite(
            @PathVariable String courseCode,
            @RequestBody CreateCoursePrerequisiteRequest request
    ) {
        requireCourse(courseCode);
        CoursePrerequisite prerequisite = new CoursePrerequisite();
        prerequisite.setCourseCode(courseCode);
        prerequisite.setRequiredCourseCode(request.requiredCourseCode());
        return CoursePrerequisiteResponse.from(prerequisiteRepository.save(prerequisite));
    }

    @PostMapping("/course-offerings")
    @ResponseStatus(HttpStatus.CREATED)
    public CourseOfferingResponse createOffering(@RequestBody CreateCourseOfferingRequest request) {
        requireCourse(request.courseCode());
        CourseOffering offering = new CourseOffering();
        offering.setCourseCode(request.courseCode());
        offering.setTermCode(request.termCode());
        offering.setSectionNumber(request.sectionNumber());
        offering.setCapacity(request.capacity());
        offering.setEnrolledCount(request.enrolledCount());
        offering.setDeliveryMode(request.deliveryMode());
        return CourseOfferingResponse.from(offeringRepository.save(offering));
    }

    @GetMapping("/courses/{courseCode}")
    public CourseResponse getCourse(@PathVariable String courseCode) {
        return CourseResponse.from(requireCourse(courseCode));
    }

    @GetMapping("/courses/{courseCode}/prerequisites")
    public List<CoursePrerequisiteResponse> getPrerequisites(@PathVariable String courseCode) {
        requireCourse(courseCode);
        return prerequisiteRepository.findByCourseCodeOrderByRequiredCourseCodeAsc(courseCode).stream()
                .map(CoursePrerequisiteResponse::from)
                .toList();
    }

    @GetMapping("/course-offerings/{courseCode}")
    public List<CourseOfferingResponse> getOfferings(
            @PathVariable String courseCode,
            @RequestParam String termCode
    ) {
        requireCourse(courseCode);
        return offeringRepository.findByCourseCodeAndTermCodeOrderBySectionNumberAsc(courseCode, termCode).stream()
                .map(CourseOfferingResponse::from)
                .toList();
    }

    @GetMapping("/db-info")
    public CoursesDatabaseInfoService.DatabaseInfoResponse databaseInfo() {
        return databaseInfoService.getDatabaseInfo();
    }

    private Course requireCourse(String courseCode) {
        return courseRepository.findByCourseCode(courseCode)
                .orElseThrow(() -> new CourseNotFoundException(courseCode));
    }

    public record CreateCourseRequest(
            String courseCode,
            String title,
            String department,
            Integer creditHours,
            boolean activeFlag
    ) {
    }

    public record CourseResponse(
            Long id,
            String courseCode,
            String title,
            String department,
            Integer creditHours,
            boolean activeFlag
    ) {
        static CourseResponse from(Course course) {
            return new CourseResponse(
                    course.getId(),
                    course.getCourseCode(),
                    course.getTitle(),
                    course.getDepartment(),
                    course.getCreditHours(),
                    course.getActiveFlag() != null && course.getActiveFlag() == 1
            );
        }
    }

    public record CreateCoursePrerequisiteRequest(String requiredCourseCode) {
    }

    public record CoursePrerequisiteResponse(Long id, String courseCode, String requiredCourseCode) {
        static CoursePrerequisiteResponse from(CoursePrerequisite prerequisite) {
            return new CoursePrerequisiteResponse(
                    prerequisite.getId(),
                    prerequisite.getCourseCode(),
                    prerequisite.getRequiredCourseCode()
            );
        }
    }

    public record CreateCourseOfferingRequest(
            String courseCode,
            String termCode,
            Integer sectionNumber,
            Integer capacity,
            Integer enrolledCount,
            String deliveryMode
    ) {
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
        static CourseOfferingResponse from(CourseOffering offering) {
            return new CourseOfferingResponse(
                    offering.getId(),
                    offering.getCourseCode(),
                    offering.getTermCode(),
                    offering.getSectionNumber(),
                    offering.getCapacity(),
                    offering.getEnrolledCount(),
                    offering.getDeliveryMode()
            );
        }
    }
}
