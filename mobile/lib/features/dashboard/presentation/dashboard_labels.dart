import 'package:flutter/material.dart';

import '../../../core/theme/strix_brand.dart';

String dashboardTypeLabel(String? raw) {
  if (raw == null || raw.isEmpty) {
    return '—';
  }
  switch (raw) {
    case 'MISSING_WELDING_MASK':
      return 'Kaynak maskesi';
    case 'MISSING_GLOVES':
      return 'Eldiven';
    case 'MISSING_WELDING_APRON':
      return 'Kaynak önlüğü';
    case 'RESTRICTED_ZONE':
      return 'Yasak alan';
    case 'UNPROTECTED_PERSON':
      return 'Korumasız kişi';
    default:
      return raw;
  }
}

String dashboardStatusLabel(String? raw) {
  if (raw == null || raw.isEmpty) {
    return '—';
  }
  switch (raw) {
    case 'ACTIVE':
      return 'Aktif';
    case 'PREPARING':
      return 'Hazırlanıyor';
    case 'COMPLETED':
      return 'Tamamlandı';
    case 'ERROR':
      return 'Hata';
    case 'UNREVIEWED':
      return 'İncelenmedi';
    case 'REVIEWED':
      return 'İncelendi';
    case 'CONFIRMED':
      return 'Onaylandı';
    case 'FALSE_ALARM':
      return 'Yanlış alarm';
    case 'REQUESTED':
    case 'PENDING':
      return 'Kayıt bekliyor';
    case 'RECORDING':
      return 'Kaydediliyor';
    case 'PROCESSING':
      return 'İşleniyor';
    case 'READY':
      return 'Hazır';
    default:
      return raw;
  }
}

String formatLocalDateTime(DateTime? value) {
  if (value == null) {
    return '—';
  }
  final local = value.toLocal();
  final y = local.year.toString().padLeft(4, '0');
  final m = local.month.toString().padLeft(2, '0');
  final d = local.day.toString().padLeft(2, '0');
  final hh = local.hour.toString().padLeft(2, '0');
  final mm = local.minute.toString().padLeft(2, '0');
  return '$d.$m.$y $hh:$mm';
}

String formatLocalShortDate(DateTime value) {
  final local = value.toLocal();
  return '${local.day.toString().padLeft(2, '0')}.${local.month.toString().padLeft(2, '0')}';
}

Color dashboardAccentForCount(int count) {
  if (count <= 0) {
    return StrixBrand.success;
  }
  if (count < 5) {
    return StrixBrand.warning;
  }
  return StrixBrand.critical;
}
