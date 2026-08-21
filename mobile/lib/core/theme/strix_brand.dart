import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

/// STRIX — Design System Guide v2.0 (Current UI / light theme) token'ları.
class StrixBrand {
  const StrixBrand._();

  static const String name = 'STRIX';
  static const String shortName = 'STRIX';
  static const String tagline = 'Saha görüşü · operatör istemcisi';
  static const String pitch =
      'Atanmış fabrika kamerasını güvenilir biçimde Camera Ingestion '
      'Gateway\'e aktarır. Kurumsal saha operasyon paneli.';

  /// Logo asset.
  static const String logoAsset = 'assets/brand/strix_app_icon.png';

  // --- Design tokens (PDF v2.0) ---
  static const Color primary = Color(0xFF2563EA);
  static const Color critical = Color(0xFFEB2121);
  static const Color success = Color(0xFF15803D);
  static const Color successBackground = Color(0xFFDCFCE7);
  static const Color warning = Color(0xFFF59E0B);
  static const Color background = Color(0xFFF5F6F8);
  static const Color surface = Color(0xFFFFFFFF);
  static const Color surfaceSubtle = Color(0xFFF9FAFC);
  static const Color textPrimary = Color(0xFF1C1C1C);
  static const Color textSecondary = Color(0xFF64748B);
  static const Color border = Color(0xFFE5E7EB);

  // Geriye dönük alias'lar (eski ekran referansları).
  static const Color teal = primary;
  static const Color ink = background;
  static const Color panel = surfaceSubtle;
  static const Color panelElevated = surface;
  static const Color steel = textSecondary;
  static const Color danger = critical;
  static const Color amber = warning;

  static const double radiusInput = 12;
  static const double radiusButton = 12;
  static const double radiusCard = 16;

  static ThemeData theme() {
    final base = ThemeData(
      useMaterial3: true,
      brightness: Brightness.light,
      scaffoldBackgroundColor: background,
      colorScheme: const ColorScheme.light(
        primary: primary,
        secondary: warning,
        surface: surface,
        error: critical,
        onPrimary: Colors.white,
        onSecondary: textPrimary,
        onSurface: textPrimary,
        onError: Colors.white,
      ),
    );

    final textTheme = GoogleFonts.interTextTheme(base.textTheme).apply(
      bodyColor: textPrimary,
      displayColor: textPrimary,
    );

    return base.copyWith(
      textTheme: textTheme,
      appBarTheme: AppBarTheme(
        backgroundColor: surface,
        foregroundColor: textPrimary,
        elevation: 0,
        scrolledUnderElevation: 0.5,
        centerTitle: false,
        titleTextStyle: GoogleFonts.inter(
          fontSize: 20,
          fontWeight: FontWeight.w700,
          color: textPrimary,
        ),
        surfaceTintColor: Colors.transparent,
      ),
      cardTheme: CardThemeData(
        color: surface,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(radiusCard),
          side: const BorderSide(color: border),
        ),
      ),
      dividerTheme: const DividerThemeData(color: border, thickness: 1),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: surface,
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(radiusInput),
          borderSide: const BorderSide(color: border),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(radiusInput),
          borderSide: const BorderSide(color: border),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(radiusInput),
          borderSide: const BorderSide(color: primary, width: 1.5),
        ),
        labelStyle: const TextStyle(color: textSecondary),
        hintStyle: const TextStyle(color: textSecondary),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: primary,
          foregroundColor: Colors.white,
          minimumSize: const Size.fromHeight(48),
          textStyle: GoogleFonts.inter(
            fontWeight: FontWeight.w600,
            fontSize: 14,
          ),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(radiusButton),
          ),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: textPrimary,
          minimumSize: const Size.fromHeight(48),
          side: const BorderSide(color: border),
          textStyle: GoogleFonts.inter(
            fontWeight: FontWeight.w600,
            fontSize: 14,
          ),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(radiusButton),
          ),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: primary,
          textStyle: GoogleFonts.inter(fontWeight: FontWeight.w600),
        ),
      ),
    );
  }
}
