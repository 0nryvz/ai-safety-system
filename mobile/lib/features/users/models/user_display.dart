import '../../../core/models/user_summary.dart';
import 'user_role.dart';

extension UserSummaryRoles on UserSummary {
  List<UserRole> get parsedRoles => parseUserRoles(roles);

  String get roleSummary {
    final labels = parsedRoles.map((role) => role.label).toList();
    if (labels.isEmpty) {
      return 'Rol yok';
    }
    return labels.join(' • ');
  }

  String get departmentSummary {
    if (departmentName != null && departmentName!.isNotEmpty) {
      return departmentName!;
    }
    if (departmentIds.isEmpty) {
      return 'Departman yok';
    }
    return '${departmentIds.length} departman';
  }
}
