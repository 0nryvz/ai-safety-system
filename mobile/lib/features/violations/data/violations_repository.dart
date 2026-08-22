import '../models/violation_detail.dart';
import '../models/violation_filter_option.dart';
import '../models/violation_filters.dart';
import '../models/violation_page.dart';
import '../models/violation_review_status.dart';
import 'violations_api.dart';

abstract class ViolationsPort {
  Future<ViolationPage> loadPage({
    ViolationFilters filters = ViolationFilters.empty,
    int page = 0,
  });

  Future<ViolationDetail> loadDetail(String id);

  Future<void> submitReview({
    required String id,
    required ViolationReviewStatus reviewStatus,
    required int version,
  });

  Future<List<ViolationFilterOption>> loadCameras();

  Future<List<ViolationFilterOption>> loadDepartments();
}

class ViolationsRepository implements ViolationsPort {
  ViolationsRepository({required this._api});

  final ViolationsApi _api;

  @override
  Future<ViolationPage> loadPage({
    ViolationFilters filters = ViolationFilters.empty,
    int page = 0,
  }) =>
      _api.fetchViolations(filters: filters, page: page);

  @override
  Future<ViolationDetail> loadDetail(String id) => _api.fetchDetail(id);

  @override
  Future<void> submitReview({
    required String id,
    required ViolationReviewStatus reviewStatus,
    required int version,
  }) =>
      _api.reviewViolation(
        id: id,
        reviewStatus: reviewStatus,
        version: version,
      );

  @override
  Future<List<ViolationFilterOption>> loadCameras() => _api.fetchCameras();

  @override
  Future<List<ViolationFilterOption>> loadDepartments() =>
      _api.fetchDepartments();
}
