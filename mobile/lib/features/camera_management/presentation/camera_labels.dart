String formatCameraLastSeen(DateTime? value) {
  if (value == null) {
    return 'Son görülme: —';
  }
  final local = value.toLocal();
  final d = local.day.toString().padLeft(2, '0');
  final m = local.month.toString().padLeft(2, '0');
  final y = local.year.toString().padLeft(4, '0');
  final hh = local.hour.toString().padLeft(2, '0');
  final mm = local.minute.toString().padLeft(2, '0');
  return 'Son görülme: $d.$m.$y $hh:$mm';
}
