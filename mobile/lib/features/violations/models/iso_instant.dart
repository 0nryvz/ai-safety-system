/// Backend `Instant` query/parse — ISO-8601 UTC (`2026-08-22T00:00:00Z`).
String formatIsoInstant(DateTime value) {
  final utc = value.toUtc();
  String two(int n) => n.toString().padLeft(2, '0');
  return '${utc.year.toString().padLeft(4, '0')}-'
      '${two(utc.month)}-'
      '${two(utc.day)}T'
      '${two(utc.hour)}:'
      '${two(utc.minute)}:'
      '${two(utc.second)}Z';
}

DateTime startOfLocalDayUtc(DateTime localDate) {
  return DateTime(localDate.year, localDate.month, localDate.day).toUtc();
}

DateTime endOfLocalDayUtc(DateTime localDate) {
  return DateTime(
    localDate.year,
    localDate.month,
    localDate.day,
    23,
    59,
    59,
  ).toUtc();
}

DateTime? parseInstant(Object? value) {
  if (value is! String || value.isEmpty) {
    return null;
  }
  return DateTime.tryParse(value)?.toUtc();
}
