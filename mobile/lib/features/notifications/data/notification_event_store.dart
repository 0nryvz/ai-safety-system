import 'dart:async';

import '../../../core/realtime/realtime_event.dart';
import 'notification_item.dart';

/// Realtime eventlerinin duplicate-safe deposu.
///
/// Backend sözleşmesinde `eventId`/`version` olmadığı için dedupe, gerçek DTO
/// alanlarından üretilen deterministic fingerprint ile yapılır. Socket
/// source-of-truth değildir; REST recovery bu state'i tazeler.
class NotificationEventStore {
  final int maxFingerprints;

  NotificationEventStore({this.maxFingerprints = 500});

  final Map<String, NotificationItem> _items = {};
  final List<String> _order = [];
  final Set<String> _seenFingerprints = {};
  final List<String> _fingerprintOrder = [];
  final StreamController<List<NotificationItem>> _changes =
      StreamController<List<NotificationItem>>.broadcast();

  /// En yeni event en başta.
  List<NotificationItem> get items => List<NotificationItem>.unmodifiable(
        _order.map((id) => _items[id]!),
      );

  Stream<List<NotificationItem>> get changes => _changes.stream;

  int get length => _items.length;

  NotificationItem? itemFor(String violationId) => _items[violationId];

  /// Malformed event store'u bozmaz; duplicate event yeni item üretmez.
  /// Uygulandıysa `true` döner.
  bool apply(RealtimeEvent event) {
    switch (event) {
      case RealtimeParseFailure():
        return false;
      case RealtimeAlertEvent(:final message):
        if (!_markSeen(event.fingerprint)) {
          return false;
        }
        return _upsert(
          violationId: message.violationId,
          eventAt: message.startedAt,
          build: (existing) {
            if (existing == null) {
              return NotificationItem(
                violationId: message.violationId,
                type: message.type,
                cameraName: message.cameraName,
                departmentName: message.departmentName,
                startedAt: message.startedAt,
                confidence: message.confidence,
                lifecycleStatus: message.lifecycleStatus,
                recordingStatus: message.recordingStatus,
                clipReady: message.clipReady,
                coverImageReady: message.coverImageReady,
                lastEventAt: message.startedAt,
              );
            }
            return existing.copyWith(
              type: message.type,
              cameraName: message.cameraName,
              departmentName: message.departmentName,
              startedAt: message.startedAt,
              confidence: message.confidence,
              lifecycleStatus: message.lifecycleStatus,
              recordingStatus: message.recordingStatus,
              clipReady: message.clipReady,
              coverImageReady: message.coverImageReady,
              lastEventAt: message.startedAt,
            );
          },
        );
      case RealtimeViolationUpdateEvent(:final message):
        if (!_markSeen(event.fingerprint)) {
          return false;
        }
        return _upsert(
          violationId: message.violationId,
          eventAt: message.updatedAt,
          build: (existing) {
            if (existing == null) {
              // Alert kaçırılmış olabilir; REST recovery detayları tamamlar.
              return NotificationItem(
                violationId: message.violationId,
                lifecycleStatus: message.lifecycleStatus,
                recordingStatus: message.recordingStatus,
                clipReady: message.clipReady,
                coverImageReady: false,
                errorCode: message.errorCode,
                lastEventAt: message.updatedAt,
              );
            }
            return existing.copyWith(
              lifecycleStatus: message.lifecycleStatus,
              recordingStatus: message.recordingStatus,
              clipReady: message.clipReady,
              errorCode: message.errorCode,
              lastEventAt: message.updatedAt,
            );
          },
        );
    }
  }

  void clear() {
    _items.clear();
    _order.clear();
    _seenFingerprints.clear();
    _fingerprintOrder.clear();
    _emit();
  }

  Future<void> dispose() => _changes.close();

  bool _upsert({
    required String violationId,
    required DateTime eventAt,
    required NotificationItem Function(NotificationItem? existing) build,
  }) {
    final existing = _items[violationId];

    // Stale event daha yeni state'i geriye götürmez.
    if (existing != null && eventAt.isBefore(existing.lastEventAt)) {
      return false;
    }

    _items[violationId] = build(existing);
    _order.remove(violationId);
    _order.insert(0, violationId);
    _emit();
    return true;
  }

  bool _markSeen(String fingerprint) {
    if (!_seenFingerprints.add(fingerprint)) {
      return false;
    }

    _fingerprintOrder.add(fingerprint);
    if (_fingerprintOrder.length > maxFingerprints) {
      _seenFingerprints.remove(_fingerprintOrder.removeAt(0));
    }
    return true;
  }

  void _emit() {
    if (!_changes.isClosed) {
      _changes.add(items);
    }
  }
}
