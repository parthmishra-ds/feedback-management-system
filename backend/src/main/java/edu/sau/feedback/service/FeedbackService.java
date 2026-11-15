package edu.sau.feedback.service;

import edu.sau.feedback.dto.Dtos.*;
import edu.sau.feedback.entity.*;
import edu.sau.feedback.repository.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Business logic. The three main transactions from the presentation live here:
 *   submit()       -> 1 row in feedback + its first history row
 *   changeStatus() -> update feedback + history row with old and new status
 *   addResponse()  -> management reply stored against that feedback
 * Each is @Transactional: either every statement commits, or none does (Atomicity).
 */
@Service
@Transactional(readOnly = true)
public class FeedbackService {

    private static final List<Status> PENDING = List.of(Status.SUBMITTED, Status.UNDER_REVIEW);

    private final FeedbackRepository feedbacks;
    private final CategoryRepository categories;
    private final UserRepository users;
    private final StatusHistoryRepository history;
    private final ResponseRepository responses;
    private final AdminFeedbackViewRepository adminView;
    private final CategorySummaryRepository summary;
    private final NotificationService alerts;

    public FeedbackService(FeedbackRepository feedbacks, CategoryRepository categories, UserRepository users,
                           StatusHistoryRepository history, ResponseRepository responses,
                           AdminFeedbackViewRepository adminView, CategorySummaryRepository summary,
                           NotificationService alerts) {
        this.feedbacks = feedbacks;
        this.categories = categories;
        this.users = users;
        this.history = history;
        this.responses = responses;
        this.adminView = adminView;
        this.summary = summary;
        this.alerts = alerts;
    }

    // ===================== categories =====================

    public List<CategoryInfo> listCategories() {
        return categories.findAllByOrderByCategoryNameAsc().stream()
                .map(c -> new CategoryInfo(c.getCategoryId(), c.getCategoryName(), c.getDescription())).toList();
    }

    // ===================== transaction 1: submit =====================

    @Transactional
    public FeedbackDetail submit(Long userId, FeedbackRequest req) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        FeedbackCategory cat = categories.findById(req.categoryId())
                .orElseThrow(() -> ApiException.badRequest("Unknown category"));

        Feedback f = new Feedback();
        f.setUser(user);
        f.setCategory(cat);
        f.setRating(req.rating());
        f.setComments(blankToNull(req.comments()));
        f.setAnonymous(req.anonymous());
        f.setStatus(Status.SUBMITTED);
        feedbacks.saveAndFlush(f);

        StatusHistory h = new StatusHistory();
        h.setFeedback(f);
        h.setOldStatus(null);
        h.setNewStatus(Status.SUBMITTED);
        h.setChangedBy(req.anonymous() ? null : user);   // anonymous: audit trail must not reveal the user
        h.setNote("Feedback submitted");
        history.saveAndFlush(h);
        alerts.newFeedback(f);

