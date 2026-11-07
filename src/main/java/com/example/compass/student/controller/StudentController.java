package com.example.compass.student.controller;
import com.example.compass.student.service.StudentInputService;
import com.example.compass.student.dto.StudentDtos;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.security.Principal;
import java.util.List;
import static com.example.compass.student.dto.StudentDtos.*;
@RestController @RequestMapping("/api/v1") public class StudentController {
    private final StudentInputService service;
    public StudentController(StudentInputService service){
        this.service=service;
    }
    private long student(Principal principal){
        return Long.parseLong(principal.getName());
    }
    @GetMapping("/student-codes/{type}") public List<Code> codes(@PathVariable String type){
        return service.codes(type);
    }
    @GetMapping("/student-codes/regions/grouped")
    public List<RegionGroup> groupedRegions() {
        return service.groupedRegions();
    }
    @GetMapping("/me") public Profile me(Principal p){
        return service.profile(student(p));
    }
    @PatchMapping("/me") public Profile patch(Principal p,@Valid @RequestBody ProfilePatch q){
        return service.patch(student(p),q);
    }
    @GetMapping("/me/majors") public Majors majors(Principal p){
        return service.majors(student(p));
    }
    @PutMapping("/me/majors") public Majors majors(Principal p,@Valid @RequestBody Majors q){
        return service.majors(student(p),q);
    }
    @GetMapping("/me/preferences") public Preferences preferences(Principal p){
        return service.preferences(student(p));
    }
    @PutMapping("/me/preferences") public Preferences preferences(Principal p,@Valid @RequestBody Preferences q){
        return service.preferences(student(p),q);
    }
    @GetMapping("/me/certifications") public Certifications certifications(Principal p){
        return service.certifications(student(p));
    }
    @PutMapping("/me/certifications") public Certifications certifications(Principal p,@Valid @RequestBody Certifications q){
        return service.certifications(student(p),q);
    }
    @GetMapping("/me/projects") public List<Project> projects(Principal p){
        return service.projects(student(p));
    }
    @GetMapping("/me/projects/{id}") public Project project(Principal p,@PathVariable long id){
        return service.project(student(p),id);
    }
    @PostMapping("/me/projects") @ResponseStatus(HttpStatus.CREATED) public Project createProject(Principal p,@Valid @RequestBody ProjectInput q){
        return service.saveProject(student(p),null,q);
    }
    @PutMapping("/me/projects/{id}") public Project updateProject(Principal p,@PathVariable long id,@Valid @RequestBody ProjectInput q){
        return service.saveProject(student(p),id,q);
    }
    @DeleteMapping("/me/projects/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteProject(Principal p,@PathVariable long id){
        service.deleteProject(student(p),id);
    }
    @GetMapping("/me/internships") public List<Internship> internships(Principal p){
        return service.internships(student(p));
    }
    @GetMapping("/me/internships/{id}") public Internship internship(Principal p,@PathVariable long id){
        return service.internship(student(p),id);
    }
    @PostMapping("/me/internships") @ResponseStatus(HttpStatus.CREATED) public Internship createInternship(Principal p,@Valid @RequestBody InternshipInput q){
        return service.saveInternship(student(p),null,q);
    }
    @PutMapping("/me/internships/{id}") public Internship updateInternship(Principal p,@PathVariable long id,@Valid @RequestBody InternshipInput q){
        return service.saveInternship(student(p),id,q);
    }
    @DeleteMapping("/me/internships/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteInternship(Principal p,@PathVariable long id){
        service.deleteInternship(student(p),id);
    }
    @GetMapping("/me/awards") public List<Award> awards(Principal p){
        return service.awards(student(p));
    }
    @GetMapping("/me/awards/{id}") public Award award(Principal p,@PathVariable long id){
        return service.award(student(p),id);
    }
    @PostMapping("/me/awards") @ResponseStatus(HttpStatus.CREATED) public Award createAward(Principal p,@Valid @RequestBody AwardInput q){
        return service.saveAward(student(p),null,q);
    }
    @PutMapping("/me/awards/{id}") public Award updateAward(Principal p,@PathVariable long id,@Valid @RequestBody AwardInput q){
        return service.saveAward(student(p),id,q);
    }
    @DeleteMapping("/me/awards/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteAward(Principal p,@PathVariable long id){
        service.deleteAward(student(p),id);
    }
    @GetMapping("/me/courses") public List<Course> courses(Principal p){
        return service.courses(student(p));
    }
    @GetMapping("/me/courses/{id}") public Course course(Principal p,@PathVariable long id){
        return service.course(student(p),id);
    }
    @PostMapping("/me/courses") @ResponseStatus(HttpStatus.CREATED) public Course createCourse(Principal p,@Valid @RequestBody CourseInput q){
        return service.saveCourse(student(p),null,q);
    }
    @PutMapping("/me/courses/{id}") public Course updateCourse(Principal p,@PathVariable long id,@Valid @RequestBody CourseInput q){
        return service.saveCourse(student(p),id,q);
    }
    @DeleteMapping("/me/courses/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteCourse(Principal p,@PathVariable long id){
        service.deleteCourse(student(p),id);
    }
}
