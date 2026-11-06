package com.shambanexa.implementation;

import com.shambanexa.common.ApiException;
import com.shambanexa.dto.Workforce;
import com.shambanexa.entity.FarmerRequest;
import com.shambanexa.entity.StaffAttendance;
import com.shambanexa.entity.StaffCompetency;
import com.shambanexa.entity.StaffGoal;
import com.shambanexa.entity.StaffMember;
import com.shambanexa.entity.StaffReview;
import com.shambanexa.entity.StaffShift;
import com.shambanexa.entity.StaffTimesheet;
import com.shambanexa.entity.StaffTraining;
import com.shambanexa.repository.FarmerRequestRepository;
import com.shambanexa.repository.StaffAttendanceRepository;
import com.shambanexa.repository.StaffCompetencyRepository;
import com.shambanexa.repository.StaffGoalRepository;
import com.shambanexa.repository.StaffMemberRepository;
import com.shambanexa.repository.StaffReviewRepository;
import com.shambanexa.repository.StaffShiftRepository;
import com.shambanexa.repository.StaffTimesheetRepository;
import com.shambanexa.repository.StaffTrainingRepository;
import com.shambanexa.security.AccessGuard;
import com.shambanexa.service.AuditTrail;
import com.shambanexa.service.FarmService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class WorkforceService {

    private static final Set<String> MARKS = Set.of("PRESENT", "LATE", "ABSENT", "LEAVE");
    private static final Set<String> LEVELS = Set.of("MET", "GAP");

    private final AccessGuard access;
    private final AuditTrail audit;
    private final FarmService farms;
    private final StaffMemberRepository staff;
    private final FarmerRequestRepository requests;
    private final StaffAttendanceRepository attendance;
    private final StaffShiftRepository shifts;
    private final StaffTimesheetRepository timesheets;
    private final StaffReviewRepository reviews;
    private final StaffTrainingRepository training;
    private final StaffGoalRepository goals;
    private final StaffCompetencyRepository competencies;

    public WorkforceService(AccessGuard access, AuditTrail audit, FarmService farms, StaffMemberRepository staff,
                            FarmerRequestRepository requests, StaffAttendanceRepository attendance, StaffShiftRepository shifts,
                            StaffTimesheetRepository timesheets, StaffReviewRepository reviews, StaffTrainingRepository training,
                            StaffGoalRepository goals, StaffCompetencyRepository competencies) {
        this.access = access;
        this.audit = audit;
        this.farms = farms;
        this.staff = staff;
        this.requests = requests;
        this.attendance = attendance;
        this.shifts = shifts;
        this.timesheets = timesheets;
        this.reviews = reviews;
        this.training = training;
        this.goals = goals;
        this.competencies = competencies;
    }

    @Transactional(readOnly = true)
    public Workforce.Bundle bundle() {
        return new Workforce.Bundle(requestViews(), attendanceViews(), shiftViews(), timesheetViews(), reviewViews(), trainingViews(), goalViews(), competencyViews());
    }

    @Transactional(readOnly = true)
    public List<Workforce.FarmerRequestView> requestViews() {
        return requests.findByTenantIdOrderByCreatedAtDesc(access.tenantId()).stream()
                .filter(row -> access.canReadFarm(row.getFarmId()))
                .map(this::view)
                .toList();
    }

    @Transactional
    public Workforce.FarmerRequestView createRequest(Workforce.FarmerRequestBody body) {
        access.requireAuthority("FARM_WRITE");
        access.checkFarm(body.farmId());
        String subject = required(body.subject(), 160, "A subject is required");
        String message = required(body.message(), 2000, "A message is required");
        FarmerRequest row = new FarmerRequest();
        row.setTenantId(access.tenantId());
        row.setFarmId(body.farmId());
        row.setAuthorUserId(access.current().id());
        row.setAuthorName(access.current().fullName());
        row.setSubject(subject);
        row.setMessage(message);
        row.setStatus("OPEN");
        requests.save(row);
        audit.record("REQUEST_CREATED", "FARMER_REQUEST", row.getId(), subject);
        audit.activity("STAFF", "Farm request sent", subject, row.getFarmId());
        return view(row);
    }

    @Transactional
    public Workforce.FarmerRequestView reply(UUID id, Workforce.ReplyBody body) {
        if (!access.current().authorities().contains("ADVICE_WRITE") && !access.current().authorities().contains("STAFF_WRITE")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to do that");
        }
        FarmerRequest row = requests.findById(id).filter(item -> item.getTenantId().equals(access.tenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REQUEST_MISSING", "That request is not in your scope."));
        access.checkFarm(row.getFarmId());
        row.setReply(required(body.reply(), 2000, "A reply is required"));
        row.setRepliedBy(access.current().fullName());
        row.setAnsweredAt(Instant.now());
        row.setStatus("ANSWERED");
        audit.record("REQUEST_ANSWERED", "FARMER_REQUEST", row.getId(), row.getSubject());
        audit.activity("STAFF", "Farm request answered", row.getSubject(), row.getFarmId());
        return view(row);
    }

    @Transactional
    public Workforce.AttendanceView saveAttendance(Workforce.AttendanceRequest body) {
        access.requireAuthority("STAFF_WRITE");
        StaffMember member = requireStaff(body.staffId());
        if (body.workDate() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_REQUIRED", "A date is required");
        }
        String mark = body.mark() == null ? "" : body.mark().trim().toUpperCase(Locale.ROOT);
        if (!MARKS.contains(mark)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MARK_INVALID", "Choose present, late, absent, or leave");
        }
        StaffAttendance row = attendance.findByTenantIdAndStaffIdAndWorkDate(access.tenantId(), member.getId(), body.workDate())
                .orElseGet(StaffAttendance::new);
        row.setTenantId(access.tenantId());
        row.setStaffId(member.getId());
        row.setFarmId(member.getFarmId());
        row.setWorkDate(body.workDate());
        row.setMark(mark);
        attendance.save(row);
        audit.record("ATTENDANCE_MARKED", "STAFF", member.getId(), member.getFullName() + " " + mark);
        audit.activity("STAFF", "Attendance marked", member.getFullName() + " · " + mark.toLowerCase(Locale.ROOT), member.getFarmId());
        return new Workforce.AttendanceView(row.getId(), member.getId(), member.getFullName(), member.getFarmId(), farmName(member.getFarmId()), row.getWorkDate(), row.getMark());
    }

    @Transactional
    public Workforce.ShiftView saveShift(Workforce.ShiftRequest body) {
        access.requireAuthority("STAFF_WRITE");
        StaffMember member = requireStaff(body.staffId());
        if (body.weekday() < 1 || body.weekday() > 7) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WEEKDAY_INVALID", "Choose a day from Monday to Sunday");
        }
        if (body.starts() == null || body.ends() == null || !body.starts().isBefore(body.ends())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SHIFT_INVALID", "The shift must start before it ends");
        }
        StaffShift row = shifts.findByTenantIdAndStaffIdAndWeekday(access.tenantId(), member.getId(), body.weekday()).orElseGet(StaffShift::new);
        row.setTenantId(access.tenantId());
        row.setStaffId(member.getId());
        row.setFarmId(member.getFarmId());
        row.setWeekday(body.weekday());
        row.setStarts(body.starts());
        row.setEnds(body.ends());
        shifts.save(row);
        audit.record("SHIFT_SAVED", "STAFF", member.getId(), member.getFullName());
        audit.activity("STAFF", "Shift saved", member.getFullName(), member.getFarmId());
        return shiftView(row, member);
    }

    @Transactional
    public Workforce.TimesheetView saveTimesheet(Workforce.TimesheetRequest body) {
        access.requireAuthority("STAFF_WRITE");
        StaffMember member = requireStaff(body.staffId());
        if (body.workDate() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_REQUIRED", "A date is required");
        }
        BigDecimal hours = body.hours();
        if (hours == null || hours.compareTo(new BigDecimal("0.5")) < 0 || hours.compareTo(new BigDecimal("24")) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "HOURS_INVALID", "Hours must be between 0.5 and 24");
        }
        StaffTimesheet row = timesheets.findByTenantIdAndStaffIdAndWorkDate(access.tenantId(), member.getId(), body.workDate()).orElseGet(StaffTimesheet::new);
        row.setTenantId(access.tenantId());
        row.setStaffId(member.getId());
        row.setFarmId(member.getFarmId());
        row.setWorkDate(body.workDate());
        row.setHours(hours);
        row.setNote(blank(body.note(), 400));
        timesheets.save(row);
        audit.record("TIMESHEET_SAVED", "STAFF", member.getId(), member.getFullName());
        audit.activity("STAFF", "Timesheet saved", member.getFullName(), member.getFarmId());
        return new Workforce.TimesheetView(row.getId(), member.getId(), member.getFullName(), member.getFarmId(), farmName(member.getFarmId()), row.getWorkDate(), row.getHours(), row.getNote());
    }

    @Transactional
    public Workforce.ReviewView saveReview(Workforce.ReviewRequest body) {
        access.requireAuthority("STAFF_WRITE");
        StaffMember member = requireStaff(body.staffId());
        if (body.reviewedOn() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_REQUIRED", "A review date is required");
        }
        BigDecimal score = body.score();
        if (score == null || score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(new BigDecimal("5")) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SCORE_INVALID", "A review score is from 0 to 5");
        }
        StaffReview row = new StaffReview();
        row.setTenantId(access.tenantId());
        row.setStaffId(member.getId());
        row.setFarmId(member.getFarmId());
        row.setScore(score);
        row.setReviewedOn(body.reviewedOn());
        row.setNotes(blank(body.notes(), 1000));
        row.setReviewerName(access.current().fullName());
        reviews.save(row);
        audit.record("REVIEW_RECORDED", "STAFF", member.getId(), member.getFullName());
        audit.activity("STAFF", "Review recorded", member.getFullName(), member.getFarmId());
        return new Workforce.ReviewView(row.getId(), member.getId(), member.getFullName(), member.getFarmId(), farmName(member.getFarmId()), row.getScore(), row.getReviewedOn(), row.getNotes(), row.getReviewerName());
    }

    @Transactional
    public Workforce.TrainingView saveTraining(Workforce.TrainingRequest body) {
        access.requireAuthority("STAFF_WRITE");
        StaffMember member = requireStaff(body.staffId());
        StaffTraining row = new StaffTraining();
        row.setTenantId(access.tenantId());
        row.setStaffId(member.getId());
        row.setFarmId(member.getFarmId());
        row.setTitle(required(body.title(), 160, "A training title is required"));
        row.setCompletedOn(body.completedOn());
        training.save(row);
        audit.record("TRAINING_RECORDED", "STAFF", member.getId(), row.getTitle());
        audit.activity("STAFF", "Training recorded", member.getFullName() + " · " + row.getTitle(), member.getFarmId());
        return new Workforce.TrainingView(row.getId(), member.getId(), member.getFullName(), member.getFarmId(), farmName(member.getFarmId()), row.getTitle(), row.getCompletedOn());
    }

    @Transactional
    public Workforce.GoalView saveGoal(Workforce.GoalRequest body) {
        access.requireAuthority("STAFF_WRITE");
        StaffMember member = requireStaff(body.staffId());
        StaffGoal row = new StaffGoal();
        row.setTenantId(access.tenantId());
        row.setStaffId(member.getId());
        row.setFarmId(member.getFarmId());
        row.setTitle(required(body.title(), 160, "A goal is required"));
        row.setStatus("OPEN");
        goals.save(row);
        audit.record("GOAL_RECORDED", "STAFF", member.getId(), row.getTitle());
        audit.activity("STAFF", "Goal recorded", member.getFullName() + " · " + row.getTitle(), member.getFarmId());
        return goalView(row, member);
    }

    @Transactional
    public Workforce.GoalView completeGoal(UUID id) {
        access.requireAuthority("STAFF_WRITE");
        StaffGoal row = goals.findById(id).filter(item -> item.getTenantId().equals(access.tenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GOAL_MISSING", "That goal is not in your scope."));
        StaffMember member = requireStaff(row.getStaffId());
        row.setStatus("DONE");
        audit.record("GOAL_COMPLETED", "STAFF", member.getId(), row.getTitle());
        audit.activity("STAFF", "Goal completed", member.getFullName() + " · " + row.getTitle(), member.getFarmId());
        return goalView(row, member);
    }

    @Transactional
    public Workforce.CompetencyView saveCompetency(Workforce.CompetencyRequest body) {
        access.requireAuthority("STAFF_WRITE");
        StaffMember member = requireStaff(body.staffId());
        String skill = required(body.skill(), 80, "A skill is required");
        String level = body.level() == null ? "" : body.level().trim().toUpperCase(Locale.ROOT);
        if (!LEVELS.contains(level)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LEVEL_INVALID", "Mark the skill as met or a gap");
        }
        StaffCompetency row = competencies.findByTenantIdAndStaffIdAndSkillIgnoreCase(access.tenantId(), member.getId(), skill).orElseGet(StaffCompetency::new);
        row.setTenantId(access.tenantId());
        row.setStaffId(member.getId());
        row.setFarmId(member.getFarmId());
        row.setSkill(skill);
        row.setLevel(level);
        competencies.save(row);
        audit.record("COMPETENCY_RECORDED", "STAFF", member.getId(), skill);
        audit.activity("STAFF", "Competency recorded", member.getFullName() + " · " + skill, member.getFarmId());
        return new Workforce.CompetencyView(row.getId(), member.getId(), member.getFullName(), member.getFarmId(), farmName(member.getFarmId()), row.getSkill(), row.getLevel());
    }

    private List<Workforce.AttendanceView> attendanceViews() {
        return attendance.findByTenantIdOrderByWorkDateDesc(access.tenantId()).stream()
                .filter(row -> access.canReadFarm(row.getFarmId()))
                .map(row -> new Workforce.AttendanceView(row.getId(), row.getStaffId(), staffName(row.getStaffId()), row.getFarmId(), farmName(row.getFarmId()), row.getWorkDate(), row.getMark()))
                .toList();
    }

    private List<Workforce.ShiftView> shiftViews() {
        return shifts.findByTenantIdOrderByWeekdayAsc(access.tenantId()).stream()
                .filter(row -> access.canReadFarm(row.getFarmId()))
                .map(row -> new Workforce.ShiftView(row.getId(), row.getStaffId(), staffName(row.getStaffId()), row.getFarmId(), farmName(row.getFarmId()), row.getWeekday(), row.getStarts(), row.getEnds()))
                .toList();
    }

    private List<Workforce.TimesheetView> timesheetViews() {
        return timesheets.findByTenantIdOrderByWorkDateDesc(access.tenantId()).stream()
                .filter(row -> access.canReadFarm(row.getFarmId()))
                .map(row -> new Workforce.TimesheetView(row.getId(), row.getStaffId(), staffName(row.getStaffId()), row.getFarmId(), farmName(row.getFarmId()), row.getWorkDate(), row.getHours(), row.getNote()))
                .toList();
    }

    private List<Workforce.ReviewView> reviewViews() {
        return reviews.findByTenantIdOrderByReviewedOnDesc(access.tenantId()).stream()
                .filter(row -> access.canReadFarm(row.getFarmId()))
                .map(row -> new Workforce.ReviewView(row.getId(), row.getStaffId(), staffName(row.getStaffId()), row.getFarmId(), farmName(row.getFarmId()), row.getScore(), row.getReviewedOn(), row.getNotes(), row.getReviewerName()))
                .toList();
    }

    private List<Workforce.TrainingView> trainingViews() {
        return training.findByTenantIdOrderByCreatedAtDesc(access.tenantId()).stream()
                .filter(row -> access.canReadFarm(row.getFarmId()))
                .map(row -> new Workforce.TrainingView(row.getId(), row.getStaffId(), staffName(row.getStaffId()), row.getFarmId(), farmName(row.getFarmId()), row.getTitle(), row.getCompletedOn()))
                .toList();
    }

    private List<Workforce.GoalView> goalViews() {
        return goals.findByTenantIdOrderByCreatedAtDesc(access.tenantId()).stream()
                .filter(row -> access.canReadFarm(row.getFarmId()))
                .map(row -> new Workforce.GoalView(row.getId(), row.getStaffId(), staffName(row.getStaffId()), row.getFarmId(), farmName(row.getFarmId()), row.getTitle(), row.getStatus()))
                .toList();
    }

    private List<Workforce.CompetencyView> competencyViews() {
        return competencies.findByTenantIdOrderBySkillAsc(access.tenantId()).stream()
                .filter(row -> access.canReadFarm(row.getFarmId()))
                .map(row -> new Workforce.CompetencyView(row.getId(), row.getStaffId(), staffName(row.getStaffId()), row.getFarmId(), farmName(row.getFarmId()), row.getSkill(), row.getLevel()))
                .toList();
    }

    private Workforce.FarmerRequestView view(FarmerRequest row) {
        return new Workforce.FarmerRequestView(row.getId(), row.getFarmId(), farmName(row.getFarmId()), row.getAuthorName(), row.getSubject(), row.getMessage(), row.getStatus(), row.getReply(), row.getRepliedBy(), row.getCreatedAt(), row.getAnsweredAt());
    }

    private Workforce.ShiftView shiftView(StaffShift row, StaffMember member) {
        return new Workforce.ShiftView(row.getId(), member.getId(), member.getFullName(), member.getFarmId(), farmName(member.getFarmId()), row.getWeekday(), row.getStarts(), row.getEnds());
    }

    private Workforce.GoalView goalView(StaffGoal row, StaffMember member) {
        return new Workforce.GoalView(row.getId(), member.getId(), member.getFullName(), member.getFarmId(), farmName(member.getFarmId()), row.getTitle(), row.getStatus());
    }

    private StaffMember requireStaff(UUID id) {
        if (id == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "STAFF_REQUIRED", "Choose a person");
        }
        StaffMember member = staff.findById(id).filter(item -> item.getTenantId().equals(access.tenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STAFF_MISSING", "That staff record is not in your scope."));
        access.checkFarm(member.getFarmId());
        return member;
    }

    private String staffName(UUID id) {
        return staff.findById(id).map(StaffMember::getFullName).orElse("Unknown");
    }

    private String farmName(UUID id) {
        return farms.load(id).getName();
    }

    private String required(String value, int max, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIELD_REQUIRED", message);
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIELD_TOO_LONG", message);
        }
        return trimmed;
    }

    private String blank(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
