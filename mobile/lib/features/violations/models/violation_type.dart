/// Backend `ViolationType` — canonical 5 değer. Jacket uydurulmaz.
enum ViolationType {
  missingWeldingMask,
  missingGloves,
  missingWeldingApron,
  restrictedZone,
  unprotectedPerson,
  unknown;

  static const List<ViolationType> canonical = [
    ViolationType.missingWeldingMask,
    ViolationType.missingGloves,
    ViolationType.missingWeldingApron,
    ViolationType.restrictedZone,
    ViolationType.unprotectedPerson,
  ];

  static ViolationType fromJson(String? raw) {
    switch (raw?.toUpperCase()) {
      case 'MISSING_WELDING_MASK':
        return ViolationType.missingWeldingMask;
      case 'MISSING_GLOVES':
        return ViolationType.missingGloves;
      case 'MISSING_WELDING_APRON':
        return ViolationType.missingWeldingApron;
      case 'RESTRICTED_ZONE':
        return ViolationType.restrictedZone;
      case 'UNPROTECTED_PERSON':
        return ViolationType.unprotectedPerson;
      default:
        return ViolationType.unknown;
    }
  }

  String get wireValue => switch (this) {
        ViolationType.missingWeldingMask => 'MISSING_WELDING_MASK',
        ViolationType.missingGloves => 'MISSING_GLOVES',
        ViolationType.missingWeldingApron => 'MISSING_WELDING_APRON',
        ViolationType.restrictedZone => 'RESTRICTED_ZONE',
        ViolationType.unprotectedPerson => 'UNPROTECTED_PERSON',
        ViolationType.unknown => 'UNKNOWN',
      };
}
