import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../../../core/models/user_summary.dart';
import '../../../../core/theme/strix_brand.dart';
import '../../models/user_display.dart';

class UserCard extends StatelessWidget {
  final UserSummary user;
  final bool canManage;
  final VoidCallback? onEdit;
  final VoidCallback? onDeactivate;
  final ValueChanged<bool>? onActiveChanged;

  const UserCard({
    super.key,
    required this.user,
    required this.canManage,
    this.onEdit,
    this.onDeactivate,
    this.onActiveChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Material(
        color: StrixBrand.surface,
        borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
        child: Container(
          padding: const EdgeInsets.fromLTRB(14, 14, 14, 12),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
            border: Border.all(color: StrixBrand.border),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      user.fullName.isEmpty ? user.email : user.fullName,
                      style: GoogleFonts.inter(
                        fontWeight: FontWeight.w600,
                        fontSize: 15,
                      ),
                    ),
                  ),
                  _ActiveChip(active: user.active),
                ],
              ),
              const SizedBox(height: 4),
              Text(
                user.email,
                style: GoogleFonts.inter(
                  fontSize: 13,
                  color: StrixBrand.textSecondary,
                ),
              ),
              const SizedBox(height: 6),
              Text(
                user.roleSummary,
                style: GoogleFonts.inter(fontSize: 13),
              ),
              Text(
                user.departmentSummary,
                style: GoogleFonts.inter(
                  fontSize: 12,
                  color: StrixBrand.textSecondary,
                ),
              ),
              if (canManage) ...[
                const SizedBox(height: 8),
                Row(
                  children: [
                    if (onActiveChanged != null)
                      Switch.adaptive(
                        value: user.active,
                        onChanged: onActiveChanged,
                      ),
                    if (onEdit != null)
                      IconButton(
                        tooltip: 'Düzenle',
                        onPressed: onEdit,
                        icon: const Icon(Icons.edit_outlined, size: 20),
                        visualDensity: VisualDensity.compact,
                      ),
                    if (onDeactivate != null && user.active)
                      TextButton(
                        onPressed: onDeactivate,
                        child: const Text('Pasifleştir'),
                      ),
                  ],
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _ActiveChip extends StatelessWidget {
  final bool active;

  const _ActiveChip({required this.active});

  @override
  Widget build(BuildContext context) {
    final color = active ? StrixBrand.success : StrixBrand.critical;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        active ? 'Aktif' : 'Pasif',
        style: GoogleFonts.inter(
          fontSize: 11,
          fontWeight: FontWeight.w600,
          color: color,
        ),
      ),
    );
  }
}