        return ownerDetail(f);
    }

    // ===================== normal user: own feedback =====================

    public List<FeedbackSummary> listMine(Long userId) {
        return feedbacks.findMine(userId).stream()
                .map(f -> new FeedbackSummary(f.getFeedbackId(), f.getCategory().getCategoryName(), f.getRating(),
                        f.getComments(), f.getStatus(), f.isAnonymous(), f.getCreatedAt(), null))
                .toList();
    }

    /** A user may open only their own feedback; admins use adminDetail(). */
    public FeedbackDetail myDetail(Long userId, Long feedbackId) {
        Feedback f = feedbacks.findWithCategory(feedbackId).orElseThrow(() -> ApiException.notFound("Feedback not found"));
        if (!f.getUser().getUserId().equals(userId)) throw ApiException.notFound("Feedback not found"); // do not reveal existence
        return ownerDetail(f);
    }

    // ===================== management =====================

    public PageDto<FeedbackSummary> adminList(Status status, Long categoryId, boolean pendingOnly, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);

        Specification<AdminFeedbackView> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (pendingOnly) p.add(root.get("status").in(PENDING));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (categoryId != null) p.add(cb.equal(root.get("categoryId"), categoryId));
            return cb.and(p.toArray(new Predicate[0]));
        };
        // pending work list: oldest first so old complaints are not forgotten; otherwise latest first
        Sort sort = pendingOnly ? Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("feedbackId"))
                                : Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("feedbackId"));
        var result = adminView.findAll(spec, PageRequest.of(safePage, safeSize, sort));
        List<FeedbackSummary> items = result.getContent().stream()
                .map(v -> new FeedbackSummary(v.getFeedbackId(), v.getCategoryName(), v.getRating(), v.getComments(),
                        v.getStatus(), v.isAnonymous(), v.getCreatedAt(), v.getSubmitterName()))
                .toList();
        return new PageDto<>(items, result.getTotalElements(), safePage, safeSize);
    }

    public FeedbackDetail adminDetail(Long feedbackId) {
        AdminFeedbackView v = adminView.findById(feedbackId).orElseThrow(() -> ApiException.notFound("Feedback not found"));
        return new FeedbackDetail(v.getFeedbackId(), v.getCategoryId(), v.getCategoryName(), v.getRating(), v.getComments(),
                v.getStatus(), v.isAnonymous(), v.getCreatedAt(), v.getSubmitterName(), v.getSubmitterRole(),
                v.getSubmitterDepartment(), trail(feedbackId), replies(feedbackId));
    }

    public Dashboard dashboard() {
        long total = feedbacks.count();
        Double avg = feedbacks.averageRating();
        Map<Status, Long> byStatus = new EnumMap<>(Status.class);
        for (Status s : Status.values()) byStatus.put(s, 0L);
        for (Object[] row : feedbacks.countByStatus()) byStatus.put((Status) row[0], (Long) row[1]);

        // worst-rated areas first (this is what tells management where to act); unrated categories last
        List<CategoryStat> cats = summary.findAll().stream()
                .map(c -> new CategoryStat(c.getCategoryId(), c.getCategoryName(), c.getTotal(), c.getAvgRating()))
                .sorted(Comparator.comparing(CategoryStat::avgRating, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CategoryStat::categoryName))
                .toList();
        long pending = byStatus.get(Status.SUBMITTED) + byStatus.get(Status.UNDER_REVIEW);
        return new Dashboard(total, avg == null ? null : Math.round(avg * 100.0) / 100.0, pending, byStatus, cats);
    }

    // ===================== transaction 2: change status =====================

    @Transactional
    public FeedbackDetail changeStatus(Long adminId, Long feedbackId, StatusChangeRequest req) {
        Feedback f = feedbacks.lockById(feedbackId).orElseThrow(() -> ApiException.notFound("Feedback not found"));
        Status old = f.getStatus();
        Status next = req.newStatus();
        if (old == next) throw ApiException.conflict("Feedback is already " + old);
        if (!old.canMoveTo(next)) throw ApiException.conflict("Status cannot move backwards from " + old + " to " + next);

        User admin = users.findById(adminId).orElseThrow(() -> ApiException.notFound("Admin not found"));
        f.setStatus(next);
        feedbacks.saveAndFlush(f);

        StatusHistory h = new StatusHistory();
        h.setFeedback(f);
        h.setOldStatus(old);
        h.setNewStatus(next);
        h.setChangedBy(admin);
        h.setNote(blankToNull(req.note()));
        history.saveAndFlush(h);
        alerts.statusChanged(f, next, req.note());
        return adminDetail(feedbackId);
    }

    // ===================== transaction 3: add response =====================

    @Transactional
    public FeedbackDetail addResponse(Long adminId, Long feedbackId, ResponseRequest req) {
        Feedback f = feedbacks.findById(feedbackId).orElseThrow(() -> ApiException.notFound("Feedback not found"));
        User admin = users.findById(adminId).orElseThrow(() -> ApiException.notFound("Admin not found"));
        FeedbackResponse r = new FeedbackResponse();
        r.setFeedback(f);
        r.setAdmin(admin);
        r.setResponseText(req.responseText().trim());
        responses.saveAndFlush(r);
        alerts.replied(f, r.getResponseText());
        return adminDetail(feedbackId);
    }

    // ===================== helpers =====================

    private FeedbackDetail ownerDetail(Feedback f) {
        return new FeedbackDetail(f.getFeedbackId(), f.getCategory().getCategoryId(), f.getCategory().getCategoryName(),
                f.getRating(), f.getComments(), f.getStatus(), f.isAnonymous(), f.getCreatedAt(),
                null, null, null, trail(f.getFeedbackId()), replies(f.getFeedbackId()));
    }

    private List<HistoryItem> trail(Long feedbackId) {
        return history.findTrail(feedbackId).stream()
                .map(h -> new HistoryItem(h.getOldStatus(), h.getNewStatus(), h.getNote(), h.getChangedAt(),
                        h.getChangedBy() == null ? "System" : h.getChangedBy().getFullName()))
                .toList();
    }

    private List<ResponseItem> replies(Long feedbackId) {
        return responses.findReplies(feedbackId).stream()
                .map(r -> new ResponseItem(r.getResponseId(), r.getAdmin().getFullName(), r.getResponseText(), r.getCreatedAt()))
                .toList();
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
