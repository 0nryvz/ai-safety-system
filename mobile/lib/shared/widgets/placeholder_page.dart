import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../core/theme/strix_brand.dart';

/// Feature henüz yokken AppShell gövdesi.
class PlaceholderPage extends StatelessWidget {
  final String title;
  final String? subtitle;

  const PlaceholderPage({
    super.key,
    required this.title,
    this.subtitle,
  });

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              title,
              textAlign: TextAlign.center,
              style: GoogleFonts.inter(
                fontSize: 20,
                fontWeight: FontWeight.w700,
                color: StrixBrand.textPrimary,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              subtitle ?? 'Bu ekran sonraki görevde bağlanacak.',
              textAlign: TextAlign.center,
              style: GoogleFonts.inter(
                fontSize: 14,
                height: 1.4,
                color: StrixBrand.textSecondary,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
