import 'package:flutter/material.dart';

import '../../core/theme/strix_brand.dart';

/// Hata mesajı — stack trace göstermez.
class ErrorBanner extends StatelessWidget {
  final String message;
  final String? actionLabel;
  final VoidCallback? onAction;

  const ErrorBanner({
    super.key,
    required this.message,
    this.actionLabel,
    this.onAction,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: StrixBrand.critical.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
        border: Border.all(color: StrixBrand.critical.withValues(alpha: 0.35)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            message,
            style: const TextStyle(
              color: StrixBrand.textPrimary,
              height: 1.35,
            ),
            textAlign: TextAlign.center,
          ),
          if (actionLabel != null && onAction != null) ...[
            const SizedBox(height: 8),
            TextButton(
              onPressed: onAction,
              style: TextButton.styleFrom(foregroundColor: StrixBrand.primary),
              child: Text(actionLabel!),
            ),
          ],
        ],
      ),
    );
  }
}
