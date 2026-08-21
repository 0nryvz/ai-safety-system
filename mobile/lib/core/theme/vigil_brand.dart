import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

/// VIGIL marka kimliği — endüstriyel, keskin, satışa uygun.
class VigilBrand {
  const VigilBrand._();

  static const String name = 'VIGIL';
  static const String tagline = 'Fabrika kamerası operasyonları';
  static const String pitch =
      'Telefonu rastgele webcam yapmaz. Atanmış bir fabrika kamerasını '
      'simüle eder, görüntüyü Camera Ingestion Gateway\'e güvenilir biçimde '
      'aktarır.';

  static const Color ink = Color(0xFF0B1220);
  static const Color panel = Color(0xFF121A2A);
  static const Color panelElevated = Color(0xFF182235);
  static const Color teal = Color(0xFF1EC8B0);
  static const Color amber = Color(0xFFF5A524);
  static const Color steel = Color(0xFF8B97A8);
  static const Color danger = Color(0xFFE85D5D);
  static const Color success = Color(0xFF3DDC97);

  static ThemeData theme() {
    final base = ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: ink,
      colorScheme: const ColorScheme.dark(
        primary: teal,
        secondary: amber,
        surface: panel,
        error: danger,
        onPrimary: ink,
        onSecondary: ink,
        onSurface: Colors.white,
        onError: Colors.white,
      ),
    );

    return base.copyWith(
      textTheme: GoogleFonts.spaceGroteskTextTheme(base.textTheme).apply(
        bodyColor: Colors.white,
        displayColor: Colors.white,
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: ink,
        foregroundColor: Colors.white,
        elevation: 0,
        centerTitle: false,
        titleTextStyle: GoogleFonts.spaceGrotesk(
          fontSize: 20,
          fontWeight: FontWeight.w700,
          letterSpacing: 1.2,
          color: Colors.white,
        ),
      ),
      cardTheme: CardThemeData(
        color: panelElevated,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: BorderSide(color: Colors.white.withValues(alpha: 0.06)),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: panel,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: Colors.white.withValues(alpha: 0.12)),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: Colors.white.withValues(alpha: 0.12)),
        ),
        focusedBorder: const OutlineInputBorder(
          borderRadius: BorderRadius.all(Radius.circular(12)),
          borderSide: BorderSide(color: teal, width: 1.4),
        ),
        labelStyle: const TextStyle(color: steel),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: teal,
          foregroundColor: ink,
          textStyle: GoogleFonts.spaceGrotesk(
            fontWeight: FontWeight.w700,
            fontSize: 15,
          ),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
          ),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: Colors.white,
          side: BorderSide(color: Colors.white.withValues(alpha: 0.2)),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
          ),
        ),
      ),
    );
  }
}
