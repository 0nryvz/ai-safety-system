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
      return 'Bilinmiyor';
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
      return 'Bilinmiyor';
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

IconData dashboardTypeIcon(String? raw) {
  switch (raw) {
    case 'MISSING_WELDING_MASK':
      return Icons.sports_motorsports_outlined;
    case 'MISSING_GLOVES':
      return Icons.back_hand_outlined;
    case 'MISSING_WELDING_APRON':
      return Icons.checkroom_outlined;
    case 'RESTRICTED_ZONE':
      return Icons.gpp_maybe_outlined;
    case 'UNPROTECTED_PERSON':
      return Icons.person_off_outlined;
    default:
      return Icons.report_gmailerrorred_outlined;
  }
}

Color dashboardTypeColor(String? raw) {
  switch (raw) {
    case 'MISSING_WELDING_MASK':
      return const Color(0xFF7C3AED);
    case 'MISSING_GLOVES':
      return StrixBrand.primary;
    case 'MISSING_WELDING_APRON':
      return const Color(0xFF0F766E);
    case 'RESTRICTED_ZONE':
      return StrixBrand.critical;
    case 'UNPROTECTED_PERSON':
      return StrixBrand.warning;
    default:
      return StrixBrand.textSecondary;
  }
}

Color dashboardStatusColor(String? raw) {
  switch (raw) {
    case 'ACTIVE':
      return StrixBrand.critical;
    case 'COMPLETED':
    case 'READY':
    case 'CONFIRMED':
    case 'REVIEWED':
      return StrixBrand.success;
    case 'ERROR':
    case 'FALSE_ALARM':
      return StrixBrand.critical;
    case 'PREPARING':
    case 'PROCESSING':
    case 'RECORDING':
    case 'PENDING':
    case 'REQUESTED':
    case 'UNREVIEWED':
      return StrixBrand.warning;
    default:
      return StrixBrand.textSecondary;
  }
}

String dashboardWeekdayShort(DateTime value) {
  const labels = ['Pzt', 'Sal', 'Çar', 'Per', 'Cum', 'Cmt', 'Paz'];
  return labels[value.toLocal().weekday - 1];
}
