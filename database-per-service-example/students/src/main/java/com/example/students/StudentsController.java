package com.example.students;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StudentsController {
    private final StudentRepository studentRepository;
    private final StudentCompletedCourseRepository completedCourseRepository;
    private final StudentsDatabaseInfoService databaseInfoService;

    public StudentsController(
            StudentRepository studentRepository,
            StudentCompletedCourseRepository completedCourseRepository,
            StudentsDatabaseInfoService databaseInfoService
    ) {
        this.studentRepository = studentRepository;
        this.completedCourseRepository = completedCourseRepository;
        this.databaseInfoService = databaseInfoService;
    }

    @PostMapping("/students")
    @ResponseStatus(HttpStatus.CREATED)
    public StudentResponse createStudent(@RequestBody CreateStudentRequest request) {
        Student student = new Student();
        student.setUniversityId(request.universityId());
        student.setFirstName(request.firstName());
        student.setLastName(request.lastName());
        student.setEmail(request.email());
        student.setProgramCode(request.programCode());
        student.setAcademicLevel(request.academicLevel());
        student.setStatus(request.status());
        student.setHoldsFlag(request.holdsFlag() ? 1 : 0);
        return StudentResponse.from(studentRepository.save(student));
    }

    @PostMapping("/students/{studentId}/completed-courses")
    @ResponseStatus(HttpStatus.CREATED)
    public CompletedCourseResponse addCompletedCourse(
            @PathVariable Long studentId,
            @RequestBody CreateCompletedCourseRequest request
    ) {
        requireStudent(studentId);
        StudentCompletedCourse completedCourse = new StudentCompletedCourse();
        completedCourse.setStudentId(studentId);
        completedCourse.setCourseCode(request.courseCode());
        completedCourse.setTermCode(request.termCode());
        completedCourse.setGrade(request.grade());
        return CompletedCourseResponse.from(completedCourseRepository.save(completedCourse));
    }

    @GetMapping("/students/{studentId}")
    public StudentResponse getStudent(@PathVariable Long studentId) {
        return StudentResponse.from(requireStudent(studentId));
    }

    @GetMapping("/students/{studentId}/completed-courses")
    public List<CompletedCourseResponse> getCompletedCourses(@PathVariable Long studentId) {
        requireStudent(studentId);
        return completedCourseRepository.findByStudentIdOrderByCourseCodeAscTermCodeAsc(studentId).stream()
                .map(CompletedCourseResponse::from)
                .toList();
    }

    @GetMapping("/db-info")
    public StudentsDatabaseInfoService.DatabaseInfoResponse databaseInfo() {
        return databaseInfoService.getDatabaseInfo();
    }

    private Student requireStudent(Long studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
    }

    public record CreateStudentRequest(
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
        static StudentResponse from(Student student) {
            return new StudentResponse(
                    student.getId(),
                    student.getUniversityId(),
                    student.getFirstName(),
                    student.getLastName(),
                    student.getEmail(),
                    student.getProgramCode(),
                    student.getAcademicLevel(),
                    student.getStatus(),
                    student.getHoldsFlag() != null && student.getHoldsFlag() == 1
            );
        }
    }

    public record CreateCompletedCourseRequest(String courseCode, String termCode, String grade) {
    }

    public record CompletedCourseResponse(Long id, Long studentId, String courseCode, String termCode, String grade) {
        static CompletedCourseResponse from(StudentCompletedCourse completedCourse) {
            return new CompletedCourseResponse(
                    completedCourse.getId(),
                    completedCourse.getStudentId(),
                    completedCourse.getCourseCode(),
                    completedCourse.getTermCode(),
                    completedCourse.getGrade()
            );
        }
    }
}
