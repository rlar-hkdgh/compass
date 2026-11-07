package com.example.compass.student.service;
import com.example.compass.student.dto.InputAssistDtos;
import com.example.compass.student.exception.ApiErrors;


import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.example.compass.student.dto.InputAssistDtos.ReadmeView;

@Service
public class ProjectReadmeService {
    private final JdbcTemplate db;
    private final StudentInputService students;
    private final GitHubReadmeClient client;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final Set<Long> inFlight=ConcurrentHashMap.newKeySet();
    private final Semaphore capacity=new Semaphore(4);
    public ProjectReadmeService(JdbcTemplate db,StudentInputService students,GitHubReadmeClient client,Clock clock,PlatformTransactionManager transactions) {
        this.db=db;this.students=students;this.client=client;this.clock=clock;this.tx=new TransactionTemplate(transactions);
    }
    public ReadmeView get(long student,long project) {
        var source=students.project(student,project);
        return read(project).filter(r->Objects.equals(source.githubUrl(),r.repositoryUrl())).orElseThrow(()->new ApiErrors.Failure(404,"README_NOT_COLLECTED","아직 수집된 README가 없습니다."));
    }
    public ReadmeView collect(long student,long project) {
        var source=students.project(student,project); // Ownership is checked before any outbound request.
        var repository=GitHubRepository.parse(source.githubUrl());
        if (!inFlight.add(project)) throw new ApiErrors.Failure(409,"README_COLLECTION_IN_PROGRESS","이 프로젝트의 README를 수집 중입니다.");
        if (!capacity.tryAcquire()) { inFlight.remove(project);throw new ApiErrors.Failure(429,"README_COLLECTOR_BUSY","잠시 후 다시 시도하세요."); }
        try {
            var previous=read(project);
            if (previous.isPresent() && Objects.equals(previous.get().repositoryUrl(),source.githubUrl())
                    && clock.instant().isBefore(previous.get().lastAttemptAt().plusSeconds(30))) throw new ApiErrors.Failure(429,"README_COLLECTION_COOLDOWN","같은 프로젝트는 30초 후 다시 수집할 수 있습니다.");
            Instant attemptedAt=clock.instant();
            var attempt=client.fetch(repository); // Never hold a DB transaction or row lock over network I/O.
            return tx.execute(status->{
                students.lock(student);
                var current=students.project(student,project);
                if (!Objects.equals(source.githubUrl(),current.githubUrl())) throw new ApiErrors.Failure(409,"PROJECT_CHANGED","수집 중 저장소 URL이 변경되었습니다. 변경된 저장소로 다시 수집하세요.");
                var stored=read(project);
                if (stored.isPresent() && !Objects.equals(stored.get().repositoryUrl(),source.githubUrl())) {
                    db.update("delete from student_project_readme where project_id=?",project);
                    stored=Optional.empty();
                }
                if (stored.isEmpty()) db.update("insert into student_project_readme(project_id,repository_url,last_attempt_at,last_attempt_status,last_error_code) values(?,?,?,?,?)",project,source.githubUrl(),java.sql.Timestamp.from(attemptedAt),"FAILED",null);
                if ("SUCCESS".equals(attempt.status())) {
                    db.update("update student_project_readme set readme_path=?,readme_sha=?,content=?,last_success_at=?,last_attempt_at=?,last_attempt_status='SUCCESS',last_error_code=NULL where project_id=?",attempt.path(),attempt.sha(),attempt.content(),java.sql.Timestamp.from(attemptedAt),java.sql.Timestamp.from(attemptedAt),project);
                } else {
                    db.update("update student_project_readme set last_attempt_at=?,last_attempt_status=?,last_error_code=? where project_id=?",java.sql.Timestamp.from(attemptedAt),attempt.status(),attempt.errorCode(),project);
                }
                return read(project).orElseThrow();
            });
        } finally { capacity.release();inFlight.remove(project); }
    }
    private Optional<ReadmeView> read(long project) {
        return db.query("select project_id,repository_url,readme_path,readme_sha,content,last_success_at,last_attempt_at,last_attempt_status,last_error_code from student_project_readme where project_id=?",
            (r,n)->new ReadmeView(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getTimestamp(6)==null?null:r.getTimestamp(6).toInstant(),r.getTimestamp(7).toInstant(),r.getString(8),r.getString(9)),project).stream().findFirst();
    }
}
