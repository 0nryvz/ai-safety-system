import 'dart:convert';

import '../models/alert_message.dart';
import '../models/violation_update_message.dart';
import 'realtime_event.dart';

/// `/user/queue/alerts` üzerinden iki farklı payload şekli gelir ve backendde
/// discriminator alanı yoktur:
/// - `AlertMessage`: `type` + `startedAt` içerir
/// - `ViolationUpdateMessage`: `updatedAt` içerir
///
/// Bilinmeyen/bozuk frame [RealtimeParseFailure] döner; exception sızdırmaz.
RealtimeEvent parseRealtimeFrame(String? body) {
  if (body == null || body.trim().isEmpty) {
    return const RealtimeParseFailure('empty body');
  }

  final Object? decoded;
  try {
    decoded = jsonDecode(body);
  } catch (_) {
    return RealtimeParseFailure('invalid json', rawBody: body);
  }

  if (decoded is! Map<String, dynamic>) {
    return RealtimeParseFailure('unexpected payload shape', rawBody: body);
  }

  try {
    if (decoded.containsKey('updatedAt')) {
      return RealtimeViolationUpdateEvent(
        ViolationUpdateMessage.fromJson(decoded),
      );
    }

    if (decoded.containsKey('type') && decoded.containsKey('startedAt')) {
      return RealtimeAlertEvent(AlertMessage.fromJson(decoded));
    }

    return RealtimeParseFailure('unknown message shape', rawBody: body);
  } on FormatException catch (e) {
    return RealtimeParseFailure(e.message, rawBody: body);
  } catch (e) {
    return RealtimeParseFailure('malformed message: $e', rawBody: body);
  }
}
