/// Backend `AlertMessage` (STOMP `/user/queue/alerts`).
///
/// Alanlar backend record'u ile birebirdir; `eventId`/`version` sözleşmede
/// yoktur ve uydurulmaz.
class AlertMessage {
  final String violationId;
  final String type;
  final String cameraName;
  final String departmentName;
  final DateTime startedAt;
  final double confidence;
  final String lifecycleStatus;
  final String recordingStatus;
  final bool clipReady;
  final bool coverImageReady;

  const AlertMessage({
    required this.violationId,
    required this.type,
    required this.cameraName,
    required this.departmentName,
    required this.startedAt,
    required this.confidence,
    required this.lifecycleStatus,
    required this.recordingStatus,
    required this.clipReady,
    required this.coverImageReady,
  });

  /// Zorunlu alan eksik/bozuksa [FormatException] atar; çağıran taraf bunu
  /// güvenli parse failure'a çevirir.
  factory AlertMessage.fromJson(Map<String, dynamic> json) {
    return AlertMessage(
      violationId: _requiredString(json['violationId'], 'violationId'),
      type: _requiredString(json['type'], 'type'),
      cameraName: _requiredString(json['cameraName'], 'cameraName'),
      departmentName: _requiredString(json['departmentName'], 'departmentName'),
      startedAt: _requiredInstant(json['startedAt'], 'startedAt'),
      confidence: _requiredDouble(json['confidence'], 'confidence'),
      lifecycleStatus:
          _requiredString(json['lifecycleStatus'], 'lifecycleStatus'),
      recordingStatus:
          _requiredString(json['recordingStatus'], 'recordingStatus'),
      clipReady: _requiredBool(json['clipReady'], 'clipReady'),
      coverImageReady:
          _requiredBool(json['coverImageReady'], 'coverImageReady'),
    );
  }

  static String _requiredString(Object? value, String field) {
    if (value is String && value.isNotEmpty) {
      return value;
    }
    throw FormatException('$field missing');
  }

  static DateTime _requiredInstant(Object? value, String field) {
    if (value is String) {
      final parsed = DateTime.tryParse(value);
      if (parsed != null) {
        return parsed;
      }
    }
    throw FormatException('$field missing');
  }

  static double _requiredDouble(Object? value, String field) {
    if (value is num) {
      return value.toDouble();
    }
    throw FormatException('$field missing');
  }

  static bool _requiredBool(Object? value, String field) {
    if (value is bool) {
      return value;
    }
    throw FormatException('$field missing');
  }
}
