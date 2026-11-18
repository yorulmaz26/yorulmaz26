// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'app_database.dart';

// ignore_for_file: type=lint
class $UserProfilesTable extends UserProfiles
    with TableInfo<$UserProfilesTable, UserProfile> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $UserProfilesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(1),
  );
  static const VerificationMeta _nameMeta = const VerificationMeta('name');
  @override
  late final GeneratedColumn<String> name = GeneratedColumn<String>(
    'name',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant(''),
  );
  static const VerificationMeta _avatarPathMeta = const VerificationMeta(
    'avatarPath',
  );
  @override
  late final GeneratedColumn<String> avatarPath = GeneratedColumn<String>(
    'avatar_path',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _focusCategoriesMeta = const VerificationMeta(
    'focusCategories',
  );
  @override
  late final GeneratedColumn<String> focusCategories = GeneratedColumn<String>(
    'focus_categories',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant(''),
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    name,
    avatarPath,
    focusCategories,
    createdAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'user_profiles';
  @override
  VerificationContext validateIntegrity(
    Insertable<UserProfile> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('name')) {
      context.handle(
        _nameMeta,
        name.isAcceptableOrUnknown(data['name']!, _nameMeta),
      );
    }
    if (data.containsKey('avatar_path')) {
      context.handle(
        _avatarPathMeta,
        avatarPath.isAcceptableOrUnknown(data['avatar_path']!, _avatarPathMeta),
      );
    }
    if (data.containsKey('focus_categories')) {
      context.handle(
        _focusCategoriesMeta,
        focusCategories.isAcceptableOrUnknown(
          data['focus_categories']!,
          _focusCategoriesMeta,
        ),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  UserProfile map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return UserProfile(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      name: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}name'],
      )!,
      avatarPath: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}avatar_path'],
      ),
      focusCategories: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}focus_categories'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
    );
  }

  @override
  $UserProfilesTable createAlias(String alias) {
    return $UserProfilesTable(attachedDatabase, alias);
  }
}

class UserProfile extends DataClass implements Insertable<UserProfile> {
  final int id;
  final String name;
  final String? avatarPath;
  final String focusCategories;
  final DateTime createdAt;
  const UserProfile({
    required this.id,
    required this.name,
    this.avatarPath,
    required this.focusCategories,
    required this.createdAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['name'] = Variable<String>(name);
    if (!nullToAbsent || avatarPath != null) {
      map['avatar_path'] = Variable<String>(avatarPath);
    }
    map['focus_categories'] = Variable<String>(focusCategories);
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  UserProfilesCompanion toCompanion(bool nullToAbsent) {
    return UserProfilesCompanion(
      id: Value(id),
      name: Value(name),
      avatarPath: avatarPath == null && nullToAbsent
          ? const Value.absent()
          : Value(avatarPath),
      focusCategories: Value(focusCategories),
      createdAt: Value(createdAt),
    );
  }

  factory UserProfile.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return UserProfile(
      id: serializer.fromJson<int>(json['id']),
      name: serializer.fromJson<String>(json['name']),
      avatarPath: serializer.fromJson<String?>(json['avatarPath']),
      focusCategories: serializer.fromJson<String>(json['focusCategories']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'name': serializer.toJson<String>(name),
      'avatarPath': serializer.toJson<String?>(avatarPath),
      'focusCategories': serializer.toJson<String>(focusCategories),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  UserProfile copyWith({
    int? id,
    String? name,
    Value<String?> avatarPath = const Value.absent(),
    String? focusCategories,
    DateTime? createdAt,
  }) => UserProfile(
    id: id ?? this.id,
    name: name ?? this.name,
    avatarPath: avatarPath.present ? avatarPath.value : this.avatarPath,
    focusCategories: focusCategories ?? this.focusCategories,
    createdAt: createdAt ?? this.createdAt,
  );
  UserProfile copyWithCompanion(UserProfilesCompanion data) {
    return UserProfile(
      id: data.id.present ? data.id.value : this.id,
      name: data.name.present ? data.name.value : this.name,
      avatarPath: data.avatarPath.present
          ? data.avatarPath.value
          : this.avatarPath,
      focusCategories: data.focusCategories.present
          ? data.focusCategories.value
          : this.focusCategories,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('UserProfile(')
          ..write('id: $id, ')
          ..write('name: $name, ')
          ..write('avatarPath: $avatarPath, ')
          ..write('focusCategories: $focusCategories, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode =>
      Object.hash(id, name, avatarPath, focusCategories, createdAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is UserProfile &&
          other.id == this.id &&
          other.name == this.name &&
          other.avatarPath == this.avatarPath &&
          other.focusCategories == this.focusCategories &&
          other.createdAt == this.createdAt);
}

class UserProfilesCompanion extends UpdateCompanion<UserProfile> {
  final Value<int> id;
  final Value<String> name;
  final Value<String?> avatarPath;
  final Value<String> focusCategories;
  final Value<DateTime> createdAt;
  const UserProfilesCompanion({
    this.id = const Value.absent(),
    this.name = const Value.absent(),
    this.avatarPath = const Value.absent(),
    this.focusCategories = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  UserProfilesCompanion.insert({
    this.id = const Value.absent(),
    this.name = const Value.absent(),
    this.avatarPath = const Value.absent(),
    this.focusCategories = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  static Insertable<UserProfile> custom({
    Expression<int>? id,
    Expression<String>? name,
    Expression<String>? avatarPath,
    Expression<String>? focusCategories,
    Expression<DateTime>? createdAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (name != null) 'name': name,
      if (avatarPath != null) 'avatar_path': avatarPath,
      if (focusCategories != null) 'focus_categories': focusCategories,
      if (createdAt != null) 'created_at': createdAt,
    });
  }

  UserProfilesCompanion copyWith({
    Value<int>? id,
    Value<String>? name,
    Value<String?>? avatarPath,
    Value<String>? focusCategories,
    Value<DateTime>? createdAt,
  }) {
    return UserProfilesCompanion(
      id: id ?? this.id,
      name: name ?? this.name,
      avatarPath: avatarPath ?? this.avatarPath,
      focusCategories: focusCategories ?? this.focusCategories,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (name.present) {
      map['name'] = Variable<String>(name.value);
    }
    if (avatarPath.present) {
      map['avatar_path'] = Variable<String>(avatarPath.value);
    }
    if (focusCategories.present) {
      map['focus_categories'] = Variable<String>(focusCategories.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('UserProfilesCompanion(')
          ..write('id: $id, ')
          ..write('name: $name, ')
          ..write('avatarPath: $avatarPath, ')
          ..write('focusCategories: $focusCategories, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }
}

class $AppSettingsTable extends AppSettings
    with TableInfo<$AppSettingsTable, AppSetting> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $AppSettingsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _keyMeta = const VerificationMeta('key');
  @override
  late final GeneratedColumn<String> key = GeneratedColumn<String>(
    'key',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _valueMeta = const VerificationMeta('value');
  @override
  late final GeneratedColumn<String> value = GeneratedColumn<String>(
    'value',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [key, value];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'app_settings';
  @override
  VerificationContext validateIntegrity(
    Insertable<AppSetting> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('key')) {
      context.handle(
        _keyMeta,
        key.isAcceptableOrUnknown(data['key']!, _keyMeta),
      );
    } else if (isInserting) {
      context.missing(_keyMeta);
    }
    if (data.containsKey('value')) {
      context.handle(
        _valueMeta,
        value.isAcceptableOrUnknown(data['value']!, _valueMeta),
      );
    } else if (isInserting) {
      context.missing(_valueMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {key};
  @override
  AppSetting map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return AppSetting(
      key: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}key'],
      )!,
      value: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}value'],
      )!,
    );
  }

  @override
  $AppSettingsTable createAlias(String alias) {
    return $AppSettingsTable(attachedDatabase, alias);
  }
}

class AppSetting extends DataClass implements Insertable<AppSetting> {
  final String key;
  final String value;
  const AppSetting({required this.key, required this.value});
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['key'] = Variable<String>(key);
    map['value'] = Variable<String>(value);
    return map;
  }

  AppSettingsCompanion toCompanion(bool nullToAbsent) {
    return AppSettingsCompanion(key: Value(key), value: Value(value));
  }

  factory AppSetting.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return AppSetting(
      key: serializer.fromJson<String>(json['key']),
      value: serializer.fromJson<String>(json['value']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'key': serializer.toJson<String>(key),
      'value': serializer.toJson<String>(value),
    };
  }

  AppSetting copyWith({String? key, String? value}) =>
      AppSetting(key: key ?? this.key, value: value ?? this.value);
  AppSetting copyWithCompanion(AppSettingsCompanion data) {
    return AppSetting(
      key: data.key.present ? data.key.value : this.key,
      value: data.value.present ? data.value.value : this.value,
    );
  }

  @override
  String toString() {
    return (StringBuffer('AppSetting(')
          ..write('key: $key, ')
          ..write('value: $value')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(key, value);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is AppSetting &&
          other.key == this.key &&
          other.value == this.value);
}

class AppSettingsCompanion extends UpdateCompanion<AppSetting> {
  final Value<String> key;
  final Value<String> value;
  final Value<int> rowid;
  const AppSettingsCompanion({
    this.key = const Value.absent(),
    this.value = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  AppSettingsCompanion.insert({
    required String key,
    required String value,
    this.rowid = const Value.absent(),
  }) : key = Value(key),
       value = Value(value);
  static Insertable<AppSetting> custom({
    Expression<String>? key,
    Expression<String>? value,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (key != null) 'key': key,
      if (value != null) 'value': value,
      if (rowid != null) 'rowid': rowid,
    });
  }

  AppSettingsCompanion copyWith({
    Value<String>? key,
    Value<String>? value,
    Value<int>? rowid,
  }) {
    return AppSettingsCompanion(
      key: key ?? this.key,
      value: value ?? this.value,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (key.present) {
      map['key'] = Variable<String>(key.value);
    }
    if (value.present) {
      map['value'] = Variable<String>(value.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('AppSettingsCompanion(')
          ..write('key: $key, ')
          ..write('value: $value, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $DreamCategoriesTable extends DreamCategories
    with TableInfo<$DreamCategoriesTable, DreamCategory> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $DreamCategoriesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _nameMeta = const VerificationMeta('name');
  @override
  late final GeneratedColumn<String> name = GeneratedColumn<String>(
    'name',
    aliasedName,
    false,
    additionalChecks: GeneratedColumn.checkTextLength(
      minTextLength: 1,
      maxTextLength: 40,
    ),
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _iconKeyMeta = const VerificationMeta(
    'iconKey',
  );
  @override
  late final GeneratedColumn<String> iconKey = GeneratedColumn<String>(
    'icon_key',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('star'),
  );
  static const VerificationMeta _colorMeta = const VerificationMeta('color');
  @override
  late final GeneratedColumn<int> color = GeneratedColumn<int>(
    'color',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _plantTypeMeta = const VerificationMeta(
    'plantType',
  );
  @override
  late final GeneratedColumn<String> plantType = GeneratedColumn<String>(
    'plant_type',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('oak'),
  );
  static const VerificationMeta _isBuiltInMeta = const VerificationMeta(
    'isBuiltIn',
  );
  @override
  late final GeneratedColumn<bool> isBuiltIn = GeneratedColumn<bool>(
    'is_built_in',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("is_built_in" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _sortOrderMeta = const VerificationMeta(
    'sortOrder',
  );
  @override
  late final GeneratedColumn<int> sortOrder = GeneratedColumn<int>(
    'sort_order',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    name,
    iconKey,
    color,
    plantType,
    isBuiltIn,
    sortOrder,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'dream_categories';
  @override
  VerificationContext validateIntegrity(
    Insertable<DreamCategory> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('name')) {
      context.handle(
        _nameMeta,
        name.isAcceptableOrUnknown(data['name']!, _nameMeta),
      );
    } else if (isInserting) {
      context.missing(_nameMeta);
    }
    if (data.containsKey('icon_key')) {
      context.handle(
        _iconKeyMeta,
        iconKey.isAcceptableOrUnknown(data['icon_key']!, _iconKeyMeta),
      );
    }
    if (data.containsKey('color')) {
      context.handle(
        _colorMeta,
        color.isAcceptableOrUnknown(data['color']!, _colorMeta),
      );
    } else if (isInserting) {
      context.missing(_colorMeta);
    }
    if (data.containsKey('plant_type')) {
      context.handle(
        _plantTypeMeta,
        plantType.isAcceptableOrUnknown(data['plant_type']!, _plantTypeMeta),
      );
    }
    if (data.containsKey('is_built_in')) {
      context.handle(
        _isBuiltInMeta,
        isBuiltIn.isAcceptableOrUnknown(data['is_built_in']!, _isBuiltInMeta),
      );
    }
    if (data.containsKey('sort_order')) {
      context.handle(
        _sortOrderMeta,
        sortOrder.isAcceptableOrUnknown(data['sort_order']!, _sortOrderMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  DreamCategory map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return DreamCategory(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      name: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}name'],
      )!,
      iconKey: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}icon_key'],
      )!,
      color: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}color'],
      )!,
      plantType: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}plant_type'],
      )!,
      isBuiltIn: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}is_built_in'],
      )!,
      sortOrder: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}sort_order'],
      )!,
    );
  }

  @override
  $DreamCategoriesTable createAlias(String alias) {
    return $DreamCategoriesTable(attachedDatabase, alias);
  }
}

class DreamCategory extends DataClass implements Insertable<DreamCategory> {
  final int id;
  final String name;
  final String iconKey;
  final int color;
  final String plantType;
  final bool isBuiltIn;
  final int sortOrder;
  const DreamCategory({
    required this.id,
    required this.name,
    required this.iconKey,
    required this.color,
    required this.plantType,
    required this.isBuiltIn,
    required this.sortOrder,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['name'] = Variable<String>(name);
    map['icon_key'] = Variable<String>(iconKey);
    map['color'] = Variable<int>(color);
    map['plant_type'] = Variable<String>(plantType);
    map['is_built_in'] = Variable<bool>(isBuiltIn);
    map['sort_order'] = Variable<int>(sortOrder);
    return map;
  }

  DreamCategoriesCompanion toCompanion(bool nullToAbsent) {
    return DreamCategoriesCompanion(
      id: Value(id),
      name: Value(name),
      iconKey: Value(iconKey),
      color: Value(color),
      plantType: Value(plantType),
      isBuiltIn: Value(isBuiltIn),
      sortOrder: Value(sortOrder),
    );
  }

  factory DreamCategory.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return DreamCategory(
      id: serializer.fromJson<int>(json['id']),
      name: serializer.fromJson<String>(json['name']),
      iconKey: serializer.fromJson<String>(json['iconKey']),
      color: serializer.fromJson<int>(json['color']),
      plantType: serializer.fromJson<String>(json['plantType']),
      isBuiltIn: serializer.fromJson<bool>(json['isBuiltIn']),
      sortOrder: serializer.fromJson<int>(json['sortOrder']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'name': serializer.toJson<String>(name),
      'iconKey': serializer.toJson<String>(iconKey),
      'color': serializer.toJson<int>(color),
      'plantType': serializer.toJson<String>(plantType),
      'isBuiltIn': serializer.toJson<bool>(isBuiltIn),
      'sortOrder': serializer.toJson<int>(sortOrder),
    };
  }

  DreamCategory copyWith({
    int? id,
    String? name,
    String? iconKey,
    int? color,
    String? plantType,
    bool? isBuiltIn,
    int? sortOrder,
  }) => DreamCategory(
    id: id ?? this.id,
    name: name ?? this.name,
    iconKey: iconKey ?? this.iconKey,
    color: color ?? this.color,
    plantType: plantType ?? this.plantType,
    isBuiltIn: isBuiltIn ?? this.isBuiltIn,
    sortOrder: sortOrder ?? this.sortOrder,
  );
  DreamCategory copyWithCompanion(DreamCategoriesCompanion data) {
    return DreamCategory(
      id: data.id.present ? data.id.value : this.id,
      name: data.name.present ? data.name.value : this.name,
      iconKey: data.iconKey.present ? data.iconKey.value : this.iconKey,
      color: data.color.present ? data.color.value : this.color,
      plantType: data.plantType.present ? data.plantType.value : this.plantType,
      isBuiltIn: data.isBuiltIn.present ? data.isBuiltIn.value : this.isBuiltIn,
      sortOrder: data.sortOrder.present ? data.sortOrder.value : this.sortOrder,
    );
  }

  @override
  String toString() {
    return (StringBuffer('DreamCategory(')
          ..write('id: $id, ')
          ..write('name: $name, ')
          ..write('iconKey: $iconKey, ')
          ..write('color: $color, ')
          ..write('plantType: $plantType, ')
          ..write('isBuiltIn: $isBuiltIn, ')
          ..write('sortOrder: $sortOrder')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode =>
      Object.hash(id, name, iconKey, color, plantType, isBuiltIn, sortOrder);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is DreamCategory &&
          other.id == this.id &&
          other.name == this.name &&
          other.iconKey == this.iconKey &&
          other.color == this.color &&
          other.plantType == this.plantType &&
          other.isBuiltIn == this.isBuiltIn &&
          other.sortOrder == this.sortOrder);
}

class DreamCategoriesCompanion extends UpdateCompanion<DreamCategory> {
  final Value<int> id;
  final Value<String> name;
  final Value<String> iconKey;
  final Value<int> color;
  final Value<String> plantType;
  final Value<bool> isBuiltIn;
  final Value<int> sortOrder;
  const DreamCategoriesCompanion({
    this.id = const Value.absent(),
    this.name = const Value.absent(),
    this.iconKey = const Value.absent(),
    this.color = const Value.absent(),
    this.plantType = const Value.absent(),
    this.isBuiltIn = const Value.absent(),
    this.sortOrder = const Value.absent(),
  });
  DreamCategoriesCompanion.insert({
    this.id = const Value.absent(),
    required String name,
    this.iconKey = const Value.absent(),
    required int color,
    this.plantType = const Value.absent(),
    this.isBuiltIn = const Value.absent(),
    this.sortOrder = const Value.absent(),
  }) : name = Value(name),
       color = Value(color);
  static Insertable<DreamCategory> custom({
    Expression<int>? id,
    Expression<String>? name,
    Expression<String>? iconKey,
    Expression<int>? color,
    Expression<String>? plantType,
    Expression<bool>? isBuiltIn,
    Expression<int>? sortOrder,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (name != null) 'name': name,
      if (iconKey != null) 'icon_key': iconKey,
      if (color != null) 'color': color,
      if (plantType != null) 'plant_type': plantType,
      if (isBuiltIn != null) 'is_built_in': isBuiltIn,
      if (sortOrder != null) 'sort_order': sortOrder,
    });
  }

  DreamCategoriesCompanion copyWith({
    Value<int>? id,
    Value<String>? name,
    Value<String>? iconKey,
    Value<int>? color,
    Value<String>? plantType,
    Value<bool>? isBuiltIn,
    Value<int>? sortOrder,
  }) {
    return DreamCategoriesCompanion(
      id: id ?? this.id,
      name: name ?? this.name,
      iconKey: iconKey ?? this.iconKey,
      color: color ?? this.color,
      plantType: plantType ?? this.plantType,
      isBuiltIn: isBuiltIn ?? this.isBuiltIn,
      sortOrder: sortOrder ?? this.sortOrder,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (name.present) {
      map['name'] = Variable<String>(name.value);
    }
    if (iconKey.present) {
      map['icon_key'] = Variable<String>(iconKey.value);
    }
    if (color.present) {
      map['color'] = Variable<int>(color.value);
    }
    if (plantType.present) {
      map['plant_type'] = Variable<String>(plantType.value);
    }
    if (isBuiltIn.present) {
      map['is_built_in'] = Variable<bool>(isBuiltIn.value);
    }
    if (sortOrder.present) {
      map['sort_order'] = Variable<int>(sortOrder.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('DreamCategoriesCompanion(')
          ..write('id: $id, ')
          ..write('name: $name, ')
          ..write('iconKey: $iconKey, ')
          ..write('color: $color, ')
          ..write('plantType: $plantType, ')
          ..write('isBuiltIn: $isBuiltIn, ')
          ..write('sortOrder: $sortOrder')
          ..write(')'))
        .toString();
  }
}

class $DreamsTable extends Dreams with TableInfo<$DreamsTable, Dream> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $DreamsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _titleMeta = const VerificationMeta('title');
  @override
  late final GeneratedColumn<String> title = GeneratedColumn<String>(
    'title',
    aliasedName,
    false,
    additionalChecks: GeneratedColumn.checkTextLength(
      minTextLength: 1,
      maxTextLength: 120,
    ),
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _descriptionMeta = const VerificationMeta(
    'description',
  );
  @override
  late final GeneratedColumn<String> description = GeneratedColumn<String>(
    'description',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant(''),
  );
  static const VerificationMeta _coverPathMeta = const VerificationMeta(
    'coverPath',
  );
  @override
  late final GeneratedColumn<String> coverPath = GeneratedColumn<String>(
    'cover_path',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _categoryIdMeta = const VerificationMeta(
    'categoryId',
  );
  @override
  late final GeneratedColumn<int> categoryId = GeneratedColumn<int>(
    'category_id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES dream_categories (id) ON DELETE RESTRICT',
    ),
  );
  static const VerificationMeta _targetDateMeta = const VerificationMeta(
    'targetDate',
  );
  @override
  late final GeneratedColumn<DateTime> targetDate = GeneratedColumn<DateTime>(
    'target_date',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _priorityMeta = const VerificationMeta(
    'priority',
  );
  @override
  late final GeneratedColumn<int> priority = GeneratedColumn<int>(
    'priority',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(1),
  );
  static const VerificationMeta _notesMeta = const VerificationMeta('notes');
  @override
  late final GeneratedColumn<String> notes = GeneratedColumn<String>(
    'notes',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant(''),
  );
  static const VerificationMeta _motivationMeta = const VerificationMeta(
    'motivation',
  );
  @override
  late final GeneratedColumn<String> motivation = GeneratedColumn<String>(
    'motivation',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant(''),
  );
  static const VerificationMeta _budgetMinorMeta = const VerificationMeta(
    'budgetMinor',
  );
  @override
  late final GeneratedColumn<int> budgetMinor = GeneratedColumn<int>(
    'budget_minor',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _currencyMeta = const VerificationMeta(
    'currency',
  );
  @override
  late final GeneratedColumn<String> currency = GeneratedColumn<String>(
    'currency',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('TRY'),
  );
  static const VerificationMeta _weightedProgressMeta = const VerificationMeta(
    'weightedProgress',
  );
  @override
  late final GeneratedColumn<bool> weightedProgress = GeneratedColumn<bool>(
    'weighted_progress',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("weighted_progress" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _isCompletedMeta = const VerificationMeta(
    'isCompleted',
  );
  @override
  late final GeneratedColumn<bool> isCompleted = GeneratedColumn<bool>(
    'is_completed',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("is_completed" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _completedAtMeta = const VerificationMeta(
    'completedAt',
  );
  @override
  late final GeneratedColumn<DateTime> completedAt = GeneratedColumn<DateTime>(
    'completed_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _isHiddenMeta = const VerificationMeta(
    'isHidden',
  );
  @override
  late final GeneratedColumn<bool> isHidden = GeneratedColumn<bool>(
    'is_hidden',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("is_hidden" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _archivedAtMeta = const VerificationMeta(
    'archivedAt',
  );
  @override
  late final GeneratedColumn<DateTime> archivedAt = GeneratedColumn<DateTime>(
    'archived_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  @override
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
    'updated_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    title,
    description,
    coverPath,
    categoryId,
    targetDate,
    priority,
    notes,
    motivation,
    budgetMinor,
    currency,
    weightedProgress,
    isCompleted,
    completedAt,
    isHidden,
    archivedAt,
    createdAt,
    updatedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'dreams';
  @override
  VerificationContext validateIntegrity(
    Insertable<Dream> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('title')) {
      context.handle(
        _titleMeta,
        title.isAcceptableOrUnknown(data['title']!, _titleMeta),
      );
    } else if (isInserting) {
      context.missing(_titleMeta);
    }
    if (data.containsKey('description')) {
      context.handle(
        _descriptionMeta,
        description.isAcceptableOrUnknown(
          data['description']!,
          _descriptionMeta,
        ),
      );
    }
    if (data.containsKey('cover_path')) {
      context.handle(
        _coverPathMeta,
        coverPath.isAcceptableOrUnknown(data['cover_path']!, _coverPathMeta),
      );
    }
    if (data.containsKey('category_id')) {
      context.handle(
        _categoryIdMeta,
        categoryId.isAcceptableOrUnknown(data['category_id']!, _categoryIdMeta),
      );
    } else if (isInserting) {
      context.missing(_categoryIdMeta);
    }
    if (data.containsKey('target_date')) {
      context.handle(
        _targetDateMeta,
        targetDate.isAcceptableOrUnknown(data['target_date']!, _targetDateMeta),
      );
    }
    if (data.containsKey('priority')) {
      context.handle(
        _priorityMeta,
        priority.isAcceptableOrUnknown(data['priority']!, _priorityMeta),
      );
    }
    if (data.containsKey('notes')) {
      context.handle(
        _notesMeta,
        notes.isAcceptableOrUnknown(data['notes']!, _notesMeta),
      );
    }
    if (data.containsKey('motivation')) {
      context.handle(
        _motivationMeta,
        motivation.isAcceptableOrUnknown(data['motivation']!, _motivationMeta),
      );
    }
    if (data.containsKey('budget_minor')) {
      context.handle(
        _budgetMinorMeta,
        budgetMinor.isAcceptableOrUnknown(
          data['budget_minor']!,
          _budgetMinorMeta,
        ),
      );
    }
    if (data.containsKey('currency')) {
      context.handle(
        _currencyMeta,
        currency.isAcceptableOrUnknown(data['currency']!, _currencyMeta),
      );
    }
    if (data.containsKey('weighted_progress')) {
      context.handle(
        _weightedProgressMeta,
        weightedProgress.isAcceptableOrUnknown(
          data['weighted_progress']!,
          _weightedProgressMeta,
        ),
      );
    }
    if (data.containsKey('is_completed')) {
      context.handle(
        _isCompletedMeta,
        isCompleted.isAcceptableOrUnknown(
          data['is_completed']!,
          _isCompletedMeta,
        ),
      );
    }
    if (data.containsKey('completed_at')) {
      context.handle(
        _completedAtMeta,
        completedAt.isAcceptableOrUnknown(
          data['completed_at']!,
          _completedAtMeta,
        ),
      );
    }
    if (data.containsKey('is_hidden')) {
      context.handle(
        _isHiddenMeta,
        isHidden.isAcceptableOrUnknown(data['is_hidden']!, _isHiddenMeta),
      );
    }
    if (data.containsKey('archived_at')) {
      context.handle(
        _archivedAtMeta,
        archivedAt.isAcceptableOrUnknown(data['archived_at']!, _archivedAtMeta),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  Dream map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return Dream(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      title: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}title'],
      )!,
      description: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}description'],
      )!,
      coverPath: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}cover_path'],
      ),
      categoryId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}category_id'],
      )!,
      targetDate: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}target_date'],
      ),
      priority: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}priority'],
      )!,
      notes: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}notes'],
      )!,
      motivation: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}motivation'],
      )!,
      budgetMinor: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}budget_minor'],
      ),
      currency: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}currency'],
      )!,
      weightedProgress: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}weighted_progress'],
      )!,
      isCompleted: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}is_completed'],
      )!,
      completedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}completed_at'],
      ),
      isHidden: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}is_hidden'],
      )!,
      archivedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}archived_at'],
      ),
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      )!,
    );
  }

  @override
  $DreamsTable createAlias(String alias) {
    return $DreamsTable(attachedDatabase, alias);
  }
}

class Dream extends DataClass implements Insertable<Dream> {
  final int id;
  final String title;
  final String description;
  final String? coverPath;
  final int categoryId;
  final DateTime? targetDate;
  final int priority;
  final String notes;
  final String motivation;
  final int? budgetMinor;
  final String currency;
  final bool weightedProgress;
  final bool isCompleted;
  final DateTime? completedAt;
  final bool isHidden;
  final DateTime? archivedAt;
  final DateTime createdAt;
  final DateTime updatedAt;
  const Dream({
    required this.id,
    required this.title,
    required this.description,
    this.coverPath,
    required this.categoryId,
    this.targetDate,
    required this.priority,
    required this.notes,
    required this.motivation,
    this.budgetMinor,
    required this.currency,
    required this.weightedProgress,
    required this.isCompleted,
    this.completedAt,
    required this.isHidden,
    this.archivedAt,
    required this.createdAt,
    required this.updatedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['title'] = Variable<String>(title);
    map['description'] = Variable<String>(description);
    if (!nullToAbsent || coverPath != null) {
      map['cover_path'] = Variable<String>(coverPath);
    }
    map['category_id'] = Variable<int>(categoryId);
    if (!nullToAbsent || targetDate != null) {
      map['target_date'] = Variable<DateTime>(targetDate);
    }
    map['priority'] = Variable<int>(priority);
    map['notes'] = Variable<String>(notes);
    map['motivation'] = Variable<String>(motivation);
    if (!nullToAbsent || budgetMinor != null) {
      map['budget_minor'] = Variable<int>(budgetMinor);
    }
    map['currency'] = Variable<String>(currency);
    map['weighted_progress'] = Variable<bool>(weightedProgress);
    map['is_completed'] = Variable<bool>(isCompleted);
    if (!nullToAbsent || completedAt != null) {
      map['completed_at'] = Variable<DateTime>(completedAt);
    }
    map['is_hidden'] = Variable<bool>(isHidden);
    if (!nullToAbsent || archivedAt != null) {
      map['archived_at'] = Variable<DateTime>(archivedAt);
    }
    map['created_at'] = Variable<DateTime>(createdAt);
    map['updated_at'] = Variable<DateTime>(updatedAt);
    return map;
  }

  DreamsCompanion toCompanion(bool nullToAbsent) {
    return DreamsCompanion(
      id: Value(id),
      title: Value(title),
      description: Value(description),
      coverPath: coverPath == null && nullToAbsent
          ? const Value.absent()
          : Value(coverPath),
      categoryId: Value(categoryId),
      targetDate: targetDate == null && nullToAbsent
          ? const Value.absent()
          : Value(targetDate),
      priority: Value(priority),
      notes: Value(notes),
      motivation: Value(motivation),
      budgetMinor: budgetMinor == null && nullToAbsent
          ? const Value.absent()
          : Value(budgetMinor),
      currency: Value(currency),
      weightedProgress: Value(weightedProgress),
      isCompleted: Value(isCompleted),
      completedAt: completedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(completedAt),
      isHidden: Value(isHidden),
      archivedAt: archivedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(archivedAt),
      createdAt: Value(createdAt),
      updatedAt: Value(updatedAt),
    );
  }

  factory Dream.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return Dream(
      id: serializer.fromJson<int>(json['id']),
      title: serializer.fromJson<String>(json['title']),
      description: serializer.fromJson<String>(json['description']),
      coverPath: serializer.fromJson<String?>(json['coverPath']),
      categoryId: serializer.fromJson<int>(json['categoryId']),
      targetDate: serializer.fromJson<DateTime?>(json['targetDate']),
      priority: serializer.fromJson<int>(json['priority']),
      notes: serializer.fromJson<String>(json['notes']),
      motivation: serializer.fromJson<String>(json['motivation']),
      budgetMinor: serializer.fromJson<int?>(json['budgetMinor']),
      currency: serializer.fromJson<String>(json['currency']),
      weightedProgress: serializer.fromJson<bool>(json['weightedProgress']),
      isCompleted: serializer.fromJson<bool>(json['isCompleted']),
      completedAt: serializer.fromJson<DateTime?>(json['completedAt']),
      isHidden: serializer.fromJson<bool>(json['isHidden']),
      archivedAt: serializer.fromJson<DateTime?>(json['archivedAt']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
      updatedAt: serializer.fromJson<DateTime>(json['updatedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'title': serializer.toJson<String>(title),
      'description': serializer.toJson<String>(description),
      'coverPath': serializer.toJson<String?>(coverPath),
      'categoryId': serializer.toJson<int>(categoryId),
      'targetDate': serializer.toJson<DateTime?>(targetDate),
      'priority': serializer.toJson<int>(priority),
      'notes': serializer.toJson<String>(notes),
      'motivation': serializer.toJson<String>(motivation),
      'budgetMinor': serializer.toJson<int?>(budgetMinor),
      'currency': serializer.toJson<String>(currency),
      'weightedProgress': serializer.toJson<bool>(weightedProgress),
      'isCompleted': serializer.toJson<bool>(isCompleted),
      'completedAt': serializer.toJson<DateTime?>(completedAt),
      'isHidden': serializer.toJson<bool>(isHidden),
      'archivedAt': serializer.toJson<DateTime?>(archivedAt),
      'createdAt': serializer.toJson<DateTime>(createdAt),
      'updatedAt': serializer.toJson<DateTime>(updatedAt),
    };
  }

  Dream copyWith({
    int? id,
    String? title,
    String? description,
    Value<String?> coverPath = const Value.absent(),
    int? categoryId,
    Value<DateTime?> targetDate = const Value.absent(),
    int? priority,
    String? notes,
    String? motivation,
    Value<int?> budgetMinor = const Value.absent(),
    String? currency,
    bool? weightedProgress,
    bool? isCompleted,
    Value<DateTime?> completedAt = const Value.absent(),
    bool? isHidden,
    Value<DateTime?> archivedAt = const Value.absent(),
    DateTime? createdAt,
    DateTime? updatedAt,
  }) => Dream(
    id: id ?? this.id,
    title: title ?? this.title,
    description: description ?? this.description,
    coverPath: coverPath.present ? coverPath.value : this.coverPath,
    categoryId: categoryId ?? this.categoryId,
    targetDate: targetDate.present ? targetDate.value : this.targetDate,
    priority: priority ?? this.priority,
    notes: notes ?? this.notes,
    motivation: motivation ?? this.motivation,
    budgetMinor: budgetMinor.present ? budgetMinor.value : this.budgetMinor,
    currency: currency ?? this.currency,
    weightedProgress: weightedProgress ?? this.weightedProgress,
    isCompleted: isCompleted ?? this.isCompleted,
    completedAt: completedAt.present ? completedAt.value : this.completedAt,
    isHidden: isHidden ?? this.isHidden,
    archivedAt: archivedAt.present ? archivedAt.value : this.archivedAt,
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt ?? this.updatedAt,
  );
  Dream copyWithCompanion(DreamsCompanion data) {
    return Dream(
      id: data.id.present ? data.id.value : this.id,
      title: data.title.present ? data.title.value : this.title,
      description: data.description.present
          ? data.description.value
          : this.description,
      coverPath: data.coverPath.present ? data.coverPath.value : this.coverPath,
      categoryId: data.categoryId.present
          ? data.categoryId.value
          : this.categoryId,
      targetDate: data.targetDate.present
          ? data.targetDate.value
          : this.targetDate,
      priority: data.priority.present ? data.priority.value : this.priority,
      notes: data.notes.present ? data.notes.value : this.notes,
      motivation: data.motivation.present
          ? data.motivation.value
          : this.motivation,
      budgetMinor: data.budgetMinor.present
          ? data.budgetMinor.value
          : this.budgetMinor,
      currency: data.currency.present ? data.currency.value : this.currency,
      weightedProgress: data.weightedProgress.present
          ? data.weightedProgress.value
          : this.weightedProgress,
      isCompleted: data.isCompleted.present
          ? data.isCompleted.value
          : this.isCompleted,
      completedAt: data.completedAt.present
          ? data.completedAt.value
          : this.completedAt,
      isHidden: data.isHidden.present ? data.isHidden.value : this.isHidden,
      archivedAt: data.archivedAt.present
          ? data.archivedAt.value
          : this.archivedAt,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('Dream(')
          ..write('id: $id, ')
          ..write('title: $title, ')
          ..write('description: $description, ')
          ..write('coverPath: $coverPath, ')
          ..write('categoryId: $categoryId, ')
          ..write('targetDate: $targetDate, ')
          ..write('priority: $priority, ')
          ..write('notes: $notes, ')
          ..write('motivation: $motivation, ')
          ..write('budgetMinor: $budgetMinor, ')
          ..write('currency: $currency, ')
          ..write('weightedProgress: $weightedProgress, ')
          ..write('isCompleted: $isCompleted, ')
          ..write('completedAt: $completedAt, ')
          ..write('isHidden: $isHidden, ')
          ..write('archivedAt: $archivedAt, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    title,
    description,
    coverPath,
    categoryId,
    targetDate,
    priority,
    notes,
    motivation,
    budgetMinor,
    currency,
    weightedProgress,
    isCompleted,
    completedAt,
    isHidden,
    archivedAt,
    createdAt,
    updatedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is Dream &&
          other.id == this.id &&
          other.title == this.title &&
          other.description == this.description &&
          other.coverPath == this.coverPath &&
          other.categoryId == this.categoryId &&
          other.targetDate == this.targetDate &&
          other.priority == this.priority &&
          other.notes == this.notes &&
          other.motivation == this.motivation &&
          other.budgetMinor == this.budgetMinor &&
          other.currency == this.currency &&
          other.weightedProgress == this.weightedProgress &&
          other.isCompleted == this.isCompleted &&
          other.completedAt == this.completedAt &&
          other.isHidden == this.isHidden &&
          other.archivedAt == this.archivedAt &&
          other.createdAt == this.createdAt &&
          other.updatedAt == this.updatedAt);
}

class DreamsCompanion extends UpdateCompanion<Dream> {
  final Value<int> id;
  final Value<String> title;
  final Value<String> description;
  final Value<String?> coverPath;
  final Value<int> categoryId;
  final Value<DateTime?> targetDate;
  final Value<int> priority;
  final Value<String> notes;
  final Value<String> motivation;
  final Value<int?> budgetMinor;
  final Value<String> currency;
  final Value<bool> weightedProgress;
  final Value<bool> isCompleted;
  final Value<DateTime?> completedAt;
  final Value<bool> isHidden;
  final Value<DateTime?> archivedAt;
  final Value<DateTime> createdAt;
  final Value<DateTime> updatedAt;
  const DreamsCompanion({
    this.id = const Value.absent(),
    this.title = const Value.absent(),
    this.description = const Value.absent(),
    this.coverPath = const Value.absent(),
    this.categoryId = const Value.absent(),
    this.targetDate = const Value.absent(),
    this.priority = const Value.absent(),
    this.notes = const Value.absent(),
    this.motivation = const Value.absent(),
    this.budgetMinor = const Value.absent(),
    this.currency = const Value.absent(),
    this.weightedProgress = const Value.absent(),
    this.isCompleted = const Value.absent(),
    this.completedAt = const Value.absent(),
    this.isHidden = const Value.absent(),
    this.archivedAt = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
  });
  DreamsCompanion.insert({
    this.id = const Value.absent(),
    required String title,
    this.description = const Value.absent(),
    this.coverPath = const Value.absent(),
    required int categoryId,
    this.targetDate = const Value.absent(),
    this.priority = const Value.absent(),
    this.notes = const Value.absent(),
    this.motivation = const Value.absent(),
    this.budgetMinor = const Value.absent(),
    this.currency = const Value.absent(),
    this.weightedProgress = const Value.absent(),
    this.isCompleted = const Value.absent(),
    this.completedAt = const Value.absent(),
    this.isHidden = const Value.absent(),
    this.archivedAt = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
  }) : title = Value(title),
       categoryId = Value(categoryId);
  static Insertable<Dream> custom({
    Expression<int>? id,
    Expression<String>? title,
    Expression<String>? description,
    Expression<String>? coverPath,
    Expression<int>? categoryId,
    Expression<DateTime>? targetDate,
    Expression<int>? priority,
    Expression<String>? notes,
    Expression<String>? motivation,
    Expression<int>? budgetMinor,
    Expression<String>? currency,
    Expression<bool>? weightedProgress,
    Expression<bool>? isCompleted,
    Expression<DateTime>? completedAt,
    Expression<bool>? isHidden,
    Expression<DateTime>? archivedAt,
    Expression<DateTime>? createdAt,
    Expression<DateTime>? updatedAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (title != null) 'title': title,
      if (description != null) 'description': description,
      if (coverPath != null) 'cover_path': coverPath,
      if (categoryId != null) 'category_id': categoryId,
      if (targetDate != null) 'target_date': targetDate,
      if (priority != null) 'priority': priority,
      if (notes != null) 'notes': notes,
      if (motivation != null) 'motivation': motivation,
      if (budgetMinor != null) 'budget_minor': budgetMinor,
      if (currency != null) 'currency': currency,
      if (weightedProgress != null) 'weighted_progress': weightedProgress,
      if (isCompleted != null) 'is_completed': isCompleted,
      if (completedAt != null) 'completed_at': completedAt,
      if (isHidden != null) 'is_hidden': isHidden,
      if (archivedAt != null) 'archived_at': archivedAt,
      if (createdAt != null) 'created_at': createdAt,
      if (updatedAt != null) 'updated_at': updatedAt,
    });
  }

  DreamsCompanion copyWith({
    Value<int>? id,
    Value<String>? title,
    Value<String>? description,
    Value<String?>? coverPath,
    Value<int>? categoryId,
    Value<DateTime?>? targetDate,
    Value<int>? priority,
    Value<String>? notes,
    Value<String>? motivation,
    Value<int?>? budgetMinor,
    Value<String>? currency,
    Value<bool>? weightedProgress,
    Value<bool>? isCompleted,
    Value<DateTime?>? completedAt,
    Value<bool>? isHidden,
    Value<DateTime?>? archivedAt,
    Value<DateTime>? createdAt,
    Value<DateTime>? updatedAt,
  }) {
    return DreamsCompanion(
      id: id ?? this.id,
      title: title ?? this.title,
      description: description ?? this.description,
      coverPath: coverPath ?? this.coverPath,
      categoryId: categoryId ?? this.categoryId,
      targetDate: targetDate ?? this.targetDate,
      priority: priority ?? this.priority,
      notes: notes ?? this.notes,
      motivation: motivation ?? this.motivation,
      budgetMinor: budgetMinor ?? this.budgetMinor,
      currency: currency ?? this.currency,
      weightedProgress: weightedProgress ?? this.weightedProgress,
      isCompleted: isCompleted ?? this.isCompleted,
      completedAt: completedAt ?? this.completedAt,
      isHidden: isHidden ?? this.isHidden,
      archivedAt: archivedAt ?? this.archivedAt,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (title.present) {
      map['title'] = Variable<String>(title.value);
    }
    if (description.present) {
      map['description'] = Variable<String>(description.value);
    }
    if (coverPath.present) {
      map['cover_path'] = Variable<String>(coverPath.value);
    }
    if (categoryId.present) {
      map['category_id'] = Variable<int>(categoryId.value);
    }
    if (targetDate.present) {
      map['target_date'] = Variable<DateTime>(targetDate.value);
    }
    if (priority.present) {
      map['priority'] = Variable<int>(priority.value);
    }
    if (notes.present) {
      map['notes'] = Variable<String>(notes.value);
    }
    if (motivation.present) {
      map['motivation'] = Variable<String>(motivation.value);
    }
    if (budgetMinor.present) {
      map['budget_minor'] = Variable<int>(budgetMinor.value);
    }
    if (currency.present) {
      map['currency'] = Variable<String>(currency.value);
    }
    if (weightedProgress.present) {
      map['weighted_progress'] = Variable<bool>(weightedProgress.value);
    }
    if (isCompleted.present) {
      map['is_completed'] = Variable<bool>(isCompleted.value);
    }
    if (completedAt.present) {
      map['completed_at'] = Variable<DateTime>(completedAt.value);
    }
    if (isHidden.present) {
      map['is_hidden'] = Variable<bool>(isHidden.value);
    }
    if (archivedAt.present) {
      map['archived_at'] = Variable<DateTime>(archivedAt.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('DreamsCompanion(')
          ..write('id: $id, ')
          ..write('title: $title, ')
          ..write('description: $description, ')
          ..write('coverPath: $coverPath, ')
          ..write('categoryId: $categoryId, ')
          ..write('targetDate: $targetDate, ')
          ..write('priority: $priority, ')
          ..write('notes: $notes, ')
          ..write('motivation: $motivation, ')
          ..write('budgetMinor: $budgetMinor, ')
          ..write('currency: $currency, ')
          ..write('weightedProgress: $weightedProgress, ')
          ..write('isCompleted: $isCompleted, ')
          ..write('completedAt: $completedAt, ')
          ..write('isHidden: $isHidden, ')
          ..write('archivedAt: $archivedAt, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }
}

class $DreamMilestonesTable extends DreamMilestones
    with TableInfo<$DreamMilestonesTable, DreamMilestone> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $DreamMilestonesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _dreamIdMeta = const VerificationMeta(
    'dreamId',
  );
  @override
  late final GeneratedColumn<int> dreamId = GeneratedColumn<int>(
    'dream_id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES dreams (id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _titleMeta = const VerificationMeta('title');
  @override
  late final GeneratedColumn<String> title = GeneratedColumn<String>(
    'title',
    aliasedName,
    false,
    additionalChecks: GeneratedColumn.checkTextLength(
      minTextLength: 1,
      maxTextLength: 160,
    ),
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _descriptionMeta = const VerificationMeta(
    'description',
  );
  @override
  late final GeneratedColumn<String> description = GeneratedColumn<String>(
    'description',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant(''),
  );
  static const VerificationMeta _targetDateMeta = const VerificationMeta(
    'targetDate',
  );
  @override
  late final GeneratedColumn<DateTime> targetDate = GeneratedColumn<DateTime>(
    'target_date',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _isCompletedMeta = const VerificationMeta(
    'isCompleted',
  );
  @override
  late final GeneratedColumn<bool> isCompleted = GeneratedColumn<bool>(
    'is_completed',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("is_completed" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _completedAtMeta = const VerificationMeta(
    'completedAt',
  );
  @override
  late final GeneratedColumn<DateTime> completedAt = GeneratedColumn<DateTime>(
    'completed_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _weightMeta = const VerificationMeta('weight');
  @override
  late final GeneratedColumn<int> weight = GeneratedColumn<int>(
    'weight',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(1),
  );
  static const VerificationMeta _photoPathMeta = const VerificationMeta(
    'photoPath',
  );
  @override
  late final GeneratedColumn<String> photoPath = GeneratedColumn<String>(
    'photo_path',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _noteMeta = const VerificationMeta('note');
  @override
  late final GeneratedColumn<String> note = GeneratedColumn<String>(
    'note',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant(''),
  );
  static const VerificationMeta _reminderAtMeta = const VerificationMeta(
    'reminderAt',
  );
  @override
  late final GeneratedColumn<DateTime> reminderAt = GeneratedColumn<DateTime>(
    'reminder_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _sortOrderMeta = const VerificationMeta(
    'sortOrder',
  );
  @override
  late final GeneratedColumn<int> sortOrder = GeneratedColumn<int>(
    'sort_order',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    dreamId,
    title,
    description,
    targetDate,
    isCompleted,
    completedAt,
    weight,
    photoPath,
    note,
    reminderAt,
    sortOrder,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'dream_milestones';
  @override
  VerificationContext validateIntegrity(
    Insertable<DreamMilestone> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('dream_id')) {
      context.handle(
        _dreamIdMeta,
        dreamId.isAcceptableOrUnknown(data['dream_id']!, _dreamIdMeta),
      );
    } else if (isInserting) {
      context.missing(_dreamIdMeta);
    }
    if (data.containsKey('title')) {
      context.handle(
        _titleMeta,
        title.isAcceptableOrUnknown(data['title']!, _titleMeta),
      );
    } else if (isInserting) {
      context.missing(_titleMeta);
    }
    if (data.containsKey('description')) {
      context.handle(
        _descriptionMeta,
        description.isAcceptableOrUnknown(
          data['description']!,
          _descriptionMeta,
        ),
      );
    }
    if (data.containsKey('target_date')) {
      context.handle(
        _targetDateMeta,
        targetDate.isAcceptableOrUnknown(data['target_date']!, _targetDateMeta),
      );
    }
    if (data.containsKey('is_completed')) {
      context.handle(
        _isCompletedMeta,
        isCompleted.isAcceptableOrUnknown(
          data['is_completed']!,
          _isCompletedMeta,
        ),
      );
    }
    if (data.containsKey('completed_at')) {
      context.handle(
        _completedAtMeta,
        completedAt.isAcceptableOrUnknown(
          data['completed_at']!,
          _completedAtMeta,
        ),
      );
    }
    if (data.containsKey('weight')) {
      context.handle(
        _weightMeta,
        weight.isAcceptableOrUnknown(data['weight']!, _weightMeta),
      );
    }
    if (data.containsKey('photo_path')) {
      context.handle(
        _photoPathMeta,
        photoPath.isAcceptableOrUnknown(data['photo_path']!, _photoPathMeta),
      );
    }
    if (data.containsKey('note')) {
      context.handle(
        _noteMeta,
        note.isAcceptableOrUnknown(data['note']!, _noteMeta),
      );
    }
    if (data.containsKey('reminder_at')) {
      context.handle(
        _reminderAtMeta,
        reminderAt.isAcceptableOrUnknown(data['reminder_at']!, _reminderAtMeta),
      );
    }
    if (data.containsKey('sort_order')) {
      context.handle(
        _sortOrderMeta,
        sortOrder.isAcceptableOrUnknown(data['sort_order']!, _sortOrderMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  DreamMilestone map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return DreamMilestone(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      dreamId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}dream_id'],
      )!,
      title: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}title'],
      )!,
      description: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}description'],
      )!,
      targetDate: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}target_date'],
      ),
      isCompleted: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}is_completed'],
      )!,
      completedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}completed_at'],
      ),
      weight: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}weight'],
      )!,
      photoPath: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}photo_path'],
      ),
      note: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}note'],
      )!,
      reminderAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}reminder_at'],
      ),
      sortOrder: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}sort_order'],
      )!,
    );
  }

  @override
  $DreamMilestonesTable createAlias(String alias) {
    return $DreamMilestonesTable(attachedDatabase, alias);
  }
}

class DreamMilestone extends DataClass implements Insertable<DreamMilestone> {
  final int id;
  final int dreamId;
  final String title;
  final String description;
  final DateTime? targetDate;
  final bool isCompleted;
  final DateTime? completedAt;
  final int weight;
  final String? photoPath;
  final String note;
  final DateTime? reminderAt;
  final int sortOrder;
  const DreamMilestone({
    required this.id,
    required this.dreamId,
    required this.title,
    required this.description,
    this.targetDate,
    required this.isCompleted,
    this.completedAt,
    required this.weight,
    this.photoPath,
    required this.note,
    this.reminderAt,
    required this.sortOrder,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['dream_id'] = Variable<int>(dreamId);
    map['title'] = Variable<String>(title);
    map['description'] = Variable<String>(description);
    if (!nullToAbsent || targetDate != null) {
      map['target_date'] = Variable<DateTime>(targetDate);
    }
    map['is_completed'] = Variable<bool>(isCompleted);
    if (!nullToAbsent || completedAt != null) {
      map['completed_at'] = Variable<DateTime>(completedAt);
    }
    map['weight'] = Variable<int>(weight);
    if (!nullToAbsent || photoPath != null) {
      map['photo_path'] = Variable<String>(photoPath);
    }
    map['note'] = Variable<String>(note);
    if (!nullToAbsent || reminderAt != null) {
      map['reminder_at'] = Variable<DateTime>(reminderAt);
    }
    map['sort_order'] = Variable<int>(sortOrder);
    return map;
  }

  DreamMilestonesCompanion toCompanion(bool nullToAbsent) {
    return DreamMilestonesCompanion(
      id: Value(id),
      dreamId: Value(dreamId),
      title: Value(title),
      description: Value(description),
      targetDate: targetDate == null && nullToAbsent
          ? const Value.absent()
          : Value(targetDate),
      isCompleted: Value(isCompleted),
      completedAt: completedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(completedAt),
      weight: Value(weight),
      photoPath: photoPath == null && nullToAbsent
          ? const Value.absent()
          : Value(photoPath),
      note: Value(note),
      reminderAt: reminderAt == null && nullToAbsent
          ? const Value.absent()
          : Value(reminderAt),
      sortOrder: Value(sortOrder),
    );
  }

  factory DreamMilestone.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return DreamMilestone(
      id: serializer.fromJson<int>(json['id']),
      dreamId: serializer.fromJson<int>(json['dreamId']),
      title: serializer.fromJson<String>(json['title']),
      description: serializer.fromJson<String>(json['description']),
      targetDate: serializer.fromJson<DateTime?>(json['targetDate']),
      isCompleted: serializer.fromJson<bool>(json['isCompleted']),
      completedAt: serializer.fromJson<DateTime?>(json['completedAt']),
      weight: serializer.fromJson<int>(json['weight']),
      photoPath: serializer.fromJson<String?>(json['photoPath']),
      note: serializer.fromJson<String>(json['note']),
      reminderAt: serializer.fromJson<DateTime?>(json['reminderAt']),
      sortOrder: serializer.fromJson<int>(json['sortOrder']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'dreamId': serializer.toJson<int>(dreamId),
      'title': serializer.toJson<String>(title),
      'description': serializer.toJson<String>(description),
      'targetDate': serializer.toJson<DateTime?>(targetDate),
      'isCompleted': serializer.toJson<bool>(isCompleted),
      'completedAt': serializer.toJson<DateTime?>(completedAt),
      'weight': serializer.toJson<int>(weight),
      'photoPath': serializer.toJson<String?>(photoPath),
      'note': serializer.toJson<String>(note),
      'reminderAt': serializer.toJson<DateTime?>(reminderAt),
      'sortOrder': serializer.toJson<int>(sortOrder),
    };
  }

  DreamMilestone copyWith({
    int? id,
    int? dreamId,
    String? title,
    String? description,
    Value<DateTime?> targetDate = const Value.absent(),
    bool? isCompleted,
    Value<DateTime?> completedAt = const Value.absent(),
    int? weight,
    Value<String?> photoPath = const Value.absent(),
    String? note,
    Value<DateTime?> reminderAt = const Value.absent(),
    int? sortOrder,
  }) => DreamMilestone(
    id: id ?? this.id,
    dreamId: dreamId ?? this.dreamId,
    title: title ?? this.title,
    description: description ?? this.description,
    targetDate: targetDate.present ? targetDate.value : this.targetDate,
    isCompleted: isCompleted ?? this.isCompleted,
    completedAt: completedAt.present ? completedAt.value : this.completedAt,
    weight: weight ?? this.weight,
    photoPath: photoPath.present ? photoPath.value : this.photoPath,
    note: note ?? this.note,
    reminderAt: reminderAt.present ? reminderAt.value : this.reminderAt,
    sortOrder: sortOrder ?? this.sortOrder,
  );
  DreamMilestone copyWithCompanion(DreamMilestonesCompanion data) {
    return DreamMilestone(
      id: data.id.present ? data.id.value : this.id,
      dreamId: data.dreamId.present ? data.dreamId.value : this.dreamId,
      title: data.title.present ? data.title.value : this.title,
      description: data.description.present
          ? data.description.value
          : this.description,
      targetDate: data.targetDate.present
          ? data.targetDate.value
          : this.targetDate,
      isCompleted: data.isCompleted.present
          ? data.isCompleted.value
          : this.isCompleted,
      completedAt: data.completedAt.present
          ? data.completedAt.value
          : this.completedAt,
      weight: data.weight.present ? data.weight.value : this.weight,
      photoPath: data.photoPath.present ? data.photoPath.value : this.photoPath,
      note: data.note.present ? data.note.value : this.note,
      reminderAt: data.reminderAt.present
          ? data.reminderAt.value
          : this.reminderAt,
      sortOrder: data.sortOrder.present ? data.sortOrder.value : this.sortOrder,
    );
  }

  @override
  String toString() {
    return (StringBuffer('DreamMilestone(')
          ..write('id: $id, ')
          ..write('dreamId: $dreamId, ')
          ..write('title: $title, ')
          ..write('description: $description, ')
          ..write('targetDate: $targetDate, ')
          ..write('isCompleted: $isCompleted, ')
          ..write('completedAt: $completedAt, ')
          ..write('weight: $weight, ')
          ..write('photoPath: $photoPath, ')
          ..write('note: $note, ')
          ..write('reminderAt: $reminderAt, ')
          ..write('sortOrder: $sortOrder')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    dreamId,
    title,
    description,
    targetDate,
    isCompleted,
    completedAt,
    weight,
    photoPath,
    note,
    reminderAt,
    sortOrder,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is DreamMilestone &&
          other.id == this.id &&
          other.dreamId == this.dreamId &&
          other.title == this.title &&
          other.description == this.description &&
          other.targetDate == this.targetDate &&
          other.isCompleted == this.isCompleted &&
          other.completedAt == this.completedAt &&
          other.weight == this.weight &&
          other.photoPath == this.photoPath &&
          other.note == this.note &&
          other.reminderAt == this.reminderAt &&
          other.sortOrder == this.sortOrder);
}

class DreamMilestonesCompanion extends UpdateCompanion<DreamMilestone> {
  final Value<int> id;
  final Value<int> dreamId;
  final Value<String> title;
  final Value<String> description;
  final Value<DateTime?> targetDate;
  final Value<bool> isCompleted;
  final Value<DateTime?> completedAt;
  final Value<int> weight;
  final Value<String?> photoPath;
  final Value<String> note;
  final Value<DateTime?> reminderAt;
  final Value<int> sortOrder;
  const DreamMilestonesCompanion({
    this.id = const Value.absent(),
    this.dreamId = const Value.absent(),
    this.title = const Value.absent(),
    this.description = const Value.absent(),
    this.targetDate = const Value.absent(),
    this.isCompleted = const Value.absent(),
    this.completedAt = const Value.absent(),
    this.weight = const Value.absent(),
    this.photoPath = const Value.absent(),
    this.note = const Value.absent(),
    this.reminderAt = const Value.absent(),
    this.sortOrder = const Value.absent(),
  });
  DreamMilestonesCompanion.insert({
    this.id = const Value.absent(),
    required int dreamId,
    required String title,
    this.description = const Value.absent(),
    this.targetDate = const Value.absent(),
    this.isCompleted = const Value.absent(),
    this.completedAt = const Value.absent(),
    this.weight = const Value.absent(),
    this.photoPath = const Value.absent(),
    this.note = const Value.absent(),
    this.reminderAt = const Value.absent(),
    this.sortOrder = const Value.absent(),
  }) : dreamId = Value(dreamId),
       title = Value(title);
  static Insertable<DreamMilestone> custom({
    Expression<int>? id,
    Expression<int>? dreamId,
    Expression<String>? title,
    Expression<String>? description,
    Expression<DateTime>? targetDate,
    Expression<bool>? isCompleted,
    Expression<DateTime>? completedAt,
    Expression<int>? weight,
    Expression<String>? photoPath,
    Expression<String>? note,
    Expression<DateTime>? reminderAt,
    Expression<int>? sortOrder,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (dreamId != null) 'dream_id': dreamId,
      if (title != null) 'title': title,
      if (description != null) 'description': description,
      if (targetDate != null) 'target_date': targetDate,
      if (isCompleted != null) 'is_completed': isCompleted,
      if (completedAt != null) 'completed_at': completedAt,
      if (weight != null) 'weight': weight,
      if (photoPath != null) 'photo_path': photoPath,
      if (note != null) 'note': note,
      if (reminderAt != null) 'reminder_at': reminderAt,
      if (sortOrder != null) 'sort_order': sortOrder,
    });
  }

  DreamMilestonesCompanion copyWith({
    Value<int>? id,
    Value<int>? dreamId,
    Value<String>? title,
    Value<String>? description,
    Value<DateTime?>? targetDate,
    Value<bool>? isCompleted,
    Value<DateTime?>? completedAt,
    Value<int>? weight,
    Value<String?>? photoPath,
    Value<String>? note,
    Value<DateTime?>? reminderAt,
    Value<int>? sortOrder,
  }) {
    return DreamMilestonesCompanion(
      id: id ?? this.id,
      dreamId: dreamId ?? this.dreamId,
      title: title ?? this.title,
      description: description ?? this.description,
      targetDate: targetDate ?? this.targetDate,
      isCompleted: isCompleted ?? this.isCompleted,
      completedAt: completedAt ?? this.completedAt,
      weight: weight ?? this.weight,
      photoPath: photoPath ?? this.photoPath,
      note: note ?? this.note,
      reminderAt: reminderAt ?? this.reminderAt,
      sortOrder: sortOrder ?? this.sortOrder,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (dreamId.present) {
      map['dream_id'] = Variable<int>(dreamId.value);
    }
    if (title.present) {
      map['title'] = Variable<String>(title.value);
    }
    if (description.present) {
      map['description'] = Variable<String>(description.value);
    }
    if (targetDate.present) {
      map['target_date'] = Variable<DateTime>(targetDate.value);
    }
    if (isCompleted.present) {
      map['is_completed'] = Variable<bool>(isCompleted.value);
    }
    if (completedAt.present) {
      map['completed_at'] = Variable<DateTime>(completedAt.value);
    }
    if (weight.present) {
      map['weight'] = Variable<int>(weight.value);
    }
    if (photoPath.present) {
      map['photo_path'] = Variable<String>(photoPath.value);
    }
    if (note.present) {
      map['note'] = Variable<String>(note.value);
    }
    if (reminderAt.present) {
      map['reminder_at'] = Variable<DateTime>(reminderAt.value);
    }
    if (sortOrder.present) {
      map['sort_order'] = Variable<int>(sortOrder.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('DreamMilestonesCompanion(')
          ..write('id: $id, ')
          ..write('dreamId: $dreamId, ')
          ..write('title: $title, ')
          ..write('description: $description, ')
          ..write('targetDate: $targetDate, ')
          ..write('isCompleted: $isCompleted, ')
          ..write('completedAt: $completedAt, ')
          ..write('weight: $weight, ')
          ..write('photoPath: $photoPath, ')
          ..write('note: $note, ')
          ..write('reminderAt: $reminderAt, ')
          ..write('sortOrder: $sortOrder')
          ..write(')'))
        .toString();
  }
}

class $DreamMediaTable extends DreamMedia
    with TableInfo<$DreamMediaTable, DreamMediaItem> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $DreamMediaTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _dreamIdMeta = const VerificationMeta(
    'dreamId',
  );
  @override
  late final GeneratedColumn<int> dreamId = GeneratedColumn<int>(
    'dream_id',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES dreams (id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _kindMeta = const VerificationMeta('kind');
  @override
  late final GeneratedColumn<String> kind = GeneratedColumn<String>(
    'kind',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('image'),
  );
  static const VerificationMeta _pathMeta = const VerificationMeta('path');
  @override
  late final GeneratedColumn<String> path = GeneratedColumn<String>(
    'path',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _captionMeta = const VerificationMeta(
    'caption',
  );
  @override
  late final GeneratedColumn<String> caption = GeneratedColumn<String>(
    'caption',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant(''),
  );
  static const VerificationMeta _boardCategoryMeta = const VerificationMeta(
    'boardCategory',
  );
  @override
  late final GeneratedColumn<String> boardCategory = GeneratedColumn<String>(
    'board_category',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('Genel'),
  );
  static const VerificationMeta _showOnBoardMeta = const VerificationMeta(
    'showOnBoard',
  );
  @override
  late final GeneratedColumn<bool> showOnBoard = GeneratedColumn<bool>(
    'show_on_board',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("show_on_board" IN (0, 1))',
    ),
    defaultValue: const Constant(true),
  );
  static const VerificationMeta _colorMeta = const VerificationMeta('color');
  @override
  late final GeneratedColumn<int> color = GeneratedColumn<int>(
    'color',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _sortOrderMeta = const VerificationMeta(
    'sortOrder',
  );
  @override
  late final GeneratedColumn<int> sortOrder = GeneratedColumn<int>(
    'sort_order',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    dreamId,
    kind,
    path,
    caption,
    boardCategory,
    showOnBoard,
    color,
    sortOrder,
    createdAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'dream_media';
  @override
  VerificationContext validateIntegrity(
    Insertable<DreamMediaItem> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('dream_id')) {
      context.handle(
        _dreamIdMeta,
        dreamId.isAcceptableOrUnknown(data['dream_id']!, _dreamIdMeta),
      );
    }
    if (data.containsKey('kind')) {
      context.handle(
        _kindMeta,
        kind.isAcceptableOrUnknown(data['kind']!, _kindMeta),
      );
    }
    if (data.containsKey('path')) {
      context.handle(
        _pathMeta,
        path.isAcceptableOrUnknown(data['path']!, _pathMeta),
      );
    }
    if (data.containsKey('caption')) {
      context.handle(
        _captionMeta,
        caption.isAcceptableOrUnknown(data['caption']!, _captionMeta),
      );
    }
    if (data.containsKey('board_category')) {
      context.handle(
        _boardCategoryMeta,
        boardCategory.isAcceptableOrUnknown(
          data['board_category']!,
          _boardCategoryMeta,
        ),
      );
    }
    if (data.containsKey('show_on_board')) {
      context.handle(
        _showOnBoardMeta,
        showOnBoard.isAcceptableOrUnknown(
          data['show_on_board']!,
          _showOnBoardMeta,
        ),
      );
    }
    if (data.containsKey('color')) {
      context.handle(
        _colorMeta,
        color.isAcceptableOrUnknown(data['color']!, _colorMeta),
      );
    }
    if (data.containsKey('sort_order')) {
      context.handle(
        _sortOrderMeta,
        sortOrder.isAcceptableOrUnknown(data['sort_order']!, _sortOrderMeta),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  DreamMediaItem map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return DreamMediaItem(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      dreamId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}dream_id'],
      ),
      kind: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}kind'],
      )!,
      path: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}path'],
      ),
      caption: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}caption'],
      )!,
      boardCategory: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}board_category'],
      )!,
      showOnBoard: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}show_on_board'],
      )!,
      color: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}color'],
      ),
      sortOrder: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}sort_order'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
    );
  }

  @override
  $DreamMediaTable createAlias(String alias) {
    return $DreamMediaTable(attachedDatabase, alias);
  }
}

class DreamMediaItem extends DataClass implements Insertable<DreamMediaItem> {
  final int id;
  final int? dreamId;
  final String kind;
  final String? path;
  final String caption;
  final String boardCategory;
  final bool showOnBoard;
  final int? color;
  final int sortOrder;
  final DateTime createdAt;
  const DreamMediaItem({
    required this.id,
    this.dreamId,
    required this.kind,
    this.path,
    required this.caption,
    required this.boardCategory,
    required this.showOnBoard,
    this.color,
    required this.sortOrder,
    required this.createdAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    if (!nullToAbsent || dreamId != null) {
      map['dream_id'] = Variable<int>(dreamId);
    }
    map['kind'] = Variable<String>(kind);
    if (!nullToAbsent || path != null) {
      map['path'] = Variable<String>(path);
    }
    map['caption'] = Variable<String>(caption);
    map['board_category'] = Variable<String>(boardCategory);
    map['show_on_board'] = Variable<bool>(showOnBoard);
    if (!nullToAbsent || color != null) {
      map['color'] = Variable<int>(color);
    }
    map['sort_order'] = Variable<int>(sortOrder);
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  DreamMediaCompanion toCompanion(bool nullToAbsent) {
    return DreamMediaCompanion(
      id: Value(id),
      dreamId: dreamId == null && nullToAbsent
          ? const Value.absent()
          : Value(dreamId),
      kind: Value(kind),
      path: path == null && nullToAbsent ? const Value.absent() : Value(path),
      caption: Value(caption),
      boardCategory: Value(boardCategory),
      showOnBoard: Value(showOnBoard),
      color: color == null && nullToAbsent
          ? const Value.absent()
          : Value(color),
      sortOrder: Value(sortOrder),
      createdAt: Value(createdAt),
    );
  }

  factory DreamMediaItem.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return DreamMediaItem(
      id: serializer.fromJson<int>(json['id']),
      dreamId: serializer.fromJson<int?>(json['dreamId']),
      kind: serializer.fromJson<String>(json['kind']),
      path: serializer.fromJson<String?>(json['path']),
      caption: serializer.fromJson<String>(json['caption']),
      boardCategory: serializer.fromJson<String>(json['boardCategory']),
      showOnBoard: serializer.fromJson<bool>(json['showOnBoard']),
      color: serializer.fromJson<int?>(json['color']),
      sortOrder: serializer.fromJson<int>(json['sortOrder']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'dreamId': serializer.toJson<int?>(dreamId),
      'kind': serializer.toJson<String>(kind),
      'path': serializer.toJson<String?>(path),
      'caption': serializer.toJson<String>(caption),
      'boardCategory': serializer.toJson<String>(boardCategory),
      'showOnBoard': serializer.toJson<bool>(showOnBoard),
      'color': serializer.toJson<int?>(color),
      'sortOrder': serializer.toJson<int>(sortOrder),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  DreamMediaItem copyWith({
    int? id,
    Value<int?> dreamId = const Value.absent(),
    String? kind,
    Value<String?> path = const Value.absent(),
    String? caption,
    String? boardCategory,
    bool? showOnBoard,
    Value<int?> color = const Value.absent(),
    int? sortOrder,
    DateTime? createdAt,
  }) => DreamMediaItem(
    id: id ?? this.id,
    dreamId: dreamId.present ? dreamId.value : this.dreamId,
    kind: kind ?? this.kind,
    path: path.present ? path.value : this.path,
    caption: caption ?? this.caption,
    boardCategory: boardCategory ?? this.boardCategory,
    showOnBoard: showOnBoard ?? this.showOnBoard,
    color: color.present ? color.value : this.color,
    sortOrder: sortOrder ?? this.sortOrder,
    createdAt: createdAt ?? this.createdAt,
  );
  DreamMediaItem copyWithCompanion(DreamMediaCompanion data) {
    return DreamMediaItem(
      id: data.id.present ? data.id.value : this.id,
      dreamId: data.dreamId.present ? data.dreamId.value : this.dreamId,
      kind: data.kind.present ? data.kind.value : this.kind,
      path: data.path.present ? data.path.value : this.path,
      caption: data.caption.present ? data.caption.value : this.caption,
      boardCategory: data.boardCategory.present
          ? data.boardCategory.value
          : this.boardCategory,
      showOnBoard: data.showOnBoard.present
          ? data.showOnBoard.value
          : this.showOnBoard,
      color: data.color.present ? data.color.value : this.color,
      sortOrder: data.sortOrder.present ? data.sortOrder.value : this.sortOrder,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('DreamMediaItem(')
          ..write('id: $id, ')
          ..write('dreamId: $dreamId, ')
          ..write('kind: $kind, ')
          ..write('path: $path, ')
          ..write('caption: $caption, ')
          ..write('boardCategory: $boardCategory, ')
          ..write('showOnBoard: $showOnBoard, ')
          ..write('color: $color, ')
          ..write('sortOrder: $sortOrder, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    dreamId,
    kind,
    path,
    caption,
    boardCategory,
    showOnBoard,
    color,
    sortOrder,
    createdAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is DreamMediaItem &&
          other.id == this.id &&
          other.dreamId == this.dreamId &&
          other.kind == this.kind &&
          other.path == this.path &&
          other.caption == this.caption &&
          other.boardCategory == this.boardCategory &&
          other.showOnBoard == this.showOnBoard &&
          other.color == this.color &&
          other.sortOrder == this.sortOrder &&
          other.createdAt == this.createdAt);
}

class DreamMediaCompanion extends UpdateCompanion<DreamMediaItem> {
  final Value<int> id;
  final Value<int?> dreamId;
  final Value<String> kind;
  final Value<String?> path;
  final Value<String> caption;
  final Value<String> boardCategory;
  final Value<bool> showOnBoard;
  final Value<int?> color;
  final Value<int> sortOrder;
  final Value<DateTime> createdAt;
  const DreamMediaCompanion({
    this.id = const Value.absent(),
    this.dreamId = const Value.absent(),
    this.kind = const Value.absent(),
    this.path = const Value.absent(),
    this.caption = const Value.absent(),
    this.boardCategory = const Value.absent(),
    this.showOnBoard = const Value.absent(),
    this.color = const Value.absent(),
    this.sortOrder = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  DreamMediaCompanion.insert({
    this.id = const Value.absent(),
    this.dreamId = const Value.absent(),
    this.kind = const Value.absent(),
    this.path = const Value.absent(),
    this.caption = const Value.absent(),
    this.boardCategory = const Value.absent(),
    this.showOnBoard = const Value.absent(),
    this.color = const Value.absent(),
    this.sortOrder = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  static Insertable<DreamMediaItem> custom({
    Expression<int>? id,
    Expression<int>? dreamId,
    Expression<String>? kind,
    Expression<String>? path,
    Expression<String>? caption,
    Expression<String>? boardCategory,
    Expression<bool>? showOnBoard,
    Expression<int>? color,
    Expression<int>? sortOrder,
    Expression<DateTime>? createdAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (dreamId != null) 'dream_id': dreamId,
      if (kind != null) 'kind': kind,
      if (path != null) 'path': path,
      if (caption != null) 'caption': caption,
      if (boardCategory != null) 'board_category': boardCategory,
      if (showOnBoard != null) 'show_on_board': showOnBoard,
      if (color != null) 'color': color,
      if (sortOrder != null) 'sort_order': sortOrder,
      if (createdAt != null) 'created_at': createdAt,
    });
  }

  DreamMediaCompanion copyWith({
    Value<int>? id,
    Value<int?>? dreamId,
    Value<String>? kind,
    Value<String?>? path,
    Value<String>? caption,
    Value<String>? boardCategory,
    Value<bool>? showOnBoard,
    Value<int?>? color,
    Value<int>? sortOrder,
    Value<DateTime>? createdAt,
  }) {
    return DreamMediaCompanion(
      id: id ?? this.id,
      dreamId: dreamId ?? this.dreamId,
      kind: kind ?? this.kind,
      path: path ?? this.path,
      caption: caption ?? this.caption,
      boardCategory: boardCategory ?? this.boardCategory,
      showOnBoard: showOnBoard ?? this.showOnBoard,
      color: color ?? this.color,
      sortOrder: sortOrder ?? this.sortOrder,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (dreamId.present) {
      map['dream_id'] = Variable<int>(dreamId.value);
    }
    if (kind.present) {
      map['kind'] = Variable<String>(kind.value);
    }
    if (path.present) {
      map['path'] = Variable<String>(path.value);
    }
    if (caption.present) {
      map['caption'] = Variable<String>(caption.value);
    }
    if (boardCategory.present) {
      map['board_category'] = Variable<String>(boardCategory.value);
    }
    if (showOnBoard.present) {
      map['show_on_board'] = Variable<bool>(showOnBoard.value);
    }
    if (color.present) {
      map['color'] = Variable<int>(color.value);
    }
    if (sortOrder.present) {
      map['sort_order'] = Variable<int>(sortOrder.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('DreamMediaCompanion(')
          ..write('id: $id, ')
          ..write('dreamId: $dreamId, ')
          ..write('kind: $kind, ')
          ..write('path: $path, ')
          ..write('caption: $caption, ')
          ..write('boardCategory: $boardCategory, ')
          ..write('showOnBoard: $showOnBoard, ')
          ..write('color: $color, ')
          ..write('sortOrder: $sortOrder, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }
}

class $GoalsTable extends Goals with TableInfo<$GoalsTable, Goal> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $GoalsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _titleMeta = const VerificationMeta('title');
  @override
  late final GeneratedColumn<String> title = GeneratedColumn<String>(
    'title',
    aliasedName,
    false,
    additionalChecks: GeneratedColumn.checkTextLength(
      minTextLength: 1,
      maxTextLength: 160,
    ),
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _descriptionMeta = const VerificationMeta(
    'description',
  );
  @override
  late final GeneratedColumn<String> description = GeneratedColumn<String>(
    'description',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant(''),
  );
  static const VerificationMeta _periodMeta = const VerificationMeta('period');
  @override
  late final GeneratedColumn<String> period = GeneratedColumn<String>(
    'period',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('monthly'),
  );
  static const VerificationMeta _dreamIdMeta = const VerificationMeta(
    'dreamId',
  );
  @override
  late final GeneratedColumn<int> dreamId = GeneratedColumn<int>(
    'dream_id',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES dreams (id) ON DELETE SET NULL',
    ),
  );
  static const VerificationMeta _priorityMeta = const VerificationMeta(
    'priority',
  );
  @override
  late final GeneratedColumn<int> priority = GeneratedColumn<int>(
    'priority',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(1),
  );
  static const VerificationMeta _startDateMeta = const VerificationMeta(
    'startDate',
  );
  @override
  late final GeneratedColumn<DateTime> startDate = GeneratedColumn<DateTime>(
    'start_date',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  static const VerificationMeta _dueDateMeta = const VerificationMeta(
    'dueDate',
  );
  @override
  late final GeneratedColumn<DateTime> dueDate = GeneratedColumn<DateTime>(
    'due_date',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _isCompletedMeta = const VerificationMeta(
    'isCompleted',
  );
  @override
  late final GeneratedColumn<bool> isCompleted = GeneratedColumn<bool>(
    'is_completed',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("is_completed" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _completedAtMeta = const VerificationMeta(
    'completedAt',
  );
  @override
  late final GeneratedColumn<DateTime> completedAt = GeneratedColumn<DateTime>(
    'completed_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _archivedAtMeta = const VerificationMeta(
    'archivedAt',
  );
  @override
  late final GeneratedColumn<DateTime> archivedAt = GeneratedColumn<DateTime>(
    'archived_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    title,
    description,
    period,
    dreamId,
    priority,
    startDate,
    dueDate,
    isCompleted,
    completedAt,
    archivedAt,
    createdAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'goals';
  @override
  VerificationContext validateIntegrity(
    Insertable<Goal> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('title')) {
      context.handle(
        _titleMeta,
        title.isAcceptableOrUnknown(data['title']!, _titleMeta),
      );
    } else if (isInserting) {
      context.missing(_titleMeta);
    }
    if (data.containsKey('description')) {
      context.handle(
        _descriptionMeta,
        description.isAcceptableOrUnknown(
          data['description']!,
          _descriptionMeta,
        ),
      );
    }
    if (data.containsKey('period')) {
      context.handle(
        _periodMeta,
        period.isAcceptableOrUnknown(data['period']!, _periodMeta),
      );
    }
    if (data.containsKey('dream_id')) {
      context.handle(
        _dreamIdMeta,
        dreamId.isAcceptableOrUnknown(data['dream_id']!, _dreamIdMeta),
      );
    }
    if (data.containsKey('priority')) {
      context.handle(
        _priorityMeta,
        priority.isAcceptableOrUnknown(data['priority']!, _priorityMeta),
      );
    }
    if (data.containsKey('start_date')) {
      context.handle(
        _startDateMeta,
        startDate.isAcceptableOrUnknown(data['start_date']!, _startDateMeta),
      );
    }
    if (data.containsKey('due_date')) {
      context.handle(
        _dueDateMeta,
        dueDate.isAcceptableOrUnknown(data['due_date']!, _dueDateMeta),
      );
    }
    if (data.containsKey('is_completed')) {
      context.handle(
        _isCompletedMeta,
        isCompleted.isAcceptableOrUnknown(
          data['is_completed']!,
          _isCompletedMeta,
        ),
      );
    }
    if (data.containsKey('completed_at')) {
      context.handle(
        _completedAtMeta,
        completedAt.isAcceptableOrUnknown(
          data['completed_at']!,
          _completedAtMeta,
        ),
      );
    }
    if (data.containsKey('archived_at')) {
      context.handle(
        _archivedAtMeta,
        archivedAt.isAcceptableOrUnknown(data['archived_at']!, _archivedAtMeta),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  Goal map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return Goal(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      title: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}title'],
      )!,
      description: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}description'],
      )!,
      period: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}period'],
      )!,
      dreamId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}dream_id'],
      ),
      priority: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}priority'],
      )!,
      startDate: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}start_date'],
      )!,
      dueDate: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}due_date'],
      ),
      isCompleted: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}is_completed'],
      )!,
      completedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}completed_at'],
      ),
      archivedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}archived_at'],
      ),
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
    );
  }

  @override
  $GoalsTable createAlias(String alias) {
    return $GoalsTable(attachedDatabase, alias);
  }
}

class Goal extends DataClass implements Insertable<Goal> {
  final int id;
  final String title;
  final String description;
  final String period;
  final int? dreamId;
  final int priority;
  final DateTime startDate;
  final DateTime? dueDate;
  final bool isCompleted;
  final DateTime? completedAt;
  final DateTime? archivedAt;
  final DateTime createdAt;
  const Goal({
    required this.id,
    required this.title,
    required this.description,
    required this.period,
    this.dreamId,
    required this.priority,
    required this.startDate,
    this.dueDate,
    required this.isCompleted,
    this.completedAt,
    this.archivedAt,
    required this.createdAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['title'] = Variable<String>(title);
    map['description'] = Variable<String>(description);
    map['period'] = Variable<String>(period);
    if (!nullToAbsent || dreamId != null) {
      map['dream_id'] = Variable<int>(dreamId);
    }
    map['priority'] = Variable<int>(priority);
    map['start_date'] = Variable<DateTime>(startDate);
    if (!nullToAbsent || dueDate != null) {
      map['due_date'] = Variable<DateTime>(dueDate);
    }
    map['is_completed'] = Variable<bool>(isCompleted);
    if (!nullToAbsent || completedAt != null) {
      map['completed_at'] = Variable<DateTime>(completedAt);
    }
    if (!nullToAbsent || archivedAt != null) {
      map['archived_at'] = Variable<DateTime>(archivedAt);
    }
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  GoalsCompanion toCompanion(bool nullToAbsent) {
    return GoalsCompanion(
      id: Value(id),
      title: Value(title),
      description: Value(description),
      period: Value(period),
      dreamId: dreamId == null && nullToAbsent
          ? const Value.absent()
          : Value(dreamId),
      priority: Value(priority),
      startDate: Value(startDate),
      dueDate: dueDate == null && nullToAbsent
          ? const Value.absent()
          : Value(dueDate),
      isCompleted: Value(isCompleted),
      completedAt: completedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(completedAt),
      archivedAt: archivedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(archivedAt),
      createdAt: Value(createdAt),
    );
  }

  factory Goal.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return Goal(
      id: serializer.fromJson<int>(json['id']),
      title: serializer.fromJson<String>(json['title']),
      description: serializer.fromJson<String>(json['description']),
      period: serializer.fromJson<String>(json['period']),
      dreamId: serializer.fromJson<int?>(json['dreamId']),
      priority: serializer.fromJson<int>(json['priority']),
      startDate: serializer.fromJson<DateTime>(json['startDate']),
      dueDate: serializer.fromJson<DateTime?>(json['dueDate']),
      isCompleted: serializer.fromJson<bool>(json['isCompleted']),
      completedAt: serializer.fromJson<DateTime?>(json['completedAt']),
      archivedAt: serializer.fromJson<DateTime?>(json['archivedAt']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'title': serializer.toJson<String>(title),
      'description': serializer.toJson<String>(description),
      'period': serializer.toJson<String>(period),
      'dreamId': serializer.toJson<int?>(dreamId),
      'priority': serializer.toJson<int>(priority),
      'startDate': serializer.toJson<DateTime>(startDate),
      'dueDate': serializer.toJson<DateTime?>(dueDate),
      'isCompleted': serializer.toJson<bool>(isCompleted),
      'completedAt': serializer.toJson<DateTime?>(completedAt),
      'archivedAt': serializer.toJson<DateTime?>(archivedAt),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  Goal copyWith({
    int? id,
    String? title,
    String? description,
    String? period,
    Value<int?> dreamId = const Value.absent(),
    int? priority,
    DateTime? startDate,
    Value<DateTime?> dueDate = const Value.absent(),
    bool? isCompleted,
    Value<DateTime?> completedAt = const Value.absent(),
    Value<DateTime?> archivedAt = const Value.absent(),
    DateTime? createdAt,
  }) => Goal(
    id: id ?? this.id,
    title: title ?? this.title,
    description: description ?? this.description,
    period: period ?? this.period,
    dreamId: dreamId.present ? dreamId.value : this.dreamId,
    priority: priority ?? this.priority,
    startDate: startDate ?? this.startDate,
    dueDate: dueDate.present ? dueDate.value : this.dueDate,
    isCompleted: isCompleted ?? this.isCompleted,
    completedAt: completedAt.present ? completedAt.value : this.completedAt,
    archivedAt: archivedAt.present ? archivedAt.value : this.archivedAt,
    createdAt: createdAt ?? this.createdAt,
  );
  Goal copyWithCompanion(GoalsCompanion data) {
    return Goal(
      id: data.id.present ? data.id.value : this.id,
      title: data.title.present ? data.title.value : this.title,
      description: data.description.present
          ? data.description.value
          : this.description,
      period: data.period.present ? data.period.value : this.period,
      dreamId: data.dreamId.present ? data.dreamId.value : this.dreamId,
      priority: data.priority.present ? data.priority.value : this.priority,
      startDate: data.startDate.present ? data.startDate.value : this.startDate,
      dueDate: data.dueDate.present ? data.dueDate.value : this.dueDate,
      isCompleted: data.isCompleted.present
          ? data.isCompleted.value
          : this.isCompleted,
      completedAt: data.completedAt.present
          ? data.completedAt.value
          : this.completedAt,
      archivedAt: data.archivedAt.present
          ? data.archivedAt.value
          : this.archivedAt,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('Goal(')
          ..write('id: $id, ')
          ..write('title: $title, ')
          ..write('description: $description, ')
          ..write('period: $period, ')
          ..write('dreamId: $dreamId, ')
          ..write('priority: $priority, ')
          ..write('startDate: $startDate, ')
          ..write('dueDate: $dueDate, ')
          ..write('isCompleted: $isCompleted, ')
          ..write('completedAt: $completedAt, ')
          ..write('archivedAt: $archivedAt, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    title,
    description,
    period,
    dreamId,
    priority,
    startDate,
    dueDate,
    isCompleted,
    completedAt,
    archivedAt,
    createdAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is Goal &&
          other.id == this.id &&
          other.title == this.title &&
          other.description == this.description &&
          other.period == this.period &&
          other.dreamId == this.dreamId &&
          other.priority == this.priority &&
          other.startDate == this.startDate &&
          other.dueDate == this.dueDate &&
          other.isCompleted == this.isCompleted &&
          other.completedAt == this.completedAt &&
          other.archivedAt == this.archivedAt &&
          other.createdAt == this.createdAt);
}

class GoalsCompanion extends UpdateCompanion<Goal> {
  final Value<int> id;
  final Value<String> title;
  final Value<String> description;
  final Value<String> period;
  final Value<int?> dreamId;
  final Value<int> priority;
  final Value<DateTime> startDate;
  final Value<DateTime?> dueDate;
  final Value<bool> isCompleted;
  final Value<DateTime?> completedAt;
  final Value<DateTime?> archivedAt;
  final Value<DateTime> createdAt;
  const GoalsCompanion({
    this.id = const Value.absent(),
    this.title = const Value.absent(),
    this.description = const Value.absent(),
    this.period = const Value.absent(),
    this.dreamId = const Value.absent(),
    this.priority = const Value.absent(),
    this.startDate = const Value.absent(),
    this.dueDate = const Value.absent(),
    this.isCompleted = const Value.absent(),
    this.completedAt = const Value.absent(),
    this.archivedAt = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  GoalsCompanion.insert({
    this.id = const Value.absent(),
    required String title,
    this.description = const Value.absent(),
    this.period = const Value.absent(),
    this.dreamId = const Value.absent(),
    this.priority = const Value.absent(),
    this.startDate = const Value.absent(),
    this.dueDate = const Value.absent(),
    this.isCompleted = const Value.absent(),
    this.completedAt = const Value.absent(),
    this.archivedAt = const Value.absent(),
    this.createdAt = const Value.absent(),
  }) : title = Value(title);
  static Insertable<Goal> custom({
    Expression<int>? id,
    Expression<String>? title,
    Expression<String>? description,
    Expression<String>? period,
    Expression<int>? dreamId,
    Expression<int>? priority,
    Expression<DateTime>? startDate,
    Expression<DateTime>? dueDate,
    Expression<bool>? isCompleted,
    Expression<DateTime>? completedAt,
    Expression<DateTime>? archivedAt,
    Expression<DateTime>? createdAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (title != null) 'title': title,
      if (description != null) 'description': description,
      if (period != null) 'period': period,
      if (dreamId != null) 'dream_id': dreamId,
      if (priority != null) 'priority': priority,
      if (startDate != null) 'start_date': startDate,
      if (dueDate != null) 'due_date': dueDate,
      if (isCompleted != null) 'is_completed': isCompleted,
      if (completedAt != null) 'completed_at': completedAt,
      if (archivedAt != null) 'archived_at': archivedAt,
      if (createdAt != null) 'created_at': createdAt,
    });
  }

  GoalsCompanion copyWith({
    Value<int>? id,
    Value<String>? title,
    Value<String>? description,
    Value<String>? period,
    Value<int?>? dreamId,
    Value<int>? priority,
    Value<DateTime>? startDate,
    Value<DateTime?>? dueDate,
    Value<bool>? isCompleted,
    Value<DateTime?>? completedAt,
    Value<DateTime?>? archivedAt,
    Value<DateTime>? createdAt,
  }) {
    return GoalsCompanion(
      id: id ?? this.id,
      title: title ?? this.title,
      description: description ?? this.description,
      period: period ?? this.period,
      dreamId: dreamId ?? this.dreamId,
      priority: priority ?? this.priority,
      startDate: startDate ?? this.startDate,
      dueDate: dueDate ?? this.dueDate,
      isCompleted: isCompleted ?? this.isCompleted,
      completedAt: completedAt ?? this.completedAt,
      archivedAt: archivedAt ?? this.archivedAt,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (title.present) {
      map['title'] = Variable<String>(title.value);
    }
    if (description.present) {
      map['description'] = Variable<String>(description.value);
    }
    if (period.present) {
      map['period'] = Variable<String>(period.value);
    }
    if (dreamId.present) {
      map['dream_id'] = Variable<int>(dreamId.value);
    }
    if (priority.present) {
      map['priority'] = Variable<int>(priority.value);
    }
    if (startDate.present) {
      map['start_date'] = Variable<DateTime>(startDate.value);
    }
    if (dueDate.present) {
      map['due_date'] = Variable<DateTime>(dueDate.value);
    }
    if (isCompleted.present) {
      map['is_completed'] = Variable<bool>(isCompleted.value);
    }
    if (completedAt.present) {
      map['completed_at'] = Variable<DateTime>(completedAt.value);
    }
    if (archivedAt.present) {
      map['archived_at'] = Variable<DateTime>(archivedAt.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('GoalsCompanion(')
          ..write('id: $id, ')
          ..write('title: $title, ')
          ..write('description: $description, ')
          ..write('period: $period, ')
          ..write('dreamId: $dreamId, ')
          ..write('priority: $priority, ')
          ..write('startDate: $startDate, ')
          ..write('dueDate: $dueDate, ')
          ..write('isCompleted: $isCompleted, ')
          ..write('completedAt: $completedAt, ')
          ..write('archivedAt: $archivedAt, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }
}

class $TasksTable extends Tasks with TableInfo<$TasksTable, TaskItem> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $TasksTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _goalIdMeta = const VerificationMeta('goalId');
  @override
  late final GeneratedColumn<int> goalId = GeneratedColumn<int>(
    'goal_id',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES goals (id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _dreamIdMeta = const VerificationMeta(
    'dreamId',
  );
  @override
  late final GeneratedColumn<int> dreamId = GeneratedColumn<int>(
    'dream_id',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES dreams (id) ON DELETE SET NULL',
    ),
  );
  static const VerificationMeta _titleMeta = const VerificationMeta('title');
  @override
  late final GeneratedColumn<String> title = GeneratedColumn<String>(
    'title',
    aliasedName,
    false,
    additionalChecks: GeneratedColumn.checkTextLength(
      minTextLength: 1,
      maxTextLength: 160,
    ),
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _noteMeta = const VerificationMeta('note');
  @override
  late final GeneratedColumn<String> note = GeneratedColumn<String>(
    'note',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant(''),
  );
  static const VerificationMeta _dueDateMeta = const VerificationMeta(
    'dueDate',
  );
  @override
  late final GeneratedColumn<DateTime> dueDate = GeneratedColumn<DateTime>(
    'due_date',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _dueMinutesMeta = const VerificationMeta(
    'dueMinutes',
  );
  @override
  late final GeneratedColumn<int> dueMinutes = GeneratedColumn<int>(
    'due_minutes',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _priorityMeta = const VerificationMeta(
    'priority',
  );
  @override
  late final GeneratedColumn<int> priority = GeneratedColumn<int>(
    'priority',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(1),
  );
  static const VerificationMeta _repeatRuleMeta = const VerificationMeta(
    'repeatRule',
  );
  @override
  late final GeneratedColumn<String> repeatRule = GeneratedColumn<String>(
    'repeat_rule',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('none'),
  );
  static const VerificationMeta _isCompletedMeta = const VerificationMeta(
    'isCompleted',
  );
  @override
  late final GeneratedColumn<bool> isCompleted = GeneratedColumn<bool>(
    'is_completed',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("is_completed" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _completedAtMeta = const VerificationMeta(
    'completedAt',
  );
  @override
  late final GeneratedColumn<DateTime> completedAt = GeneratedColumn<DateTime>(
    'completed_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _reminderMeta = const VerificationMeta(
    'reminder',
  );
  @override
  late final GeneratedColumn<bool> reminder = GeneratedColumn<bool>(
    'reminder',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("reminder" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    goalId,
    dreamId,
    title,
    note,
    dueDate,
    dueMinutes,
    priority,
    repeatRule,
    isCompleted,
    completedAt,
    reminder,
    createdAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'tasks';
  @override
  VerificationContext validateIntegrity(
    Insertable<TaskItem> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('goal_id')) {
      context.handle(
        _goalIdMeta,
        goalId.isAcceptableOrUnknown(data['goal_id']!, _goalIdMeta),
      );
    }
    if (data.containsKey('dream_id')) {
      context.handle(
        _dreamIdMeta,
        dreamId.isAcceptableOrUnknown(data['dream_id']!, _dreamIdMeta),
      );
    }
    if (data.containsKey('title')) {
      context.handle(
        _titleMeta,
        title.isAcceptableOrUnknown(data['title']!, _titleMeta),
      );
    } else if (isInserting) {
      context.missing(_titleMeta);
    }
    if (data.containsKey('note')) {
      context.handle(
        _noteMeta,
        note.isAcceptableOrUnknown(data['note']!, _noteMeta),
      );
    }
    if (data.containsKey('due_date')) {
      context.handle(
        _dueDateMeta,
        dueDate.isAcceptableOrUnknown(data['due_date']!, _dueDateMeta),
      );
    }
    if (data.containsKey('due_minutes')) {
      context.handle(
        _dueMinutesMeta,
        dueMinutes.isAcceptableOrUnknown(data['due_minutes']!, _dueMinutesMeta),
      );
    }
    if (data.containsKey('priority')) {
      context.handle(
        _priorityMeta,
        priority.isAcceptableOrUnknown(data['priority']!, _priorityMeta),
      );
    }
    if (data.containsKey('repeat_rule')) {
      context.handle(
        _repeatRuleMeta,
        repeatRule.isAcceptableOrUnknown(data['repeat_rule']!, _repeatRuleMeta),
      );
    }
    if (data.containsKey('is_completed')) {
      context.handle(
        _isCompletedMeta,
        isCompleted.isAcceptableOrUnknown(
          data['is_completed']!,
          _isCompletedMeta,
        ),
      );
    }
    if (data.containsKey('completed_at')) {
      context.handle(
        _completedAtMeta,
        completedAt.isAcceptableOrUnknown(
          data['completed_at']!,
          _completedAtMeta,
        ),
      );
    }
    if (data.containsKey('reminder')) {
      context.handle(
        _reminderMeta,
        reminder.isAcceptableOrUnknown(data['reminder']!, _reminderMeta),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  TaskItem map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return TaskItem(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      goalId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}goal_id'],
      ),
      dreamId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}dream_id'],
      ),
      title: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}title'],
      )!,
      note: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}note'],
      )!,
      dueDate: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}due_date'],
      ),
      dueMinutes: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}due_minutes'],
      ),
      priority: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}priority'],
      )!,
      repeatRule: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}repeat_rule'],
      )!,
      isCompleted: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}is_completed'],
      )!,
      completedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}completed_at'],
      ),
      reminder: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}reminder'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
    );
  }

  @override
  $TasksTable createAlias(String alias) {
    return $TasksTable(attachedDatabase, alias);
  }
}

class TaskItem extends DataClass implements Insertable<TaskItem> {
  final int id;
  final int? goalId;
  final int? dreamId;
  final String title;
  final String note;
  final DateTime? dueDate;
  final int? dueMinutes;
  final int priority;
  final String repeatRule;
  final bool isCompleted;
  final DateTime? completedAt;
  final bool reminder;
  final DateTime createdAt;
  const TaskItem({
    required this.id,
    this.goalId,
    this.dreamId,
    required this.title,
    required this.note,
    this.dueDate,
    this.dueMinutes,
    required this.priority,
    required this.repeatRule,
    required this.isCompleted,
    this.completedAt,
    required this.reminder,
    required this.createdAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    if (!nullToAbsent || goalId != null) {
      map['goal_id'] = Variable<int>(goalId);
    }
    if (!nullToAbsent || dreamId != null) {
      map['dream_id'] = Variable<int>(dreamId);
    }
    map['title'] = Variable<String>(title);
    map['note'] = Variable<String>(note);
    if (!nullToAbsent || dueDate != null) {
      map['due_date'] = Variable<DateTime>(dueDate);
    }
    if (!nullToAbsent || dueMinutes != null) {
      map['due_minutes'] = Variable<int>(dueMinutes);
    }
    map['priority'] = Variable<int>(priority);
    map['repeat_rule'] = Variable<String>(repeatRule);
    map['is_completed'] = Variable<bool>(isCompleted);
    if (!nullToAbsent || completedAt != null) {
      map['completed_at'] = Variable<DateTime>(completedAt);
    }
    map['reminder'] = Variable<bool>(reminder);
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  TasksCompanion toCompanion(bool nullToAbsent) {
    return TasksCompanion(
      id: Value(id),
      goalId: goalId == null && nullToAbsent
          ? const Value.absent()
          : Value(goalId),
      dreamId: dreamId == null && nullToAbsent
          ? const Value.absent()
          : Value(dreamId),
      title: Value(title),
      note: Value(note),
      dueDate: dueDate == null && nullToAbsent
          ? const Value.absent()
          : Value(dueDate),
      dueMinutes: dueMinutes == null && nullToAbsent
          ? const Value.absent()
          : Value(dueMinutes),
      priority: Value(priority),
      repeatRule: Value(repeatRule),
      isCompleted: Value(isCompleted),
      completedAt: completedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(completedAt),
      reminder: Value(reminder),
      createdAt: Value(createdAt),
    );
  }

  factory TaskItem.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return TaskItem(
      id: serializer.fromJson<int>(json['id']),
      goalId: serializer.fromJson<int?>(json['goalId']),
      dreamId: serializer.fromJson<int?>(json['dreamId']),
      title: serializer.fromJson<String>(json['title']),
      note: serializer.fromJson<String>(json['note']),
      dueDate: serializer.fromJson<DateTime?>(json['dueDate']),
      dueMinutes: serializer.fromJson<int?>(json['dueMinutes']),
      priority: serializer.fromJson<int>(json['priority']),
      repeatRule: serializer.fromJson<String>(json['repeatRule']),
      isCompleted: serializer.fromJson<bool>(json['isCompleted']),
      completedAt: serializer.fromJson<DateTime?>(json['completedAt']),
      reminder: serializer.fromJson<bool>(json['reminder']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'goalId': serializer.toJson<int?>(goalId),
      'dreamId': serializer.toJson<int?>(dreamId),
      'title': serializer.toJson<String>(title),
      'note': serializer.toJson<String>(note),
      'dueDate': serializer.toJson<DateTime?>(dueDate),
      'dueMinutes': serializer.toJson<int?>(dueMinutes),
      'priority': serializer.toJson<int>(priority),
      'repeatRule': serializer.toJson<String>(repeatRule),
      'isCompleted': serializer.toJson<bool>(isCompleted),
      'completedAt': serializer.toJson<DateTime?>(completedAt),
      'reminder': serializer.toJson<bool>(reminder),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  TaskItem copyWith({
    int? id,
    Value<int?> goalId = const Value.absent(),
    Value<int?> dreamId = const Value.absent(),
    String? title,
    String? note,
    Value<DateTime?> dueDate = const Value.absent(),
    Value<int?> dueMinutes = const Value.absent(),
    int? priority,
    String? repeatRule,
    bool? isCompleted,
    Value<DateTime?> completedAt = const Value.absent(),
    bool? reminder,
    DateTime? createdAt,
  }) => TaskItem(
    id: id ?? this.id,
    goalId: goalId.present ? goalId.value : this.goalId,
    dreamId: dreamId.present ? dreamId.value : this.dreamId,
    title: title ?? this.title,
    note: note ?? this.note,
    dueDate: dueDate.present ? dueDate.value : this.dueDate,
    dueMinutes: dueMinutes.present ? dueMinutes.value : this.dueMinutes,
    priority: priority ?? this.priority,
    repeatRule: repeatRule ?? this.repeatRule,
    isCompleted: isCompleted ?? this.isCompleted,
    completedAt: completedAt.present ? completedAt.value : this.completedAt,
    reminder: reminder ?? this.reminder,
    createdAt: createdAt ?? this.createdAt,
  );
  TaskItem copyWithCompanion(TasksCompanion data) {
    return TaskItem(
      id: data.id.present ? data.id.value : this.id,
      goalId: data.goalId.present ? data.goalId.value : this.goalId,
      dreamId: data.dreamId.present ? data.dreamId.value : this.dreamId,
      title: data.title.present ? data.title.value : this.title,
      note: data.note.present ? data.note.value : this.note,
      dueDate: data.dueDate.present ? data.dueDate.value : this.dueDate,
      dueMinutes: data.dueMinutes.present
          ? data.dueMinutes.value
          : this.dueMinutes,
      priority: data.priority.present ? data.priority.value : this.priority,
      repeatRule: data.repeatRule.present
          ? data.repeatRule.value
          : this.repeatRule,
      isCompleted: data.isCompleted.present
          ? data.isCompleted.value
          : this.isCompleted,
      completedAt: data.completedAt.present
          ? data.completedAt.value
          : this.completedAt,
      reminder: data.reminder.present ? data.reminder.value : this.reminder,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('TaskItem(')
          ..write('id: $id, ')
          ..write('goalId: $goalId, ')
          ..write('dreamId: $dreamId, ')
          ..write('title: $title, ')
          ..write('note: $note, ')
          ..write('dueDate: $dueDate, ')
          ..write('dueMinutes: $dueMinutes, ')
          ..write('priority: $priority, ')
          ..write('repeatRule: $repeatRule, ')
          ..write('isCompleted: $isCompleted, ')
          ..write('completedAt: $completedAt, ')
          ..write('reminder: $reminder, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    goalId,
    dreamId,
    title,
    note,
    dueDate,
    dueMinutes,
    priority,
    repeatRule,
    isCompleted,
    completedAt,
    reminder,
    createdAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is TaskItem &&
          other.id == this.id &&
          other.goalId == this.goalId &&
          other.dreamId == this.dreamId &&
          other.title == this.title &&
          other.note == this.note &&
          other.dueDate == this.dueDate &&
          other.dueMinutes == this.dueMinutes &&
          other.priority == this.priority &&
          other.repeatRule == this.repeatRule &&
          other.isCompleted == this.isCompleted &&
          other.completedAt == this.completedAt &&
          other.reminder == this.reminder &&
          other.createdAt == this.createdAt);
}

class TasksCompanion extends UpdateCompanion<TaskItem> {
  final Value<int> id;
  final Value<int?> goalId;
  final Value<int?> dreamId;
  final Value<String> title;
  final Value<String> note;
  final Value<DateTime?> dueDate;
  final Value<int?> dueMinutes;
  final Value<int> priority;
  final Value<String> repeatRule;
  final Value<bool> isCompleted;
  final Value<DateTime?> completedAt;
  final Value<bool> reminder;
  final Value<DateTime> createdAt;
  const TasksCompanion({
    this.id = const Value.absent(),
    this.goalId = const Value.absent(),
    this.dreamId = const Value.absent(),
    this.title = const Value.absent(),
    this.note = const Value.absent(),
    this.dueDate = const Value.absent(),
    this.dueMinutes = const Value.absent(),
    this.priority = const Value.absent(),
    this.repeatRule = const Value.absent(),
    this.isCompleted = const Value.absent(),
    this.completedAt = const Value.absent(),
    this.reminder = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  TasksCompanion.insert({
    this.id = const Value.absent(),
    this.goalId = const Value.absent(),
    this.dreamId = const Value.absent(),
    required String title,
    this.note = const Value.absent(),
    this.dueDate = const Value.absent(),
    this.dueMinutes = const Value.absent(),
    this.priority = const Value.absent(),
    this.repeatRule = const Value.absent(),
    this.isCompleted = const Value.absent(),
    this.completedAt = const Value.absent(),
    this.reminder = const Value.absent(),
    this.createdAt = const Value.absent(),
  }) : title = Value(title);
  static Insertable<TaskItem> custom({
    Expression<int>? id,
    Expression<int>? goalId,
    Expression<int>? dreamId,
    Expression<String>? title,
    Expression<String>? note,
    Expression<DateTime>? dueDate,
    Expression<int>? dueMinutes,
    Expression<int>? priority,
    Expression<String>? repeatRule,
    Expression<bool>? isCompleted,
    Expression<DateTime>? completedAt,
    Expression<bool>? reminder,
    Expression<DateTime>? createdAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (goalId != null) 'goal_id': goalId,
      if (dreamId != null) 'dream_id': dreamId,
      if (title != null) 'title': title,
      if (note != null) 'note': note,
      if (dueDate != null) 'due_date': dueDate,
      if (dueMinutes != null) 'due_minutes': dueMinutes,
      if (priority != null) 'priority': priority,
      if (repeatRule != null) 'repeat_rule': repeatRule,
      if (isCompleted != null) 'is_completed': isCompleted,
      if (completedAt != null) 'completed_at': completedAt,
      if (reminder != null) 'reminder': reminder,
      if (createdAt != null) 'created_at': createdAt,
    });
  }

  TasksCompanion copyWith({
    Value<int>? id,
    Value<int?>? goalId,
    Value<int?>? dreamId,
    Value<String>? title,
    Value<String>? note,
    Value<DateTime?>? dueDate,
    Value<int?>? dueMinutes,
    Value<int>? priority,
    Value<String>? repeatRule,
    Value<bool>? isCompleted,
    Value<DateTime?>? completedAt,
    Value<bool>? reminder,
    Value<DateTime>? createdAt,
  }) {
    return TasksCompanion(
      id: id ?? this.id,
      goalId: goalId ?? this.goalId,
      dreamId: dreamId ?? this.dreamId,
      title: title ?? this.title,
      note: note ?? this.note,
      dueDate: dueDate ?? this.dueDate,
      dueMinutes: dueMinutes ?? this.dueMinutes,
      priority: priority ?? this.priority,
      repeatRule: repeatRule ?? this.repeatRule,
      isCompleted: isCompleted ?? this.isCompleted,
      completedAt: completedAt ?? this.completedAt,
      reminder: reminder ?? this.reminder,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (goalId.present) {
      map['goal_id'] = Variable<int>(goalId.value);
    }
    if (dreamId.present) {
      map['dream_id'] = Variable<int>(dreamId.value);
    }
    if (title.present) {
      map['title'] = Variable<String>(title.value);
    }
    if (note.present) {
      map['note'] = Variable<String>(note.value);
    }
    if (dueDate.present) {
      map['due_date'] = Variable<DateTime>(dueDate.value);
    }
    if (dueMinutes.present) {
      map['due_minutes'] = Variable<int>(dueMinutes.value);
    }
    if (priority.present) {
      map['priority'] = Variable<int>(priority.value);
    }
    if (repeatRule.present) {
      map['repeat_rule'] = Variable<String>(repeatRule.value);
    }
    if (isCompleted.present) {
      map['is_completed'] = Variable<bool>(isCompleted.value);
    }
    if (completedAt.present) {
      map['completed_at'] = Variable<DateTime>(completedAt.value);
    }
    if (reminder.present) {
      map['reminder'] = Variable<bool>(reminder.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('TasksCompanion(')
          ..write('id: $id, ')
          ..write('goalId: $goalId, ')
          ..write('dreamId: $dreamId, ')
          ..write('title: $title, ')
          ..write('note: $note, ')
          ..write('dueDate: $dueDate, ')
          ..write('dueMinutes: $dueMinutes, ')
          ..write('priority: $priority, ')
          ..write('repeatRule: $repeatRule, ')
          ..write('isCompleted: $isCompleted, ')
          ..write('completedAt: $completedAt, ')
          ..write('reminder: $reminder, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }
}

class $HabitsTable extends Habits with TableInfo<$HabitsTable, Habit> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $HabitsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _titleMeta = const VerificationMeta('title');
  @override
  late final GeneratedColumn<String> title = GeneratedColumn<String>(
    'title',
    aliasedName,
    false,
    additionalChecks: GeneratedColumn.checkTextLength(
      minTextLength: 1,
      maxTextLength: 80,
    ),
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _iconKeyMeta = const VerificationMeta(
    'iconKey',
  );
  @override
  late final GeneratedColumn<String> iconKey = GeneratedColumn<String>(
    'icon_key',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('check'),
  );
  static const VerificationMeta _colorMeta = const VerificationMeta('color');
  @override
  late final GeneratedColumn<int> color = GeneratedColumn<int>(
    'color',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _weekdaysMaskMeta = const VerificationMeta(
    'weekdaysMask',
  );
  @override
  late final GeneratedColumn<int> weekdaysMask = GeneratedColumn<int>(
    'weekdays_mask',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(127),
  );
  static const VerificationMeta _reminderMinutesMeta = const VerificationMeta(
    'reminderMinutes',
  );
  @override
  late final GeneratedColumn<int> reminderMinutes = GeneratedColumn<int>(
    'reminder_minutes',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _challengeDaysMeta = const VerificationMeta(
    'challengeDays',
  );
  @override
  late final GeneratedColumn<int> challengeDays = GeneratedColumn<int>(
    'challenge_days',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _dreamIdMeta = const VerificationMeta(
    'dreamId',
  );
  @override
  late final GeneratedColumn<int> dreamId = GeneratedColumn<int>(
    'dream_id',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES dreams (id) ON DELETE SET NULL',
    ),
  );
  static const VerificationMeta _startDateMeta = const VerificationMeta(
    'startDate',
  );
  @override
  late final GeneratedColumn<DateTime> startDate = GeneratedColumn<DateTime>(
    'start_date',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  static const VerificationMeta _archivedAtMeta = const VerificationMeta(
    'archivedAt',
  );
  @override
  late final GeneratedColumn<DateTime> archivedAt = GeneratedColumn<DateTime>(
    'archived_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    title,
    iconKey,
    color,
    weekdaysMask,
    reminderMinutes,
    challengeDays,
    dreamId,
    startDate,
    archivedAt,
    createdAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'habits';
  @override
  VerificationContext validateIntegrity(
    Insertable<Habit> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('title')) {
      context.handle(
        _titleMeta,
        title.isAcceptableOrUnknown(data['title']!, _titleMeta),
      );
    } else if (isInserting) {
      context.missing(_titleMeta);
    }
    if (data.containsKey('icon_key')) {
      context.handle(
        _iconKeyMeta,
        iconKey.isAcceptableOrUnknown(data['icon_key']!, _iconKeyMeta),
      );
    }
    if (data.containsKey('color')) {
      context.handle(
        _colorMeta,
        color.isAcceptableOrUnknown(data['color']!, _colorMeta),
      );
    } else if (isInserting) {
      context.missing(_colorMeta);
    }
    if (data.containsKey('weekdays_mask')) {
      context.handle(
        _weekdaysMaskMeta,
        weekdaysMask.isAcceptableOrUnknown(
          data['weekdays_mask']!,
          _weekdaysMaskMeta,
        ),
      );
    }
    if (data.containsKey('reminder_minutes')) {
      context.handle(
        _reminderMinutesMeta,
        reminderMinutes.isAcceptableOrUnknown(
          data['reminder_minutes']!,
          _reminderMinutesMeta,
        ),
      );
    }
    if (data.containsKey('challenge_days')) {
      context.handle(
        _challengeDaysMeta,
        challengeDays.isAcceptableOrUnknown(
          data['challenge_days']!,
          _challengeDaysMeta,
        ),
      );
    }
    if (data.containsKey('dream_id')) {
      context.handle(
        _dreamIdMeta,
        dreamId.isAcceptableOrUnknown(data['dream_id']!, _dreamIdMeta),
      );
    }
    if (data.containsKey('start_date')) {
      context.handle(
        _startDateMeta,
        startDate.isAcceptableOrUnknown(data['start_date']!, _startDateMeta),
      );
    }
    if (data.containsKey('archived_at')) {
      context.handle(
        _archivedAtMeta,
        archivedAt.isAcceptableOrUnknown(data['archived_at']!, _archivedAtMeta),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  Habit map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return Habit(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      title: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}title'],
      )!,
      iconKey: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}icon_key'],
      )!,
      color: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}color'],
      )!,
      weekdaysMask: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}weekdays_mask'],
      )!,
      reminderMinutes: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}reminder_minutes'],
      ),
      challengeDays: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}challenge_days'],
      ),
      dreamId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}dream_id'],
      ),
      startDate: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}start_date'],
      )!,
      archivedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}archived_at'],
      ),
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
    );
  }

  @override
  $HabitsTable createAlias(String alias) {
    return $HabitsTable(attachedDatabase, alias);
  }
}

class Habit extends DataClass implements Insertable<Habit> {
  final int id;
  final String title;
  final String iconKey;
  final int color;
  final int weekdaysMask;
  final int? reminderMinutes;
  final int? challengeDays;
  final int? dreamId;
  final DateTime startDate;
  final DateTime? archivedAt;
  final DateTime createdAt;
  const Habit({
    required this.id,
    required this.title,
    required this.iconKey,
    required this.color,
    required this.weekdaysMask,
    this.reminderMinutes,
    this.challengeDays,
    this.dreamId,
    required this.startDate,
    this.archivedAt,
    required this.createdAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['title'] = Variable<String>(title);
    map['icon_key'] = Variable<String>(iconKey);
    map['color'] = Variable<int>(color);
    map['weekdays_mask'] = Variable<int>(weekdaysMask);
    if (!nullToAbsent || reminderMinutes != null) {
      map['reminder_minutes'] = Variable<int>(reminderMinutes);
    }
    if (!nullToAbsent || challengeDays != null) {
      map['challenge_days'] = Variable<int>(challengeDays);
    }
    if (!nullToAbsent || dreamId != null) {
      map['dream_id'] = Variable<int>(dreamId);
    }
    map['start_date'] = Variable<DateTime>(startDate);
    if (!nullToAbsent || archivedAt != null) {
      map['archived_at'] = Variable<DateTime>(archivedAt);
    }
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  HabitsCompanion toCompanion(bool nullToAbsent) {
    return HabitsCompanion(
      id: Value(id),
      title: Value(title),
      iconKey: Value(iconKey),
      color: Value(color),
      weekdaysMask: Value(weekdaysMask),
      reminderMinutes: reminderMinutes == null && nullToAbsent
          ? const Value.absent()
          : Value(reminderMinutes),
      challengeDays: challengeDays == null && nullToAbsent
          ? const Value.absent()
          : Value(challengeDays),
      dreamId: dreamId == null && nullToAbsent
          ? const Value.absent()
          : Value(dreamId),
      startDate: Value(startDate),
      archivedAt: archivedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(archivedAt),
      createdAt: Value(createdAt),
    );
  }

  factory Habit.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return Habit(
      id: serializer.fromJson<int>(json['id']),
      title: serializer.fromJson<String>(json['title']),
      iconKey: serializer.fromJson<String>(json['iconKey']),
      color: serializer.fromJson<int>(json['color']),
      weekdaysMask: serializer.fromJson<int>(json['weekdaysMask']),
      reminderMinutes: serializer.fromJson<int?>(json['reminderMinutes']),
      challengeDays: serializer.fromJson<int?>(json['challengeDays']),
      dreamId: serializer.fromJson<int?>(json['dreamId']),
      startDate: serializer.fromJson<DateTime>(json['startDate']),
      archivedAt: serializer.fromJson<DateTime?>(json['archivedAt']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'title': serializer.toJson<String>(title),
      'iconKey': serializer.toJson<String>(iconKey),
      'color': serializer.toJson<int>(color),
      'weekdaysMask': serializer.toJson<int>(weekdaysMask),
      'reminderMinutes': serializer.toJson<int?>(reminderMinutes),
      'challengeDays': serializer.toJson<int?>(challengeDays),
      'dreamId': serializer.toJson<int?>(dreamId),
      'startDate': serializer.toJson<DateTime>(startDate),
      'archivedAt': serializer.toJson<DateTime?>(archivedAt),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  Habit copyWith({
    int? id,
    String? title,
    String? iconKey,
    int? color,
    int? weekdaysMask,
    Value<int?> reminderMinutes = const Value.absent(),
    Value<int?> challengeDays = const Value.absent(),
    Value<int?> dreamId = const Value.absent(),
    DateTime? startDate,
    Value<DateTime?> archivedAt = const Value.absent(),
    DateTime? createdAt,
  }) => Habit(
    id: id ?? this.id,
    title: title ?? this.title,
    iconKey: iconKey ?? this.iconKey,
    color: color ?? this.color,
    weekdaysMask: weekdaysMask ?? this.weekdaysMask,
    reminderMinutes: reminderMinutes.present
        ? reminderMinutes.value
        : this.reminderMinutes,
    challengeDays: challengeDays.present
        ? challengeDays.value
        : this.challengeDays,
    dreamId: dreamId.present ? dreamId.value : this.dreamId,
    startDate: startDate ?? this.startDate,
    archivedAt: archivedAt.present ? archivedAt.value : this.archivedAt,
    createdAt: createdAt ?? this.createdAt,
  );
  Habit copyWithCompanion(HabitsCompanion data) {
    return Habit(
      id: data.id.present ? data.id.value : this.id,
      title: data.title.present ? data.title.value : this.title,
      iconKey: data.iconKey.present ? data.iconKey.value : this.iconKey,
      color: data.color.present ? data.color.value : this.color,
      weekdaysMask: data.weekdaysMask.present
          ? data.weekdaysMask.value
          : this.weekdaysMask,
      reminderMinutes: data.reminderMinutes.present
          ? data.reminderMinutes.value
          : this.reminderMinutes,
      challengeDays: data.challengeDays.present
          ? data.challengeDays.value
          : this.challengeDays,
      dreamId: data.dreamId.present ? data.dreamId.value : this.dreamId,
      startDate: data.startDate.present ? data.startDate.value : this.startDate,
      archivedAt: data.archivedAt.present
          ? data.archivedAt.value
          : this.archivedAt,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('Habit(')
          ..write('id: $id, ')
          ..write('title: $title, ')
          ..write('iconKey: $iconKey, ')
          ..write('color: $color, ')
          ..write('weekdaysMask: $weekdaysMask, ')
          ..write('reminderMinutes: $reminderMinutes, ')
          ..write('challengeDays: $challengeDays, ')
          ..write('dreamId: $dreamId, ')
          ..write('startDate: $startDate, ')
          ..write('archivedAt: $archivedAt, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    title,
    iconKey,
    color,
    weekdaysMask,
    reminderMinutes,
    challengeDays,
    dreamId,
    startDate,
    archivedAt,
    createdAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is Habit &&
          other.id == this.id &&
          other.title == this.title &&
          other.iconKey == this.iconKey &&
          other.color == this.color &&
          other.weekdaysMask == this.weekdaysMask &&
          other.reminderMinutes == this.reminderMinutes &&
          other.challengeDays == this.challengeDays &&
          other.dreamId == this.dreamId &&
          other.startDate == this.startDate &&
          other.archivedAt == this.archivedAt &&
          other.createdAt == this.createdAt);
}

class HabitsCompanion extends UpdateCompanion<Habit> {
  final Value<int> id;
  final Value<String> title;
  final Value<String> iconKey;
  final Value<int> color;
  final Value<int> weekdaysMask;
  final Value<int?> reminderMinutes;
  final Value<int?> challengeDays;
  final Value<int?> dreamId;
  final Value<DateTime> startDate;
  final Value<DateTime?> archivedAt;
  final Value<DateTime> createdAt;
  const HabitsCompanion({
    this.id = const Value.absent(),
    this.title = const Value.absent(),
    this.iconKey = const Value.absent(),
    this.color = const Value.absent(),
    this.weekdaysMask = const Value.absent(),
    this.reminderMinutes = const Value.absent(),
    this.challengeDays = const Value.absent(),
    this.dreamId = const Value.absent(),
    this.startDate = const Value.absent(),
    this.archivedAt = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  HabitsCompanion.insert({
    this.id = const Value.absent(),
    required String title,
    this.iconKey = const Value.absent(),
    required int color,
    this.weekdaysMask = const Value.absent(),
    this.reminderMinutes = const Value.absent(),
    this.challengeDays = const Value.absent(),
    this.dreamId = const Value.absent(),
    this.startDate = const Value.absent(),
    this.archivedAt = const Value.absent(),
    this.createdAt = const Value.absent(),
  }) : title = Value(title),
       color = Value(color);
  static Insertable<Habit> custom({
    Expression<int>? id,
    Expression<String>? title,
    Expression<String>? iconKey,
    Expression<int>? color,
    Expression<int>? weekdaysMask,
    Expression<int>? reminderMinutes,
    Expression<int>? challengeDays,
    Expression<int>? dreamId,
    Expression<DateTime>? startDate,
    Expression<DateTime>? archivedAt,
    Expression<DateTime>? createdAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (title != null) 'title': title,
      if (iconKey != null) 'icon_key': iconKey,
      if (color != null) 'color': color,
      if (weekdaysMask != null) 'weekdays_mask': weekdaysMask,
      if (reminderMinutes != null) 'reminder_minutes': reminderMinutes,
      if (challengeDays != null) 'challenge_days': challengeDays,
      if (dreamId != null) 'dream_id': dreamId,
      if (startDate != null) 'start_date': startDate,
      if (archivedAt != null) 'archived_at': archivedAt,
      if (createdAt != null) 'created_at': createdAt,
    });
  }

  HabitsCompanion copyWith({
    Value<int>? id,
    Value<String>? title,
    Value<String>? iconKey,
    Value<int>? color,
    Value<int>? weekdaysMask,
    Value<int?>? reminderMinutes,
    Value<int?>? challengeDays,
    Value<int?>? dreamId,
    Value<DateTime>? startDate,
    Value<DateTime?>? archivedAt,
    Value<DateTime>? createdAt,
  }) {
    return HabitsCompanion(
      id: id ?? this.id,
      title: title ?? this.title,
      iconKey: iconKey ?? this.iconKey,
      color: color ?? this.color,
      weekdaysMask: weekdaysMask ?? this.weekdaysMask,
      reminderMinutes: reminderMinutes ?? this.reminderMinutes,
      challengeDays: challengeDays ?? this.challengeDays,
      dreamId: dreamId ?? this.dreamId,
      startDate: startDate ?? this.startDate,
      archivedAt: archivedAt ?? this.archivedAt,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (title.present) {
      map['title'] = Variable<String>(title.value);
    }
    if (iconKey.present) {
      map['icon_key'] = Variable<String>(iconKey.value);
    }
    if (color.present) {
      map['color'] = Variable<int>(color.value);
    }
    if (weekdaysMask.present) {
      map['weekdays_mask'] = Variable<int>(weekdaysMask.value);
    }
    if (reminderMinutes.present) {
      map['reminder_minutes'] = Variable<int>(reminderMinutes.value);
    }
    if (challengeDays.present) {
      map['challenge_days'] = Variable<int>(challengeDays.value);
    }
    if (dreamId.present) {
      map['dream_id'] = Variable<int>(dreamId.value);
    }
    if (startDate.present) {
      map['start_date'] = Variable<DateTime>(startDate.value);
    }
    if (archivedAt.present) {
      map['archived_at'] = Variable<DateTime>(archivedAt.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('HabitsCompanion(')
          ..write('id: $id, ')
          ..write('title: $title, ')
          ..write('iconKey: $iconKey, ')
          ..write('color: $color, ')
          ..write('weekdaysMask: $weekdaysMask, ')
          ..write('reminderMinutes: $reminderMinutes, ')
          ..write('challengeDays: $challengeDays, ')
          ..write('dreamId: $dreamId, ')
          ..write('startDate: $startDate, ')
          ..write('archivedAt: $archivedAt, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }
}

class $HabitCompletionsTable extends HabitCompletions
    with TableInfo<$HabitCompletionsTable, HabitCompletion> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $HabitCompletionsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _habitIdMeta = const VerificationMeta(
    'habitId',
  );
  @override
  late final GeneratedColumn<int> habitId = GeneratedColumn<int>(
    'habit_id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES habits (id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _dayMeta = const VerificationMeta('day');
  @override
  late final GeneratedColumn<DateTime> day = GeneratedColumn<DateTime>(
    'day',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  @override
  List<GeneratedColumn> get $columns => [id, habitId, day, createdAt];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'habit_completions';
  @override
  VerificationContext validateIntegrity(
    Insertable<HabitCompletion> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('habit_id')) {
      context.handle(
        _habitIdMeta,
        habitId.isAcceptableOrUnknown(data['habit_id']!, _habitIdMeta),
      );
    } else if (isInserting) {
      context.missing(_habitIdMeta);
    }
    if (data.containsKey('day')) {
      context.handle(
        _dayMeta,
        day.isAcceptableOrUnknown(data['day']!, _dayMeta),
      );
    } else if (isInserting) {
      context.missing(_dayMeta);
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  List<Set<GeneratedColumn>> get uniqueKeys => [
    {habitId, day},
  ];
  @override
  HabitCompletion map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return HabitCompletion(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      habitId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}habit_id'],
      )!,
      day: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}day'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
    );
  }

  @override
  $HabitCompletionsTable createAlias(String alias) {
    return $HabitCompletionsTable(attachedDatabase, alias);
  }
}

class HabitCompletion extends DataClass implements Insertable<HabitCompletion> {
  final int id;
  final int habitId;
  final DateTime day;
  final DateTime createdAt;
  const HabitCompletion({
    required this.id,
    required this.habitId,
    required this.day,
    required this.createdAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['habit_id'] = Variable<int>(habitId);
    map['day'] = Variable<DateTime>(day);
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  HabitCompletionsCompanion toCompanion(bool nullToAbsent) {
    return HabitCompletionsCompanion(
      id: Value(id),
      habitId: Value(habitId),
      day: Value(day),
      createdAt: Value(createdAt),
    );
  }

  factory HabitCompletion.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return HabitCompletion(
      id: serializer.fromJson<int>(json['id']),
      habitId: serializer.fromJson<int>(json['habitId']),
      day: serializer.fromJson<DateTime>(json['day']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'habitId': serializer.toJson<int>(habitId),
      'day': serializer.toJson<DateTime>(day),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  HabitCompletion copyWith({
    int? id,
    int? habitId,
    DateTime? day,
    DateTime? createdAt,
  }) => HabitCompletion(
    id: id ?? this.id,
    habitId: habitId ?? this.habitId,
    day: day ?? this.day,
    createdAt: createdAt ?? this.createdAt,
  );
  HabitCompletion copyWithCompanion(HabitCompletionsCompanion data) {
    return HabitCompletion(
      id: data.id.present ? data.id.value : this.id,
      habitId: data.habitId.present ? data.habitId.value : this.habitId,
      day: data.day.present ? data.day.value : this.day,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('HabitCompletion(')
          ..write('id: $id, ')
          ..write('habitId: $habitId, ')
          ..write('day: $day, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(id, habitId, day, createdAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is HabitCompletion &&
          other.id == this.id &&
          other.habitId == this.habitId &&
          other.day == this.day &&
          other.createdAt == this.createdAt);
}

class HabitCompletionsCompanion extends UpdateCompanion<HabitCompletion> {
  final Value<int> id;
  final Value<int> habitId;
  final Value<DateTime> day;
  final Value<DateTime> createdAt;
  const HabitCompletionsCompanion({
    this.id = const Value.absent(),
    this.habitId = const Value.absent(),
    this.day = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  HabitCompletionsCompanion.insert({
    this.id = const Value.absent(),
    required int habitId,
    required DateTime day,
    this.createdAt = const Value.absent(),
  }) : habitId = Value(habitId),
       day = Value(day);
  static Insertable<HabitCompletion> custom({
    Expression<int>? id,
    Expression<int>? habitId,
    Expression<DateTime>? day,
    Expression<DateTime>? createdAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (habitId != null) 'habit_id': habitId,
      if (day != null) 'day': day,
      if (createdAt != null) 'created_at': createdAt,
    });
  }

  HabitCompletionsCompanion copyWith({
    Value<int>? id,
    Value<int>? habitId,
    Value<DateTime>? day,
    Value<DateTime>? createdAt,
  }) {
    return HabitCompletionsCompanion(
      id: id ?? this.id,
      habitId: habitId ?? this.habitId,
      day: day ?? this.day,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (habitId.present) {
      map['habit_id'] = Variable<int>(habitId.value);
    }
    if (day.present) {
      map['day'] = Variable<DateTime>(day.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('HabitCompletionsCompanion(')
          ..write('id: $id, ')
          ..write('habitId: $habitId, ')
          ..write('day: $day, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }
}

class $SavingsGoalsTable extends SavingsGoals
    with TableInfo<$SavingsGoalsTable, SavingsGoal> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $SavingsGoalsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _titleMeta = const VerificationMeta('title');
  @override
  late final GeneratedColumn<String> title = GeneratedColumn<String>(
    'title',
    aliasedName,
    false,
    additionalChecks: GeneratedColumn.checkTextLength(
      minTextLength: 1,
      maxTextLength: 120,
    ),
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _dreamIdMeta = const VerificationMeta(
    'dreamId',
  );
  @override
  late final GeneratedColumn<int> dreamId = GeneratedColumn<int>(
    'dream_id',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES dreams (id) ON DELETE SET NULL',
    ),
  );
  static const VerificationMeta _targetMinorMeta = const VerificationMeta(
    'targetMinor',
  );
  @override
  late final GeneratedColumn<int> targetMinor = GeneratedColumn<int>(
    'target_minor',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _currencyMeta = const VerificationMeta(
    'currency',
  );
  @override
  late final GeneratedColumn<String> currency = GeneratedColumn<String>(
    'currency',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('TRY'),
  );
  static const VerificationMeta _monthlyTargetMinorMeta =
      const VerificationMeta('monthlyTargetMinor');
  @override
  late final GeneratedColumn<int> monthlyTargetMinor = GeneratedColumn<int>(
    'monthly_target_minor',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _targetDateMeta = const VerificationMeta(
    'targetDate',
  );
  @override
  late final GeneratedColumn<DateTime> targetDate = GeneratedColumn<DateTime>(
    'target_date',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _isCompletedMeta = const VerificationMeta(
    'isCompleted',
  );
  @override
  late final GeneratedColumn<bool> isCompleted = GeneratedColumn<bool>(
    'is_completed',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("is_completed" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _archivedAtMeta = const VerificationMeta(
    'archivedAt',
  );
  @override
  late final GeneratedColumn<DateTime> archivedAt = GeneratedColumn<DateTime>(
    'archived_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    title,
    dreamId,
    targetMinor,
    currency,
    monthlyTargetMinor,
    targetDate,
    isCompleted,
    archivedAt,
    createdAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'savings_goals';
  @override
  VerificationContext validateIntegrity(
    Insertable<SavingsGoal> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('title')) {
      context.handle(
        _titleMeta,
        title.isAcceptableOrUnknown(data['title']!, _titleMeta),
      );
    } else if (isInserting) {
      context.missing(_titleMeta);
    }
    if (data.containsKey('dream_id')) {
      context.handle(
        _dreamIdMeta,
        dreamId.isAcceptableOrUnknown(data['dream_id']!, _dreamIdMeta),
      );
    }
    if (data.containsKey('target_minor')) {
      context.handle(
        _targetMinorMeta,
        targetMinor.isAcceptableOrUnknown(
          data['target_minor']!,
          _targetMinorMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_targetMinorMeta);
    }
    if (data.containsKey('currency')) {
      context.handle(
        _currencyMeta,
        currency.isAcceptableOrUnknown(data['currency']!, _currencyMeta),
      );
    }
    if (data.containsKey('monthly_target_minor')) {
      context.handle(
        _monthlyTargetMinorMeta,
        monthlyTargetMinor.isAcceptableOrUnknown(
          data['monthly_target_minor']!,
          _monthlyTargetMinorMeta,
        ),
      );
    }
    if (data.containsKey('target_date')) {
      context.handle(
        _targetDateMeta,
        targetDate.isAcceptableOrUnknown(data['target_date']!, _targetDateMeta),
      );
    }
    if (data.containsKey('is_completed')) {
      context.handle(
        _isCompletedMeta,
        isCompleted.isAcceptableOrUnknown(
          data['is_completed']!,
          _isCompletedMeta,
        ),
      );
    }
    if (data.containsKey('archived_at')) {
      context.handle(
        _archivedAtMeta,
        archivedAt.isAcceptableOrUnknown(data['archived_at']!, _archivedAtMeta),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  SavingsGoal map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return SavingsGoal(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      title: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}title'],
      )!,
      dreamId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}dream_id'],
      ),
      targetMinor: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}target_minor'],
      )!,
      currency: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}currency'],
      )!,
      monthlyTargetMinor: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}monthly_target_minor'],
      ),
      targetDate: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}target_date'],
      ),
      isCompleted: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}is_completed'],
      )!,
      archivedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}archived_at'],
      ),
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
    );
  }

  @override
  $SavingsGoalsTable createAlias(String alias) {
    return $SavingsGoalsTable(attachedDatabase, alias);
  }
}

class SavingsGoal extends DataClass implements Insertable<SavingsGoal> {
  final int id;
  final String title;
  final int? dreamId;
  final int targetMinor;
  final String currency;
  final int? monthlyTargetMinor;
  final DateTime? targetDate;
  final bool isCompleted;
  final DateTime? archivedAt;
  final DateTime createdAt;
  const SavingsGoal({
    required this.id,
    required this.title,
    this.dreamId,
    required this.targetMinor,
    required this.currency,
    this.monthlyTargetMinor,
    this.targetDate,
    required this.isCompleted,
    this.archivedAt,
    required this.createdAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['title'] = Variable<String>(title);
    if (!nullToAbsent || dreamId != null) {
      map['dream_id'] = Variable<int>(dreamId);
    }
    map['target_minor'] = Variable<int>(targetMinor);
    map['currency'] = Variable<String>(currency);
    if (!nullToAbsent || monthlyTargetMinor != null) {
      map['monthly_target_minor'] = Variable<int>(monthlyTargetMinor);
    }
    if (!nullToAbsent || targetDate != null) {
      map['target_date'] = Variable<DateTime>(targetDate);
    }
    map['is_completed'] = Variable<bool>(isCompleted);
    if (!nullToAbsent || archivedAt != null) {
      map['archived_at'] = Variable<DateTime>(archivedAt);
    }
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  SavingsGoalsCompanion toCompanion(bool nullToAbsent) {
    return SavingsGoalsCompanion(
      id: Value(id),
      title: Value(title),
      dreamId: dreamId == null && nullToAbsent
          ? const Value.absent()
          : Value(dreamId),
      targetMinor: Value(targetMinor),
      currency: Value(currency),
      monthlyTargetMinor: monthlyTargetMinor == null && nullToAbsent
          ? const Value.absent()
          : Value(monthlyTargetMinor),
      targetDate: targetDate == null && nullToAbsent
          ? const Value.absent()
          : Value(targetDate),
      isCompleted: Value(isCompleted),
      archivedAt: archivedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(archivedAt),
      createdAt: Value(createdAt),
    );
  }

  factory SavingsGoal.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return SavingsGoal(
      id: serializer.fromJson<int>(json['id']),
      title: serializer.fromJson<String>(json['title']),
      dreamId: serializer.fromJson<int?>(json['dreamId']),
      targetMinor: serializer.fromJson<int>(json['targetMinor']),
      currency: serializer.fromJson<String>(json['currency']),
      monthlyTargetMinor: serializer.fromJson<int?>(json['monthlyTargetMinor']),
      targetDate: serializer.fromJson<DateTime?>(json['targetDate']),
      isCompleted: serializer.fromJson<bool>(json['isCompleted']),
      archivedAt: serializer.fromJson<DateTime?>(json['archivedAt']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'title': serializer.toJson<String>(title),
      'dreamId': serializer.toJson<int?>(dreamId),
      'targetMinor': serializer.toJson<int>(targetMinor),
      'currency': serializer.toJson<String>(currency),
      'monthlyTargetMinor': serializer.toJson<int?>(monthlyTargetMinor),
      'targetDate': serializer.toJson<DateTime?>(targetDate),
      'isCompleted': serializer.toJson<bool>(isCompleted),
      'archivedAt': serializer.toJson<DateTime?>(archivedAt),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  SavingsGoal copyWith({
    int? id,
    String? title,
    Value<int?> dreamId = const Value.absent(),
    int? targetMinor,
    String? currency,
    Value<int?> monthlyTargetMinor = const Value.absent(),
    Value<DateTime?> targetDate = const Value.absent(),
    bool? isCompleted,
    Value<DateTime?> archivedAt = const Value.absent(),
    DateTime? createdAt,
  }) => SavingsGoal(
    id: id ?? this.id,
    title: title ?? this.title,
    dreamId: dreamId.present ? dreamId.value : this.dreamId,
    targetMinor: targetMinor ?? this.targetMinor,
    currency: currency ?? this.currency,
    monthlyTargetMinor: monthlyTargetMinor.present
        ? monthlyTargetMinor.value
        : this.monthlyTargetMinor,
    targetDate: targetDate.present ? targetDate.value : this.targetDate,
    isCompleted: isCompleted ?? this.isCompleted,
    archivedAt: archivedAt.present ? archivedAt.value : this.archivedAt,
    createdAt: createdAt ?? this.createdAt,
  );
  SavingsGoal copyWithCompanion(SavingsGoalsCompanion data) {
    return SavingsGoal(
      id: data.id.present ? data.id.value : this.id,
      title: data.title.present ? data.title.value : this.title,
      dreamId: data.dreamId.present ? data.dreamId.value : this.dreamId,
      targetMinor: data.targetMinor.present
          ? data.targetMinor.value
          : this.targetMinor,
      currency: data.currency.present ? data.currency.value : this.currency,
      monthlyTargetMinor: data.monthlyTargetMinor.present
          ? data.monthlyTargetMinor.value
          : this.monthlyTargetMinor,
      targetDate: data.targetDate.present
          ? data.targetDate.value
          : this.targetDate,
      isCompleted: data.isCompleted.present
          ? data.isCompleted.value
          : this.isCompleted,
      archivedAt: data.archivedAt.present
          ? data.archivedAt.value
          : this.archivedAt,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('SavingsGoal(')
          ..write('id: $id, ')
          ..write('title: $title, ')
          ..write('dreamId: $dreamId, ')
          ..write('targetMinor: $targetMinor, ')
          ..write('currency: $currency, ')
          ..write('monthlyTargetMinor: $monthlyTargetMinor, ')
          ..write('targetDate: $targetDate, ')
          ..write('isCompleted: $isCompleted, ')
          ..write('archivedAt: $archivedAt, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    title,
    dreamId,
    targetMinor,
    currency,
    monthlyTargetMinor,
    targetDate,
    isCompleted,
    archivedAt,
    createdAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is SavingsGoal &&
          other.id == this.id &&
          other.title == this.title &&
          other.dreamId == this.dreamId &&
          other.targetMinor == this.targetMinor &&
          other.currency == this.currency &&
          other.monthlyTargetMinor == this.monthlyTargetMinor &&
          other.targetDate == this.targetDate &&
          other.isCompleted == this.isCompleted &&
          other.archivedAt == this.archivedAt &&
          other.createdAt == this.createdAt);
}

class SavingsGoalsCompanion extends UpdateCompanion<SavingsGoal> {
  final Value<int> id;
  final Value<String> title;
  final Value<int?> dreamId;
  final Value<int> targetMinor;
  final Value<String> currency;
  final Value<int?> monthlyTargetMinor;
  final Value<DateTime?> targetDate;
  final Value<bool> isCompleted;
  final Value<DateTime?> archivedAt;
  final Value<DateTime> createdAt;
  const SavingsGoalsCompanion({
    this.id = const Value.absent(),
    this.title = const Value.absent(),
    this.dreamId = const Value.absent(),
    this.targetMinor = const Value.absent(),
    this.currency = const Value.absent(),
    this.monthlyTargetMinor = const Value.absent(),
    this.targetDate = const Value.absent(),
    this.isCompleted = const Value.absent(),
    this.archivedAt = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  SavingsGoalsCompanion.insert({
    this.id = const Value.absent(),
    required String title,
    this.dreamId = const Value.absent(),
    required int targetMinor,
    this.currency = const Value.absent(),
    this.monthlyTargetMinor = const Value.absent(),
    this.targetDate = const Value.absent(),
    this.isCompleted = const Value.absent(),
    this.archivedAt = const Value.absent(),
    this.createdAt = const Value.absent(),
  }) : title = Value(title),
       targetMinor = Value(targetMinor);
  static Insertable<SavingsGoal> custom({
    Expression<int>? id,
    Expression<String>? title,
    Expression<int>? dreamId,
    Expression<int>? targetMinor,
    Expression<String>? currency,
    Expression<int>? monthlyTargetMinor,
    Expression<DateTime>? targetDate,
    Expression<bool>? isCompleted,
    Expression<DateTime>? archivedAt,
    Expression<DateTime>? createdAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (title != null) 'title': title,
      if (dreamId != null) 'dream_id': dreamId,
      if (targetMinor != null) 'target_minor': targetMinor,
      if (currency != null) 'currency': currency,
      if (monthlyTargetMinor != null)
        'monthly_target_minor': monthlyTargetMinor,
      if (targetDate != null) 'target_date': targetDate,
      if (isCompleted != null) 'is_completed': isCompleted,
      if (archivedAt != null) 'archived_at': archivedAt,
      if (createdAt != null) 'created_at': createdAt,
    });
  }

  SavingsGoalsCompanion copyWith({
    Value<int>? id,
    Value<String>? title,
    Value<int?>? dreamId,
    Value<int>? targetMinor,
    Value<String>? currency,
    Value<int?>? monthlyTargetMinor,
    Value<DateTime?>? targetDate,
    Value<bool>? isCompleted,
    Value<DateTime?>? archivedAt,
    Value<DateTime>? createdAt,
  }) {
    return SavingsGoalsCompanion(
      id: id ?? this.id,
      title: title ?? this.title,
      dreamId: dreamId ?? this.dreamId,
      targetMinor: targetMinor ?? this.targetMinor,
      currency: currency ?? this.currency,
      monthlyTargetMinor: monthlyTargetMinor ?? this.monthlyTargetMinor,
      targetDate: targetDate ?? this.targetDate,
      isCompleted: isCompleted ?? this.isCompleted,
      archivedAt: archivedAt ?? this.archivedAt,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (title.present) {
      map['title'] = Variable<String>(title.value);
    }
    if (dreamId.present) {
      map['dream_id'] = Variable<int>(dreamId.value);
    }
    if (targetMinor.present) {
      map['target_minor'] = Variable<int>(targetMinor.value);
    }
    if (currency.present) {
      map['currency'] = Variable<String>(currency.value);
    }
    if (monthlyTargetMinor.present) {
      map['monthly_target_minor'] = Variable<int>(monthlyTargetMinor.value);
    }
    if (targetDate.present) {
      map['target_date'] = Variable<DateTime>(targetDate.value);
    }
    if (isCompleted.present) {
      map['is_completed'] = Variable<bool>(isCompleted.value);
    }
    if (archivedAt.present) {
      map['archived_at'] = Variable<DateTime>(archivedAt.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('SavingsGoalsCompanion(')
          ..write('id: $id, ')
          ..write('title: $title, ')
          ..write('dreamId: $dreamId, ')
          ..write('targetMinor: $targetMinor, ')
          ..write('currency: $currency, ')
          ..write('monthlyTargetMinor: $monthlyTargetMinor, ')
          ..write('targetDate: $targetDate, ')
          ..write('isCompleted: $isCompleted, ')
          ..write('archivedAt: $archivedAt, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }
}

class $SavingsTransactionsTable extends SavingsTransactions
    with TableInfo<$SavingsTransactionsTable, SavingsTransaction> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $SavingsTransactionsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _savingsGoalIdMeta = const VerificationMeta(
    'savingsGoalId',
  );
  @override
  late final GeneratedColumn<int> savingsGoalId = GeneratedColumn<int>(
    'savings_goal_id',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES savings_goals (id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _kindMeta = const VerificationMeta('kind');
  @override
  late final GeneratedColumn<String> kind = GeneratedColumn<String>(
    'kind',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _amountMinorMeta = const VerificationMeta(
    'amountMinor',
  );
  @override
  late final GeneratedColumn<int> amountMinor = GeneratedColumn<int>(
    'amount_minor',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _currencyMeta = const VerificationMeta(
    'currency',
  );
  @override
  late final GeneratedColumn<String> currency = GeneratedColumn<String>(
    'currency',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('TRY'),
  );
  static const VerificationMeta _originalAmountMinorMeta =
      const VerificationMeta('originalAmountMinor');
  @override
  late final GeneratedColumn<int> originalAmountMinor = GeneratedColumn<int>(
    'original_amount_minor',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _originalCurrencyMeta = const VerificationMeta(
    'originalCurrency',
  );
  @override
  late final GeneratedColumn<String> originalCurrency = GeneratedColumn<String>(
    'original_currency',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _manualRateMeta = const VerificationMeta(
    'manualRate',
  );
  @override
  late final GeneratedColumn<String> manualRate = GeneratedColumn<String>(
    'manual_rate',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _noteMeta = const VerificationMeta('note');
  @override
  late final GeneratedColumn<String> note = GeneratedColumn<String>(
    'note',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant(''),
  );
  static const VerificationMeta _dateMeta = const VerificationMeta('date');
  @override
  late final GeneratedColumn<DateTime> date = GeneratedColumn<DateTime>(
    'date',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    savingsGoalId,
    kind,
    amountMinor,
    currency,
    originalAmountMinor,
    originalCurrency,
    manualRate,
    note,
    date,
    createdAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'savings_transactions';
  @override
  VerificationContext validateIntegrity(
    Insertable<SavingsTransaction> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('savings_goal_id')) {
      context.handle(
        _savingsGoalIdMeta,
        savingsGoalId.isAcceptableOrUnknown(
          data['savings_goal_id']!,
          _savingsGoalIdMeta,
        ),
      );
    }
    if (data.containsKey('kind')) {
      context.handle(
        _kindMeta,
        kind.isAcceptableOrUnknown(data['kind']!, _kindMeta),
      );
    } else if (isInserting) {
      context.missing(_kindMeta);
    }
    if (data.containsKey('amount_minor')) {
      context.handle(
        _amountMinorMeta,
        amountMinor.isAcceptableOrUnknown(
          data['amount_minor']!,
          _amountMinorMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_amountMinorMeta);
    }
    if (data.containsKey('currency')) {
      context.handle(
        _currencyMeta,
        currency.isAcceptableOrUnknown(data['currency']!, _currencyMeta),
      );
    }
    if (data.containsKey('original_amount_minor')) {
      context.handle(
        _originalAmountMinorMeta,
        originalAmountMinor.isAcceptableOrUnknown(
          data['original_amount_minor']!,
          _originalAmountMinorMeta,
        ),
      );
    }
    if (data.containsKey('original_currency')) {
      context.handle(
        _originalCurrencyMeta,
        originalCurrency.isAcceptableOrUnknown(
          data['original_currency']!,
          _originalCurrencyMeta,
        ),
      );
    }
    if (data.containsKey('manual_rate')) {
      context.handle(
        _manualRateMeta,
        manualRate.isAcceptableOrUnknown(data['manual_rate']!, _manualRateMeta),
      );
    }
    if (data.containsKey('note')) {
      context.handle(
        _noteMeta,
        note.isAcceptableOrUnknown(data['note']!, _noteMeta),
      );
    }
    if (data.containsKey('date')) {
      context.handle(
        _dateMeta,
        date.isAcceptableOrUnknown(data['date']!, _dateMeta),
      );
    } else if (isInserting) {
      context.missing(_dateMeta);
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  SavingsTransaction map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return SavingsTransaction(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      savingsGoalId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}savings_goal_id'],
      ),
      kind: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}kind'],
      )!,
      amountMinor: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}amount_minor'],
      )!,
      currency: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}currency'],
      )!,
      originalAmountMinor: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}original_amount_minor'],
      ),
      originalCurrency: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}original_currency'],
      ),
      manualRate: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}manual_rate'],
      ),
      note: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}note'],
      )!,
      date: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}date'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
    );
  }

  @override
  $SavingsTransactionsTable createAlias(String alias) {
    return $SavingsTransactionsTable(attachedDatabase, alias);
  }
}

class SavingsTransaction extends DataClass
    implements Insertable<SavingsTransaction> {
  final int id;
  final int? savingsGoalId;
  final String kind;
  final int amountMinor;
  final String currency;
  final int? originalAmountMinor;
  final String? originalCurrency;
  final String? manualRate;
  final String note;
  final DateTime date;
  final DateTime createdAt;
  const SavingsTransaction({
    required this.id,
    this.savingsGoalId,
    required this.kind,
    required this.amountMinor,
    required this.currency,
    this.originalAmountMinor,
    this.originalCurrency,
    this.manualRate,
    required this.note,
    required this.date,
    required this.createdAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    if (!nullToAbsent || savingsGoalId != null) {
      map['savings_goal_id'] = Variable<int>(savingsGoalId);
    }
    map['kind'] = Variable<String>(kind);
    map['amount_minor'] = Variable<int>(amountMinor);
    map['currency'] = Variable<String>(currency);
    if (!nullToAbsent || originalAmountMinor != null) {
      map['original_amount_minor'] = Variable<int>(originalAmountMinor);
    }
    if (!nullToAbsent || originalCurrency != null) {
      map['original_currency'] = Variable<String>(originalCurrency);
    }
    if (!nullToAbsent || manualRate != null) {
      map['manual_rate'] = Variable<String>(manualRate);
    }
    map['note'] = Variable<String>(note);
    map['date'] = Variable<DateTime>(date);
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  SavingsTransactionsCompanion toCompanion(bool nullToAbsent) {
    return SavingsTransactionsCompanion(
      id: Value(id),
      savingsGoalId: savingsGoalId == null && nullToAbsent
          ? const Value.absent()
          : Value(savingsGoalId),
      kind: Value(kind),
      amountMinor: Value(amountMinor),
      currency: Value(currency),
      originalAmountMinor: originalAmountMinor == null && nullToAbsent
          ? const Value.absent()
          : Value(originalAmountMinor),
      originalCurrency: originalCurrency == null && nullToAbsent
          ? const Value.absent()
          : Value(originalCurrency),
      manualRate: manualRate == null && nullToAbsent
          ? const Value.absent()
          : Value(manualRate),
      note: Value(note),
      date: Value(date),
      createdAt: Value(createdAt),
    );
  }

  factory SavingsTransaction.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return SavingsTransaction(
      id: serializer.fromJson<int>(json['id']),
      savingsGoalId: serializer.fromJson<int?>(json['savingsGoalId']),
      kind: serializer.fromJson<String>(json['kind']),
      amountMinor: serializer.fromJson<int>(json['amountMinor']),
      currency: serializer.fromJson<String>(json['currency']),
      originalAmountMinor: serializer.fromJson<int?>(
        json['originalAmountMinor'],
      ),
      originalCurrency: serializer.fromJson<String?>(json['originalCurrency']),
      manualRate: serializer.fromJson<String?>(json['manualRate']),
      note: serializer.fromJson<String>(json['note']),
      date: serializer.fromJson<DateTime>(json['date']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'savingsGoalId': serializer.toJson<int?>(savingsGoalId),
      'kind': serializer.toJson<String>(kind),
      'amountMinor': serializer.toJson<int>(amountMinor),
      'currency': serializer.toJson<String>(currency),
      'originalAmountMinor': serializer.toJson<int?>(originalAmountMinor),
      'originalCurrency': serializer.toJson<String?>(originalCurrency),
      'manualRate': serializer.toJson<String?>(manualRate),
      'note': serializer.toJson<String>(note),
      'date': serializer.toJson<DateTime>(date),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  SavingsTransaction copyWith({
    int? id,
    Value<int?> savingsGoalId = const Value.absent(),
    String? kind,
    int? amountMinor,
    String? currency,
    Value<int?> originalAmountMinor = const Value.absent(),
    Value<String?> originalCurrency = const Value.absent(),
    Value<String?> manualRate = const Value.absent(),
    String? note,
    DateTime? date,
    DateTime? createdAt,
  }) => SavingsTransaction(
    id: id ?? this.id,
    savingsGoalId: savingsGoalId.present
        ? savingsGoalId.value
        : this.savingsGoalId,
    kind: kind ?? this.kind,
    amountMinor: amountMinor ?? this.amountMinor,
    currency: currency ?? this.currency,
    originalAmountMinor: originalAmountMinor.present
        ? originalAmountMinor.value
        : this.originalAmountMinor,
    originalCurrency: originalCurrency.present
        ? originalCurrency.value
        : this.originalCurrency,
    manualRate: manualRate.present ? manualRate.value : this.manualRate,
    note: note ?? this.note,
    date: date ?? this.date,
    createdAt: createdAt ?? this.createdAt,
  );
  SavingsTransaction copyWithCompanion(SavingsTransactionsCompanion data) {
    return SavingsTransaction(
      id: data.id.present ? data.id.value : this.id,
      savingsGoalId: data.savingsGoalId.present
          ? data.savingsGoalId.value
          : this.savingsGoalId,
      kind: data.kind.present ? data.kind.value : this.kind,
      amountMinor: data.amountMinor.present
          ? data.amountMinor.value
          : this.amountMinor,
      currency: data.currency.present ? data.currency.value : this.currency,
      originalAmountMinor: data.originalAmountMinor.present
          ? data.originalAmountMinor.value
          : this.originalAmountMinor,
      originalCurrency: data.originalCurrency.present
          ? data.originalCurrency.value
          : this.originalCurrency,
      manualRate: data.manualRate.present
          ? data.manualRate.value
          : this.manualRate,
      note: data.note.present ? data.note.value : this.note,
      date: data.date.present ? data.date.value : this.date,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('SavingsTransaction(')
          ..write('id: $id, ')
          ..write('savingsGoalId: $savingsGoalId, ')
          ..write('kind: $kind, ')
          ..write('amountMinor: $amountMinor, ')
          ..write('currency: $currency, ')
          ..write('originalAmountMinor: $originalAmountMinor, ')
          ..write('originalCurrency: $originalCurrency, ')
          ..write('manualRate: $manualRate, ')
          ..write('note: $note, ')
          ..write('date: $date, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    savingsGoalId,
    kind,
    amountMinor,
    currency,
    originalAmountMinor,
    originalCurrency,
    manualRate,
    note,
    date,
    createdAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is SavingsTransaction &&
          other.id == this.id &&
          other.savingsGoalId == this.savingsGoalId &&
          other.kind == this.kind &&
          other.amountMinor == this.amountMinor &&
          other.currency == this.currency &&
          other.originalAmountMinor == this.originalAmountMinor &&
          other.originalCurrency == this.originalCurrency &&
          other.manualRate == this.manualRate &&
          other.note == this.note &&
          other.date == this.date &&
          other.createdAt == this.createdAt);
}

class SavingsTransactionsCompanion extends UpdateCompanion<SavingsTransaction> {
  final Value<int> id;
  final Value<int?> savingsGoalId;
  final Value<String> kind;
  final Value<int> amountMinor;
  final Value<String> currency;
  final Value<int?> originalAmountMinor;
  final Value<String?> originalCurrency;
  final Value<String?> manualRate;
  final Value<String> note;
  final Value<DateTime> date;
  final Value<DateTime> createdAt;
  const SavingsTransactionsCompanion({
    this.id = const Value.absent(),
    this.savingsGoalId = const Value.absent(),
    this.kind = const Value.absent(),
    this.amountMinor = const Value.absent(),
    this.currency = const Value.absent(),
    this.originalAmountMinor = const Value.absent(),
    this.originalCurrency = const Value.absent(),
    this.manualRate = const Value.absent(),
    this.note = const Value.absent(),
    this.date = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  SavingsTransactionsCompanion.insert({
    this.id = const Value.absent(),
    this.savingsGoalId = const Value.absent(),
    required String kind,
    required int amountMinor,
    this.currency = const Value.absent(),
    this.originalAmountMinor = const Value.absent(),
    this.originalCurrency = const Value.absent(),
    this.manualRate = const Value.absent(),
    this.note = const Value.absent(),
    required DateTime date,
    this.createdAt = const Value.absent(),
  }) : kind = Value(kind),
       amountMinor = Value(amountMinor),
       date = Value(date);
  static Insertable<SavingsTransaction> custom({
    Expression<int>? id,
    Expression<int>? savingsGoalId,
    Expression<String>? kind,
    Expression<int>? amountMinor,
    Expression<String>? currency,
    Expression<int>? originalAmountMinor,
    Expression<String>? originalCurrency,
    Expression<String>? manualRate,
    Expression<String>? note,
    Expression<DateTime>? date,
    Expression<DateTime>? createdAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (savingsGoalId != null) 'savings_goal_id': savingsGoalId,
      if (kind != null) 'kind': kind,
      if (amountMinor != null) 'amount_minor': amountMinor,
      if (currency != null) 'currency': currency,
      if (originalAmountMinor != null)
        'original_amount_minor': originalAmountMinor,
      if (originalCurrency != null) 'original_currency': originalCurrency,
      if (manualRate != null) 'manual_rate': manualRate,
      if (note != null) 'note': note,
      if (date != null) 'date': date,
      if (createdAt != null) 'created_at': createdAt,
    });
  }

  SavingsTransactionsCompanion copyWith({
    Value<int>? id,
    Value<int?>? savingsGoalId,
    Value<String>? kind,
    Value<int>? amountMinor,
    Value<String>? currency,
    Value<int?>? originalAmountMinor,
    Value<String?>? originalCurrency,
    Value<String?>? manualRate,
    Value<String>? note,
    Value<DateTime>? date,
    Value<DateTime>? createdAt,
  }) {
    return SavingsTransactionsCompanion(
      id: id ?? this.id,
      savingsGoalId: savingsGoalId ?? this.savingsGoalId,
      kind: kind ?? this.kind,
      amountMinor: amountMinor ?? this.amountMinor,
      currency: currency ?? this.currency,
      originalAmountMinor: originalAmountMinor ?? this.originalAmountMinor,
      originalCurrency: originalCurrency ?? this.originalCurrency,
      manualRate: manualRate ?? this.manualRate,
      note: note ?? this.note,
      date: date ?? this.date,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (savingsGoalId.present) {
      map['savings_goal_id'] = Variable<int>(savingsGoalId.value);
    }
    if (kind.present) {
      map['kind'] = Variable<String>(kind.value);
    }
    if (amountMinor.present) {
      map['amount_minor'] = Variable<int>(amountMinor.value);
    }
    if (currency.present) {
      map['currency'] = Variable<String>(currency.value);
    }
    if (originalAmountMinor.present) {
      map['original_amount_minor'] = Variable<int>(originalAmountMinor.value);
    }
    if (originalCurrency.present) {
      map['original_currency'] = Variable<String>(originalCurrency.value);
    }
    if (manualRate.present) {
      map['manual_rate'] = Variable<String>(manualRate.value);
    }
    if (note.present) {
      map['note'] = Variable<String>(note.value);
    }
    if (date.present) {
      map['date'] = Variable<DateTime>(date.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('SavingsTransactionsCompanion(')
          ..write('id: $id, ')
          ..write('savingsGoalId: $savingsGoalId, ')
          ..write('kind: $kind, ')
          ..write('amountMinor: $amountMinor, ')
          ..write('currency: $currency, ')
          ..write('originalAmountMinor: $originalAmountMinor, ')
          ..write('originalCurrency: $originalCurrency, ')
          ..write('manualRate: $manualRate, ')
          ..write('note: $note, ')
          ..write('date: $date, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }
}

class $JournalEntriesTable extends JournalEntries
    with TableInfo<$JournalEntriesTable, JournalEntry> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $JournalEntriesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _dateMeta = const VerificationMeta('date');
  @override
  late final GeneratedColumn<DateTime> date = GeneratedColumn<DateTime>(
    'date',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _titleMeta = const VerificationMeta('title');
  @override
  late final GeneratedColumn<String> title = GeneratedColumn<String>(
    'title',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant(''),
  );
  static const VerificationMeta _bodyMeta = const VerificationMeta('body');
  @override
  late final GeneratedColumn<String> body = GeneratedColumn<String>(
    'body',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant(''),
  );
  static const VerificationMeta _moodMeta = const VerificationMeta('mood');
  @override
  late final GeneratedColumn<int> mood = GeneratedColumn<int>(
    'mood',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(3),
  );
  static const VerificationMeta _tagsMeta = const VerificationMeta('tags');
  @override
  late final GeneratedColumn<String> tags = GeneratedColumn<String>(
    'tags',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant(''),
  );
  static const VerificationMeta _isImportantMeta = const VerificationMeta(
    'isImportant',
  );
  @override
  late final GeneratedColumn<bool> isImportant = GeneratedColumn<bool>(
    'is_important',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("is_important" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _isPrivateMeta = const VerificationMeta(
    'isPrivate',
  );
  @override
  late final GeneratedColumn<bool> isPrivate = GeneratedColumn<bool>(
    'is_private',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("is_private" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  @override
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
    'updated_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    date,
    title,
    body,
    mood,
    tags,
    isImportant,
    isPrivate,
    createdAt,
    updatedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'journal_entries';
  @override
  VerificationContext validateIntegrity(
    Insertable<JournalEntry> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('date')) {
      context.handle(
        _dateMeta,
        date.isAcceptableOrUnknown(data['date']!, _dateMeta),
      );
    } else if (isInserting) {
      context.missing(_dateMeta);
    }
    if (data.containsKey('title')) {
      context.handle(
        _titleMeta,
        title.isAcceptableOrUnknown(data['title']!, _titleMeta),
      );
    }
    if (data.containsKey('body')) {
      context.handle(
        _bodyMeta,
        body.isAcceptableOrUnknown(data['body']!, _bodyMeta),
      );
    }
    if (data.containsKey('mood')) {
      context.handle(
        _moodMeta,
        mood.isAcceptableOrUnknown(data['mood']!, _moodMeta),
      );
    }
    if (data.containsKey('tags')) {
      context.handle(
        _tagsMeta,
        tags.isAcceptableOrUnknown(data['tags']!, _tagsMeta),
      );
    }
    if (data.containsKey('is_important')) {
      context.handle(
        _isImportantMeta,
        isImportant.isAcceptableOrUnknown(
          data['is_important']!,
          _isImportantMeta,
        ),
      );
    }
    if (data.containsKey('is_private')) {
      context.handle(
        _isPrivateMeta,
        isPrivate.isAcceptableOrUnknown(data['is_private']!, _isPrivateMeta),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  JournalEntry map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return JournalEntry(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      date: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}date'],
      )!,
      title: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}title'],
      )!,
      body: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}body'],
      )!,
      mood: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}mood'],
      )!,
      tags: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}tags'],
      )!,
      isImportant: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}is_important'],
      )!,
      isPrivate: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}is_private'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      )!,
    );
  }

  @override
  $JournalEntriesTable createAlias(String alias) {
    return $JournalEntriesTable(attachedDatabase, alias);
  }
}

class JournalEntry extends DataClass implements Insertable<JournalEntry> {
  final int id;
  final DateTime date;
  final String title;
  final String body;
  final int mood;
  final String tags;
  final bool isImportant;
  final bool isPrivate;
  final DateTime createdAt;
  final DateTime updatedAt;
  const JournalEntry({
    required this.id,
    required this.date,
    required this.title,
    required this.body,
    required this.mood,
    required this.tags,
    required this.isImportant,
    required this.isPrivate,
    required this.createdAt,
    required this.updatedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['date'] = Variable<DateTime>(date);
    map['title'] = Variable<String>(title);
    map['body'] = Variable<String>(body);
    map['mood'] = Variable<int>(mood);
    map['tags'] = Variable<String>(tags);
    map['is_important'] = Variable<bool>(isImportant);
    map['is_private'] = Variable<bool>(isPrivate);
    map['created_at'] = Variable<DateTime>(createdAt);
    map['updated_at'] = Variable<DateTime>(updatedAt);
    return map;
  }

  JournalEntriesCompanion toCompanion(bool nullToAbsent) {
    return JournalEntriesCompanion(
      id: Value(id),
      date: Value(date),
      title: Value(title),
      body: Value(body),
      mood: Value(mood),
      tags: Value(tags),
      isImportant: Value(isImportant),
      isPrivate: Value(isPrivate),
      createdAt: Value(createdAt),
      updatedAt: Value(updatedAt),
    );
  }

  factory JournalEntry.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return JournalEntry(
      id: serializer.fromJson<int>(json['id']),
      date: serializer.fromJson<DateTime>(json['date']),
      title: serializer.fromJson<String>(json['title']),
      body: serializer.fromJson<String>(json['body']),
      mood: serializer.fromJson<int>(json['mood']),
      tags: serializer.fromJson<String>(json['tags']),
      isImportant: serializer.fromJson<bool>(json['isImportant']),
      isPrivate: serializer.fromJson<bool>(json['isPrivate']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
      updatedAt: serializer.fromJson<DateTime>(json['updatedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'date': serializer.toJson<DateTime>(date),
      'title': serializer.toJson<String>(title),
      'body': serializer.toJson<String>(body),
      'mood': serializer.toJson<int>(mood),
      'tags': serializer.toJson<String>(tags),
      'isImportant': serializer.toJson<bool>(isImportant),
      'isPrivate': serializer.toJson<bool>(isPrivate),
      'createdAt': serializer.toJson<DateTime>(createdAt),
      'updatedAt': serializer.toJson<DateTime>(updatedAt),
    };
  }

  JournalEntry copyWith({
    int? id,
    DateTime? date,
    String? title,
    String? body,
    int? mood,
    String? tags,
    bool? isImportant,
    bool? isPrivate,
    DateTime? createdAt,
    DateTime? updatedAt,
  }) => JournalEntry(
    id: id ?? this.id,
    date: date ?? this.date,
    title: title ?? this.title,
    body: body ?? this.body,
    mood: mood ?? this.mood,
    tags: tags ?? this.tags,
    isImportant: isImportant ?? this.isImportant,
    isPrivate: isPrivate ?? this.isPrivate,
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt ?? this.updatedAt,
  );
  JournalEntry copyWithCompanion(JournalEntriesCompanion data) {
    return JournalEntry(
      id: data.id.present ? data.id.value : this.id,
      date: data.date.present ? data.date.value : this.date,
      title: data.title.present ? data.title.value : this.title,
      body: data.body.present ? data.body.value : this.body,
      mood: data.mood.present ? data.mood.value : this.mood,
      tags: data.tags.present ? data.tags.value : this.tags,
      isImportant: data.isImportant.present
          ? data.isImportant.value
          : this.isImportant,
      isPrivate: data.isPrivate.present ? data.isPrivate.value : this.isPrivate,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('JournalEntry(')
          ..write('id: $id, ')
          ..write('date: $date, ')
          ..write('title: $title, ')
          ..write('body: $body, ')
          ..write('mood: $mood, ')
          ..write('tags: $tags, ')
          ..write('isImportant: $isImportant, ')
          ..write('isPrivate: $isPrivate, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    date,
    title,
    body,
    mood,
    tags,
    isImportant,
    isPrivate,
    createdAt,
    updatedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is JournalEntry &&
          other.id == this.id &&
          other.date == this.date &&
          other.title == this.title &&
          other.body == this.body &&
          other.mood == this.mood &&
          other.tags == this.tags &&
          other.isImportant == this.isImportant &&
          other.isPrivate == this.isPrivate &&
          other.createdAt == this.createdAt &&
          other.updatedAt == this.updatedAt);
}

class JournalEntriesCompanion extends UpdateCompanion<JournalEntry> {
  final Value<int> id;
  final Value<DateTime> date;
  final Value<String> title;
  final Value<String> body;
  final Value<int> mood;
  final Value<String> tags;
  final Value<bool> isImportant;
  final Value<bool> isPrivate;
  final Value<DateTime> createdAt;
  final Value<DateTime> updatedAt;
  const JournalEntriesCompanion({
    this.id = const Value.absent(),
    this.date = const Value.absent(),
    this.title = const Value.absent(),
    this.body = const Value.absent(),
    this.mood = const Value.absent(),
    this.tags = const Value.absent(),
    this.isImportant = const Value.absent(),
    this.isPrivate = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
  });
  JournalEntriesCompanion.insert({
    this.id = const Value.absent(),
    required DateTime date,
    this.title = const Value.absent(),
    this.body = const Value.absent(),
    this.mood = const Value.absent(),
    this.tags = const Value.absent(),
    this.isImportant = const Value.absent(),
    this.isPrivate = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
  }) : date = Value(date);
  static Insertable<JournalEntry> custom({
    Expression<int>? id,
    Expression<DateTime>? date,
    Expression<String>? title,
    Expression<String>? body,
    Expression<int>? mood,
    Expression<String>? tags,
    Expression<bool>? isImportant,
    Expression<bool>? isPrivate,
    Expression<DateTime>? createdAt,
    Expression<DateTime>? updatedAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (date != null) 'date': date,
      if (title != null) 'title': title,
      if (body != null) 'body': body,
      if (mood != null) 'mood': mood,
      if (tags != null) 'tags': tags,
      if (isImportant != null) 'is_important': isImportant,
      if (isPrivate != null) 'is_private': isPrivate,
      if (createdAt != null) 'created_at': createdAt,
      if (updatedAt != null) 'updated_at': updatedAt,
    });
  }

  JournalEntriesCompanion copyWith({
    Value<int>? id,
    Value<DateTime>? date,
    Value<String>? title,
    Value<String>? body,
    Value<int>? mood,
    Value<String>? tags,
    Value<bool>? isImportant,
    Value<bool>? isPrivate,
    Value<DateTime>? createdAt,
    Value<DateTime>? updatedAt,
  }) {
    return JournalEntriesCompanion(
      id: id ?? this.id,
      date: date ?? this.date,
      title: title ?? this.title,
      body: body ?? this.body,
      mood: mood ?? this.mood,
      tags: tags ?? this.tags,
      isImportant: isImportant ?? this.isImportant,
      isPrivate: isPrivate ?? this.isPrivate,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (date.present) {
      map['date'] = Variable<DateTime>(date.value);
    }
    if (title.present) {
      map['title'] = Variable<String>(title.value);
    }
    if (body.present) {
      map['body'] = Variable<String>(body.value);
    }
    if (mood.present) {
      map['mood'] = Variable<int>(mood.value);
    }
    if (tags.present) {
      map['tags'] = Variable<String>(tags.value);
    }
    if (isImportant.present) {
      map['is_important'] = Variable<bool>(isImportant.value);
    }
    if (isPrivate.present) {
      map['is_private'] = Variable<bool>(isPrivate.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('JournalEntriesCompanion(')
          ..write('id: $id, ')
          ..write('date: $date, ')
          ..write('title: $title, ')
          ..write('body: $body, ')
          ..write('mood: $mood, ')
          ..write('tags: $tags, ')
          ..write('isImportant: $isImportant, ')
          ..write('isPrivate: $isPrivate, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }
}

class $TimeCapsulesTable extends TimeCapsules
    with TableInfo<$TimeCapsulesTable, TimeCapsule> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $TimeCapsulesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _titleMeta = const VerificationMeta('title');
  @override
  late final GeneratedColumn<String> title = GeneratedColumn<String>(
    'title',
    aliasedName,
    false,
    additionalChecks: GeneratedColumn.checkTextLength(
      minTextLength: 1,
      maxTextLength: 120,
    ),
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _letterMeta = const VerificationMeta('letter');
  @override
  late final GeneratedColumn<String> letter = GeneratedColumn<String>(
    'letter',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant(''),
  );
  static const VerificationMeta _dreamsSnapshotMeta = const VerificationMeta(
    'dreamsSnapshot',
  );
  @override
  late final GeneratedColumn<String> dreamsSnapshot = GeneratedColumn<String>(
    'dreams_snapshot',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant(''),
  );
  static const VerificationMeta _goalsTextMeta = const VerificationMeta(
    'goalsText',
  );
  @override
  late final GeneratedColumn<String> goalsText = GeneratedColumn<String>(
    'goals_text',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant(''),
  );
  static const VerificationMeta _openAtMeta = const VerificationMeta('openAt');
  @override
  late final GeneratedColumn<DateTime> openAt = GeneratedColumn<DateTime>(
    'open_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _openedAtMeta = const VerificationMeta(
    'openedAt',
  );
  @override
  late final GeneratedColumn<DateTime> openedAt = GeneratedColumn<DateTime>(
    'opened_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    title,
    letter,
    dreamsSnapshot,
    goalsText,
    openAt,
    openedAt,
    createdAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'time_capsules';
  @override
  VerificationContext validateIntegrity(
    Insertable<TimeCapsule> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('title')) {
      context.handle(
        _titleMeta,
        title.isAcceptableOrUnknown(data['title']!, _titleMeta),
      );
    } else if (isInserting) {
      context.missing(_titleMeta);
    }
    if (data.containsKey('letter')) {
      context.handle(
        _letterMeta,
        letter.isAcceptableOrUnknown(data['letter']!, _letterMeta),
      );
    }
    if (data.containsKey('dreams_snapshot')) {
      context.handle(
        _dreamsSnapshotMeta,
        dreamsSnapshot.isAcceptableOrUnknown(
          data['dreams_snapshot']!,
          _dreamsSnapshotMeta,
        ),
      );
    }
    if (data.containsKey('goals_text')) {
      context.handle(
        _goalsTextMeta,
        goalsText.isAcceptableOrUnknown(data['goals_text']!, _goalsTextMeta),
      );
    }
    if (data.containsKey('open_at')) {
      context.handle(
        _openAtMeta,
        openAt.isAcceptableOrUnknown(data['open_at']!, _openAtMeta),
      );
    } else if (isInserting) {
      context.missing(_openAtMeta);
    }
    if (data.containsKey('opened_at')) {
      context.handle(
        _openedAtMeta,
        openedAt.isAcceptableOrUnknown(data['opened_at']!, _openedAtMeta),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  TimeCapsule map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return TimeCapsule(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      title: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}title'],
      )!,
      letter: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}letter'],
      )!,
      dreamsSnapshot: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}dreams_snapshot'],
      )!,
      goalsText: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}goals_text'],
      )!,
      openAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}open_at'],
      )!,
      openedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}opened_at'],
      ),
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
    );
  }

  @override
  $TimeCapsulesTable createAlias(String alias) {
    return $TimeCapsulesTable(attachedDatabase, alias);
  }
}

class TimeCapsule extends DataClass implements Insertable<TimeCapsule> {
  final int id;
  final String title;
  final String letter;
  final String dreamsSnapshot;
  final String goalsText;
  final DateTime openAt;
  final DateTime? openedAt;
  final DateTime createdAt;
  const TimeCapsule({
    required this.id,
    required this.title,
    required this.letter,
    required this.dreamsSnapshot,
    required this.goalsText,
    required this.openAt,
    this.openedAt,
    required this.createdAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['title'] = Variable<String>(title);
    map['letter'] = Variable<String>(letter);
    map['dreams_snapshot'] = Variable<String>(dreamsSnapshot);
    map['goals_text'] = Variable<String>(goalsText);
    map['open_at'] = Variable<DateTime>(openAt);
    if (!nullToAbsent || openedAt != null) {
      map['opened_at'] = Variable<DateTime>(openedAt);
    }
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  TimeCapsulesCompanion toCompanion(bool nullToAbsent) {
    return TimeCapsulesCompanion(
      id: Value(id),
      title: Value(title),
      letter: Value(letter),
      dreamsSnapshot: Value(dreamsSnapshot),
      goalsText: Value(goalsText),
      openAt: Value(openAt),
      openedAt: openedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(openedAt),
      createdAt: Value(createdAt),
    );
  }

  factory TimeCapsule.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return TimeCapsule(
      id: serializer.fromJson<int>(json['id']),
      title: serializer.fromJson<String>(json['title']),
      letter: serializer.fromJson<String>(json['letter']),
      dreamsSnapshot: serializer.fromJson<String>(json['dreamsSnapshot']),
      goalsText: serializer.fromJson<String>(json['goalsText']),
      openAt: serializer.fromJson<DateTime>(json['openAt']),
      openedAt: serializer.fromJson<DateTime?>(json['openedAt']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'title': serializer.toJson<String>(title),
      'letter': serializer.toJson<String>(letter),
      'dreamsSnapshot': serializer.toJson<String>(dreamsSnapshot),
      'goalsText': serializer.toJson<String>(goalsText),
      'openAt': serializer.toJson<DateTime>(openAt),
      'openedAt': serializer.toJson<DateTime?>(openedAt),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  TimeCapsule copyWith({
    int? id,
    String? title,
    String? letter,
    String? dreamsSnapshot,
    String? goalsText,
    DateTime? openAt,
    Value<DateTime?> openedAt = const Value.absent(),
    DateTime? createdAt,
  }) => TimeCapsule(
    id: id ?? this.id,
    title: title ?? this.title,
    letter: letter ?? this.letter,
    dreamsSnapshot: dreamsSnapshot ?? this.dreamsSnapshot,
    goalsText: goalsText ?? this.goalsText,
    openAt: openAt ?? this.openAt,
    openedAt: openedAt.present ? openedAt.value : this.openedAt,
    createdAt: createdAt ?? this.createdAt,
  );
  TimeCapsule copyWithCompanion(TimeCapsulesCompanion data) {
    return TimeCapsule(
      id: data.id.present ? data.id.value : this.id,
      title: data.title.present ? data.title.value : this.title,
      letter: data.letter.present ? data.letter.value : this.letter,
      dreamsSnapshot: data.dreamsSnapshot.present
          ? data.dreamsSnapshot.value
          : this.dreamsSnapshot,
      goalsText: data.goalsText.present ? data.goalsText.value : this.goalsText,
      openAt: data.openAt.present ? data.openAt.value : this.openAt,
      openedAt: data.openedAt.present ? data.openedAt.value : this.openedAt,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('TimeCapsule(')
          ..write('id: $id, ')
          ..write('title: $title, ')
          ..write('letter: $letter, ')
          ..write('dreamsSnapshot: $dreamsSnapshot, ')
          ..write('goalsText: $goalsText, ')
          ..write('openAt: $openAt, ')
          ..write('openedAt: $openedAt, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    title,
    letter,
    dreamsSnapshot,
    goalsText,
    openAt,
    openedAt,
    createdAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is TimeCapsule &&
          other.id == this.id &&
          other.title == this.title &&
          other.letter == this.letter &&
          other.dreamsSnapshot == this.dreamsSnapshot &&
          other.goalsText == this.goalsText &&
          other.openAt == this.openAt &&
          other.openedAt == this.openedAt &&
          other.createdAt == this.createdAt);
}

class TimeCapsulesCompanion extends UpdateCompanion<TimeCapsule> {
  final Value<int> id;
  final Value<String> title;
  final Value<String> letter;
  final Value<String> dreamsSnapshot;
  final Value<String> goalsText;
  final Value<DateTime> openAt;
  final Value<DateTime?> openedAt;
  final Value<DateTime> createdAt;
  const TimeCapsulesCompanion({
    this.id = const Value.absent(),
    this.title = const Value.absent(),
    this.letter = const Value.absent(),
    this.dreamsSnapshot = const Value.absent(),
    this.goalsText = const Value.absent(),
    this.openAt = const Value.absent(),
    this.openedAt = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  TimeCapsulesCompanion.insert({
    this.id = const Value.absent(),
    required String title,
    this.letter = const Value.absent(),
    this.dreamsSnapshot = const Value.absent(),
    this.goalsText = const Value.absent(),
    required DateTime openAt,
    this.openedAt = const Value.absent(),
    this.createdAt = const Value.absent(),
  }) : title = Value(title),
       openAt = Value(openAt);
  static Insertable<TimeCapsule> custom({
    Expression<int>? id,
    Expression<String>? title,
    Expression<String>? letter,
    Expression<String>? dreamsSnapshot,
    Expression<String>? goalsText,
    Expression<DateTime>? openAt,
    Expression<DateTime>? openedAt,
    Expression<DateTime>? createdAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (title != null) 'title': title,
      if (letter != null) 'letter': letter,
      if (dreamsSnapshot != null) 'dreams_snapshot': dreamsSnapshot,
      if (goalsText != null) 'goals_text': goalsText,
      if (openAt != null) 'open_at': openAt,
      if (openedAt != null) 'opened_at': openedAt,
      if (createdAt != null) 'created_at': createdAt,
    });
  }

  TimeCapsulesCompanion copyWith({
    Value<int>? id,
    Value<String>? title,
    Value<String>? letter,
    Value<String>? dreamsSnapshot,
    Value<String>? goalsText,
    Value<DateTime>? openAt,
    Value<DateTime?>? openedAt,
    Value<DateTime>? createdAt,
  }) {
    return TimeCapsulesCompanion(
      id: id ?? this.id,
      title: title ?? this.title,
      letter: letter ?? this.letter,
      dreamsSnapshot: dreamsSnapshot ?? this.dreamsSnapshot,
      goalsText: goalsText ?? this.goalsText,
      openAt: openAt ?? this.openAt,
      openedAt: openedAt ?? this.openedAt,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (title.present) {
      map['title'] = Variable<String>(title.value);
    }
    if (letter.present) {
      map['letter'] = Variable<String>(letter.value);
    }
    if (dreamsSnapshot.present) {
      map['dreams_snapshot'] = Variable<String>(dreamsSnapshot.value);
    }
    if (goalsText.present) {
      map['goals_text'] = Variable<String>(goalsText.value);
    }
    if (openAt.present) {
      map['open_at'] = Variable<DateTime>(openAt.value);
    }
    if (openedAt.present) {
      map['opened_at'] = Variable<DateTime>(openedAt.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('TimeCapsulesCompanion(')
          ..write('id: $id, ')
          ..write('title: $title, ')
          ..write('letter: $letter, ')
          ..write('dreamsSnapshot: $dreamsSnapshot, ')
          ..write('goalsText: $goalsText, ')
          ..write('openAt: $openAt, ')
          ..write('openedAt: $openedAt, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }
}

class $JournalMediaTable extends JournalMedia
    with TableInfo<$JournalMediaTable, JournalMediaItem> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $JournalMediaTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _entryIdMeta = const VerificationMeta(
    'entryId',
  );
  @override
  late final GeneratedColumn<int> entryId = GeneratedColumn<int>(
    'entry_id',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES journal_entries (id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _capsuleIdMeta = const VerificationMeta(
    'capsuleId',
  );
  @override
  late final GeneratedColumn<int> capsuleId = GeneratedColumn<int>(
    'capsule_id',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES time_capsules (id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _kindMeta = const VerificationMeta('kind');
  @override
  late final GeneratedColumn<String> kind = GeneratedColumn<String>(
    'kind',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _pathMeta = const VerificationMeta('path');
  @override
  late final GeneratedColumn<String> path = GeneratedColumn<String>(
    'path',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _durationMsMeta = const VerificationMeta(
    'durationMs',
  );
  @override
  late final GeneratedColumn<int> durationMs = GeneratedColumn<int>(
    'duration_ms',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    entryId,
    capsuleId,
    kind,
    path,
    durationMs,
    createdAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'journal_media';
  @override
  VerificationContext validateIntegrity(
    Insertable<JournalMediaItem> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('entry_id')) {
      context.handle(
        _entryIdMeta,
        entryId.isAcceptableOrUnknown(data['entry_id']!, _entryIdMeta),
      );
    }
    if (data.containsKey('capsule_id')) {
      context.handle(
        _capsuleIdMeta,
        capsuleId.isAcceptableOrUnknown(data['capsule_id']!, _capsuleIdMeta),
      );
    }
    if (data.containsKey('kind')) {
      context.handle(
        _kindMeta,
        kind.isAcceptableOrUnknown(data['kind']!, _kindMeta),
      );
    } else if (isInserting) {
      context.missing(_kindMeta);
    }
    if (data.containsKey('path')) {
      context.handle(
        _pathMeta,
        path.isAcceptableOrUnknown(data['path']!, _pathMeta),
      );
    } else if (isInserting) {
      context.missing(_pathMeta);
    }
    if (data.containsKey('duration_ms')) {
      context.handle(
        _durationMsMeta,
        durationMs.isAcceptableOrUnknown(data['duration_ms']!, _durationMsMeta),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  JournalMediaItem map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return JournalMediaItem(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      entryId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}entry_id'],
      ),
      capsuleId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}capsule_id'],
      ),
      kind: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}kind'],
      )!,
      path: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}path'],
      )!,
      durationMs: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}duration_ms'],
      ),
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
    );
  }

  @override
  $JournalMediaTable createAlias(String alias) {
    return $JournalMediaTable(attachedDatabase, alias);
  }
}

class JournalMediaItem extends DataClass
    implements Insertable<JournalMediaItem> {
  final int id;
  final int? entryId;
  final int? capsuleId;
  final String kind;
  final String path;
  final int? durationMs;
  final DateTime createdAt;
  const JournalMediaItem({
    required this.id,
    this.entryId,
    this.capsuleId,
    required this.kind,
    required this.path,
    this.durationMs,
    required this.createdAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    if (!nullToAbsent || entryId != null) {
      map['entry_id'] = Variable<int>(entryId);
    }
    if (!nullToAbsent || capsuleId != null) {
      map['capsule_id'] = Variable<int>(capsuleId);
    }
    map['kind'] = Variable<String>(kind);
    map['path'] = Variable<String>(path);
    if (!nullToAbsent || durationMs != null) {
      map['duration_ms'] = Variable<int>(durationMs);
    }
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  JournalMediaCompanion toCompanion(bool nullToAbsent) {
    return JournalMediaCompanion(
      id: Value(id),
      entryId: entryId == null && nullToAbsent
          ? const Value.absent()
          : Value(entryId),
      capsuleId: capsuleId == null && nullToAbsent
          ? const Value.absent()
          : Value(capsuleId),
      kind: Value(kind),
      path: Value(path),
      durationMs: durationMs == null && nullToAbsent
          ? const Value.absent()
          : Value(durationMs),
      createdAt: Value(createdAt),
    );
  }

  factory JournalMediaItem.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return JournalMediaItem(
      id: serializer.fromJson<int>(json['id']),
      entryId: serializer.fromJson<int?>(json['entryId']),
      capsuleId: serializer.fromJson<int?>(json['capsuleId']),
      kind: serializer.fromJson<String>(json['kind']),
      path: serializer.fromJson<String>(json['path']),
      durationMs: serializer.fromJson<int?>(json['durationMs']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'entryId': serializer.toJson<int?>(entryId),
      'capsuleId': serializer.toJson<int?>(capsuleId),
      'kind': serializer.toJson<String>(kind),
      'path': serializer.toJson<String>(path),
      'durationMs': serializer.toJson<int?>(durationMs),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  JournalMediaItem copyWith({
    int? id,
    Value<int?> entryId = const Value.absent(),
    Value<int?> capsuleId = const Value.absent(),
    String? kind,
    String? path,
    Value<int?> durationMs = const Value.absent(),
    DateTime? createdAt,
  }) => JournalMediaItem(
    id: id ?? this.id,
    entryId: entryId.present ? entryId.value : this.entryId,
    capsuleId: capsuleId.present ? capsuleId.value : this.capsuleId,
    kind: kind ?? this.kind,
    path: path ?? this.path,
    durationMs: durationMs.present ? durationMs.value : this.durationMs,
    createdAt: createdAt ?? this.createdAt,
  );
  JournalMediaItem copyWithCompanion(JournalMediaCompanion data) {
    return JournalMediaItem(
      id: data.id.present ? data.id.value : this.id,
      entryId: data.entryId.present ? data.entryId.value : this.entryId,
      capsuleId: data.capsuleId.present ? data.capsuleId.value : this.capsuleId,
      kind: data.kind.present ? data.kind.value : this.kind,
      path: data.path.present ? data.path.value : this.path,
      durationMs: data.durationMs.present
          ? data.durationMs.value
          : this.durationMs,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('JournalMediaItem(')
          ..write('id: $id, ')
          ..write('entryId: $entryId, ')
          ..write('capsuleId: $capsuleId, ')
          ..write('kind: $kind, ')
          ..write('path: $path, ')
          ..write('durationMs: $durationMs, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode =>
      Object.hash(id, entryId, capsuleId, kind, path, durationMs, createdAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is JournalMediaItem &&
          other.id == this.id &&
          other.entryId == this.entryId &&
          other.capsuleId == this.capsuleId &&
          other.kind == this.kind &&
          other.path == this.path &&
          other.durationMs == this.durationMs &&
          other.createdAt == this.createdAt);
}

class JournalMediaCompanion extends UpdateCompanion<JournalMediaItem> {
  final Value<int> id;
  final Value<int?> entryId;
  final Value<int?> capsuleId;
  final Value<String> kind;
  final Value<String> path;
  final Value<int?> durationMs;
  final Value<DateTime> createdAt;
  const JournalMediaCompanion({
    this.id = const Value.absent(),
    this.entryId = const Value.absent(),
    this.capsuleId = const Value.absent(),
    this.kind = const Value.absent(),
    this.path = const Value.absent(),
    this.durationMs = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  JournalMediaCompanion.insert({
    this.id = const Value.absent(),
    this.entryId = const Value.absent(),
    this.capsuleId = const Value.absent(),
    required String kind,
    required String path,
    this.durationMs = const Value.absent(),
    this.createdAt = const Value.absent(),
  }) : kind = Value(kind),
       path = Value(path);
  static Insertable<JournalMediaItem> custom({
    Expression<int>? id,
    Expression<int>? entryId,
    Expression<int>? capsuleId,
    Expression<String>? kind,
    Expression<String>? path,
    Expression<int>? durationMs,
    Expression<DateTime>? createdAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (entryId != null) 'entry_id': entryId,
      if (capsuleId != null) 'capsule_id': capsuleId,
      if (kind != null) 'kind': kind,
      if (path != null) 'path': path,
      if (durationMs != null) 'duration_ms': durationMs,
      if (createdAt != null) 'created_at': createdAt,
    });
  }

  JournalMediaCompanion copyWith({
    Value<int>? id,
    Value<int?>? entryId,
    Value<int?>? capsuleId,
    Value<String>? kind,
    Value<String>? path,
    Value<int?>? durationMs,
    Value<DateTime>? createdAt,
  }) {
    return JournalMediaCompanion(
      id: id ?? this.id,
      entryId: entryId ?? this.entryId,
      capsuleId: capsuleId ?? this.capsuleId,
      kind: kind ?? this.kind,
      path: path ?? this.path,
      durationMs: durationMs ?? this.durationMs,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (entryId.present) {
      map['entry_id'] = Variable<int>(entryId.value);
    }
    if (capsuleId.present) {
      map['capsule_id'] = Variable<int>(capsuleId.value);
    }
    if (kind.present) {
      map['kind'] = Variable<String>(kind.value);
    }
    if (path.present) {
      map['path'] = Variable<String>(path.value);
    }
    if (durationMs.present) {
      map['duration_ms'] = Variable<int>(durationMs.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('JournalMediaCompanion(')
          ..write('id: $id, ')
          ..write('entryId: $entryId, ')
          ..write('capsuleId: $capsuleId, ')
          ..write('kind: $kind, ')
          ..write('path: $path, ')
          ..write('durationMs: $durationMs, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }
}

class $GardenTreesTable extends GardenTrees
    with TableInfo<$GardenTreesTable, GardenTree> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $GardenTreesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _dreamIdMeta = const VerificationMeta(
    'dreamId',
  );
  @override
  late final GeneratedColumn<int> dreamId = GeneratedColumn<int>(
    'dream_id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'UNIQUE REFERENCES dreams (id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _plantTypeMeta = const VerificationMeta(
    'plantType',
  );
  @override
  late final GeneratedColumn<String> plantType = GeneratedColumn<String>(
    'plant_type',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _stageMeta = const VerificationMeta('stage');
  @override
  late final GeneratedColumn<int> stage = GeneratedColumn<int>(
    'stage',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  static const VerificationMeta _slotMeta = const VerificationMeta('slot');
  @override
  late final GeneratedColumn<int> slot = GeneratedColumn<int>(
    'slot',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _plantedAtMeta = const VerificationMeta(
    'plantedAt',
  );
  @override
  late final GeneratedColumn<DateTime> plantedAt = GeneratedColumn<DateTime>(
    'planted_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  static const VerificationMeta _lastGrowthAtMeta = const VerificationMeta(
    'lastGrowthAt',
  );
  @override
  late final GeneratedColumn<DateTime> lastGrowthAt = GeneratedColumn<DateTime>(
    'last_growth_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    dreamId,
    plantType,
    stage,
    slot,
    plantedAt,
    lastGrowthAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'garden_trees';
  @override
  VerificationContext validateIntegrity(
    Insertable<GardenTree> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('dream_id')) {
      context.handle(
        _dreamIdMeta,
        dreamId.isAcceptableOrUnknown(data['dream_id']!, _dreamIdMeta),
      );
    } else if (isInserting) {
      context.missing(_dreamIdMeta);
    }
    if (data.containsKey('plant_type')) {
      context.handle(
        _plantTypeMeta,
        plantType.isAcceptableOrUnknown(data['plant_type']!, _plantTypeMeta),
      );
    } else if (isInserting) {
      context.missing(_plantTypeMeta);
    }
    if (data.containsKey('stage')) {
      context.handle(
        _stageMeta,
        stage.isAcceptableOrUnknown(data['stage']!, _stageMeta),
      );
    }
    if (data.containsKey('slot')) {
      context.handle(
        _slotMeta,
        slot.isAcceptableOrUnknown(data['slot']!, _slotMeta),
      );
    } else if (isInserting) {
      context.missing(_slotMeta);
    }
    if (data.containsKey('planted_at')) {
      context.handle(
        _plantedAtMeta,
        plantedAt.isAcceptableOrUnknown(data['planted_at']!, _plantedAtMeta),
      );
    }
    if (data.containsKey('last_growth_at')) {
      context.handle(
        _lastGrowthAtMeta,
        lastGrowthAt.isAcceptableOrUnknown(
          data['last_growth_at']!,
          _lastGrowthAtMeta,
        ),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  GardenTree map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return GardenTree(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      dreamId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}dream_id'],
      )!,
      plantType: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}plant_type'],
      )!,
      stage: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}stage'],
      )!,
      slot: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}slot'],
      )!,
      plantedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}planted_at'],
      )!,
      lastGrowthAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}last_growth_at'],
      ),
    );
  }

  @override
  $GardenTreesTable createAlias(String alias) {
    return $GardenTreesTable(attachedDatabase, alias);
  }
}

class GardenTree extends DataClass implements Insertable<GardenTree> {
  final int id;
  final int dreamId;
  final String plantType;
  final int stage;
  final int slot;
  final DateTime plantedAt;
  final DateTime? lastGrowthAt;
  const GardenTree({
    required this.id,
    required this.dreamId,
    required this.plantType,
    required this.stage,
    required this.slot,
    required this.plantedAt,
    this.lastGrowthAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['dream_id'] = Variable<int>(dreamId);
    map['plant_type'] = Variable<String>(plantType);
    map['stage'] = Variable<int>(stage);
    map['slot'] = Variable<int>(slot);
    map['planted_at'] = Variable<DateTime>(plantedAt);
    if (!nullToAbsent || lastGrowthAt != null) {
      map['last_growth_at'] = Variable<DateTime>(lastGrowthAt);
    }
    return map;
  }

  GardenTreesCompanion toCompanion(bool nullToAbsent) {
    return GardenTreesCompanion(
      id: Value(id),
      dreamId: Value(dreamId),
      plantType: Value(plantType),
      stage: Value(stage),
      slot: Value(slot),
      plantedAt: Value(plantedAt),
      lastGrowthAt: lastGrowthAt == null && nullToAbsent
          ? const Value.absent()
          : Value(lastGrowthAt),
    );
  }

  factory GardenTree.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return GardenTree(
      id: serializer.fromJson<int>(json['id']),
      dreamId: serializer.fromJson<int>(json['dreamId']),
      plantType: serializer.fromJson<String>(json['plantType']),
      stage: serializer.fromJson<int>(json['stage']),
      slot: serializer.fromJson<int>(json['slot']),
      plantedAt: serializer.fromJson<DateTime>(json['plantedAt']),
      lastGrowthAt: serializer.fromJson<DateTime?>(json['lastGrowthAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'dreamId': serializer.toJson<int>(dreamId),
      'plantType': serializer.toJson<String>(plantType),
      'stage': serializer.toJson<int>(stage),
      'slot': serializer.toJson<int>(slot),
      'plantedAt': serializer.toJson<DateTime>(plantedAt),
      'lastGrowthAt': serializer.toJson<DateTime?>(lastGrowthAt),
    };
  }

  GardenTree copyWith({
    int? id,
    int? dreamId,
    String? plantType,
    int? stage,
    int? slot,
    DateTime? plantedAt,
    Value<DateTime?> lastGrowthAt = const Value.absent(),
  }) => GardenTree(
    id: id ?? this.id,
    dreamId: dreamId ?? this.dreamId,
    plantType: plantType ?? this.plantType,
    stage: stage ?? this.stage,
    slot: slot ?? this.slot,
    plantedAt: plantedAt ?? this.plantedAt,
    lastGrowthAt: lastGrowthAt.present ? lastGrowthAt.value : this.lastGrowthAt,
  );
  GardenTree copyWithCompanion(GardenTreesCompanion data) {
    return GardenTree(
      id: data.id.present ? data.id.value : this.id,
      dreamId: data.dreamId.present ? data.dreamId.value : this.dreamId,
      plantType: data.plantType.present ? data.plantType.value : this.plantType,
      stage: data.stage.present ? data.stage.value : this.stage,
      slot: data.slot.present ? data.slot.value : this.slot,
      plantedAt: data.plantedAt.present ? data.plantedAt.value : this.plantedAt,
      lastGrowthAt: data.lastGrowthAt.present
          ? data.lastGrowthAt.value
          : this.lastGrowthAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('GardenTree(')
          ..write('id: $id, ')
          ..write('dreamId: $dreamId, ')
          ..write('plantType: $plantType, ')
          ..write('stage: $stage, ')
          ..write('slot: $slot, ')
          ..write('plantedAt: $plantedAt, ')
          ..write('lastGrowthAt: $lastGrowthAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode =>
      Object.hash(id, dreamId, plantType, stage, slot, plantedAt, lastGrowthAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is GardenTree &&
          other.id == this.id &&
          other.dreamId == this.dreamId &&
          other.plantType == this.plantType &&
          other.stage == this.stage &&
          other.slot == this.slot &&
          other.plantedAt == this.plantedAt &&
          other.lastGrowthAt == this.lastGrowthAt);
}

class GardenTreesCompanion extends UpdateCompanion<GardenTree> {
  final Value<int> id;
  final Value<int> dreamId;
  final Value<String> plantType;
  final Value<int> stage;
  final Value<int> slot;
  final Value<DateTime> plantedAt;
  final Value<DateTime?> lastGrowthAt;
  const GardenTreesCompanion({
    this.id = const Value.absent(),
    this.dreamId = const Value.absent(),
    this.plantType = const Value.absent(),
    this.stage = const Value.absent(),
    this.slot = const Value.absent(),
    this.plantedAt = const Value.absent(),
    this.lastGrowthAt = const Value.absent(),
  });
  GardenTreesCompanion.insert({
    this.id = const Value.absent(),
    required int dreamId,
    required String plantType,
    this.stage = const Value.absent(),
    required int slot,
    this.plantedAt = const Value.absent(),
    this.lastGrowthAt = const Value.absent(),
  }) : dreamId = Value(dreamId),
       plantType = Value(plantType),
       slot = Value(slot);
  static Insertable<GardenTree> custom({
    Expression<int>? id,
    Expression<int>? dreamId,
    Expression<String>? plantType,
    Expression<int>? stage,
    Expression<int>? slot,
    Expression<DateTime>? plantedAt,
    Expression<DateTime>? lastGrowthAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (dreamId != null) 'dream_id': dreamId,
      if (plantType != null) 'plant_type': plantType,
      if (stage != null) 'stage': stage,
      if (slot != null) 'slot': slot,
      if (plantedAt != null) 'planted_at': plantedAt,
      if (lastGrowthAt != null) 'last_growth_at': lastGrowthAt,
    });
  }

  GardenTreesCompanion copyWith({
    Value<int>? id,
    Value<int>? dreamId,
    Value<String>? plantType,
    Value<int>? stage,
    Value<int>? slot,
    Value<DateTime>? plantedAt,
    Value<DateTime?>? lastGrowthAt,
  }) {
    return GardenTreesCompanion(
      id: id ?? this.id,
      dreamId: dreamId ?? this.dreamId,
      plantType: plantType ?? this.plantType,
      stage: stage ?? this.stage,
      slot: slot ?? this.slot,
      plantedAt: plantedAt ?? this.plantedAt,
      lastGrowthAt: lastGrowthAt ?? this.lastGrowthAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (dreamId.present) {
      map['dream_id'] = Variable<int>(dreamId.value);
    }
    if (plantType.present) {
      map['plant_type'] = Variable<String>(plantType.value);
    }
    if (stage.present) {
      map['stage'] = Variable<int>(stage.value);
    }
    if (slot.present) {
      map['slot'] = Variable<int>(slot.value);
    }
    if (plantedAt.present) {
      map['planted_at'] = Variable<DateTime>(plantedAt.value);
    }
    if (lastGrowthAt.present) {
      map['last_growth_at'] = Variable<DateTime>(lastGrowthAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('GardenTreesCompanion(')
          ..write('id: $id, ')
          ..write('dreamId: $dreamId, ')
          ..write('plantType: $plantType, ')
          ..write('stage: $stage, ')
          ..write('slot: $slot, ')
          ..write('plantedAt: $plantedAt, ')
          ..write('lastGrowthAt: $lastGrowthAt')
          ..write(')'))
        .toString();
  }
}

class $AchievementsTable extends Achievements
    with TableInfo<$AchievementsTable, Achievement> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $AchievementsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _codeMeta = const VerificationMeta('code');
  @override
  late final GeneratedColumn<String> code = GeneratedColumn<String>(
    'code',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways('UNIQUE'),
  );
  static const VerificationMeta _unlockedAtMeta = const VerificationMeta(
    'unlockedAt',
  );
  @override
  late final GeneratedColumn<DateTime> unlockedAt = GeneratedColumn<DateTime>(
    'unlocked_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  @override
  List<GeneratedColumn> get $columns => [id, code, unlockedAt];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'achievements';
  @override
  VerificationContext validateIntegrity(
    Insertable<Achievement> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('code')) {
      context.handle(
        _codeMeta,
        code.isAcceptableOrUnknown(data['code']!, _codeMeta),
      );
    } else if (isInserting) {
      context.missing(_codeMeta);
    }
    if (data.containsKey('unlocked_at')) {
      context.handle(
        _unlockedAtMeta,
        unlockedAt.isAcceptableOrUnknown(data['unlocked_at']!, _unlockedAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  Achievement map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return Achievement(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      code: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}code'],
      )!,
      unlockedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}unlocked_at'],
      )!,
    );
  }

  @override
  $AchievementsTable createAlias(String alias) {
    return $AchievementsTable(attachedDatabase, alias);
  }
}

class Achievement extends DataClass implements Insertable<Achievement> {
  final int id;
  final String code;
  final DateTime unlockedAt;
  const Achievement({
    required this.id,
    required this.code,
    required this.unlockedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['code'] = Variable<String>(code);
    map['unlocked_at'] = Variable<DateTime>(unlockedAt);
    return map;
  }

  AchievementsCompanion toCompanion(bool nullToAbsent) {
    return AchievementsCompanion(
      id: Value(id),
      code: Value(code),
      unlockedAt: Value(unlockedAt),
    );
  }

  factory Achievement.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return Achievement(
      id: serializer.fromJson<int>(json['id']),
      code: serializer.fromJson<String>(json['code']),
      unlockedAt: serializer.fromJson<DateTime>(json['unlockedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'code': serializer.toJson<String>(code),
      'unlockedAt': serializer.toJson<DateTime>(unlockedAt),
    };
  }

  Achievement copyWith({int? id, String? code, DateTime? unlockedAt}) =>
      Achievement(
        id: id ?? this.id,
        code: code ?? this.code,
        unlockedAt: unlockedAt ?? this.unlockedAt,
      );
  Achievement copyWithCompanion(AchievementsCompanion data) {
    return Achievement(
      id: data.id.present ? data.id.value : this.id,
      code: data.code.present ? data.code.value : this.code,
      unlockedAt: data.unlockedAt.present
          ? data.unlockedAt.value
          : this.unlockedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('Achievement(')
          ..write('id: $id, ')
          ..write('code: $code, ')
          ..write('unlockedAt: $unlockedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(id, code, unlockedAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is Achievement &&
          other.id == this.id &&
          other.code == this.code &&
          other.unlockedAt == this.unlockedAt);
}

class AchievementsCompanion extends UpdateCompanion<Achievement> {
  final Value<int> id;
  final Value<String> code;
  final Value<DateTime> unlockedAt;
  const AchievementsCompanion({
    this.id = const Value.absent(),
    this.code = const Value.absent(),
    this.unlockedAt = const Value.absent(),
  });
  AchievementsCompanion.insert({
    this.id = const Value.absent(),
    required String code,
    this.unlockedAt = const Value.absent(),
  }) : code = Value(code);
  static Insertable<Achievement> custom({
    Expression<int>? id,
    Expression<String>? code,
    Expression<DateTime>? unlockedAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (code != null) 'code': code,
      if (unlockedAt != null) 'unlocked_at': unlockedAt,
    });
  }

  AchievementsCompanion copyWith({
    Value<int>? id,
    Value<String>? code,
    Value<DateTime>? unlockedAt,
  }) {
    return AchievementsCompanion(
      id: id ?? this.id,
      code: code ?? this.code,
      unlockedAt: unlockedAt ?? this.unlockedAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (code.present) {
      map['code'] = Variable<String>(code.value);
    }
    if (unlockedAt.present) {
      map['unlocked_at'] = Variable<DateTime>(unlockedAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('AchievementsCompanion(')
          ..write('id: $id, ')
          ..write('code: $code, ')
          ..write('unlockedAt: $unlockedAt')
          ..write(')'))
        .toString();
  }
}

class $FocusSessionsTable extends FocusSessions
    with TableInfo<$FocusSessionsTable, FocusSession> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $FocusSessionsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _startedAtMeta = const VerificationMeta(
    'startedAt',
  );
  @override
  late final GeneratedColumn<DateTime> startedAt = GeneratedColumn<DateTime>(
    'started_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _endedAtMeta = const VerificationMeta(
    'endedAt',
  );
  @override
  late final GeneratedColumn<DateTime> endedAt = GeneratedColumn<DateTime>(
    'ended_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _plannedSecondsMeta = const VerificationMeta(
    'plannedSeconds',
  );
  @override
  late final GeneratedColumn<int> plannedSeconds = GeneratedColumn<int>(
    'planned_seconds',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _actualSecondsMeta = const VerificationMeta(
    'actualSeconds',
  );
  @override
  late final GeneratedColumn<int> actualSeconds = GeneratedColumn<int>(
    'actual_seconds',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _kindMeta = const VerificationMeta('kind');
  @override
  late final GeneratedColumn<String> kind = GeneratedColumn<String>(
    'kind',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('work'),
  );
  static const VerificationMeta _completedMeta = const VerificationMeta(
    'completed',
  );
  @override
  late final GeneratedColumn<bool> completed = GeneratedColumn<bool>(
    'completed',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("completed" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _goalIdMeta = const VerificationMeta('goalId');
  @override
  late final GeneratedColumn<int> goalId = GeneratedColumn<int>(
    'goal_id',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES goals (id) ON DELETE SET NULL',
    ),
  );
  static const VerificationMeta _dreamIdMeta = const VerificationMeta(
    'dreamId',
  );
  @override
  late final GeneratedColumn<int> dreamId = GeneratedColumn<int>(
    'dream_id',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES dreams (id) ON DELETE SET NULL',
    ),
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    startedAt,
    endedAt,
    plannedSeconds,
    actualSeconds,
    kind,
    completed,
    goalId,
    dreamId,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'focus_sessions';
  @override
  VerificationContext validateIntegrity(
    Insertable<FocusSession> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('started_at')) {
      context.handle(
        _startedAtMeta,
        startedAt.isAcceptableOrUnknown(data['started_at']!, _startedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_startedAtMeta);
    }
    if (data.containsKey('ended_at')) {
      context.handle(
        _endedAtMeta,
        endedAt.isAcceptableOrUnknown(data['ended_at']!, _endedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_endedAtMeta);
    }
    if (data.containsKey('planned_seconds')) {
      context.handle(
        _plannedSecondsMeta,
        plannedSeconds.isAcceptableOrUnknown(
          data['planned_seconds']!,
          _plannedSecondsMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_plannedSecondsMeta);
    }
    if (data.containsKey('actual_seconds')) {
      context.handle(
        _actualSecondsMeta,
        actualSeconds.isAcceptableOrUnknown(
          data['actual_seconds']!,
          _actualSecondsMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_actualSecondsMeta);
    }
    if (data.containsKey('kind')) {
      context.handle(
        _kindMeta,
        kind.isAcceptableOrUnknown(data['kind']!, _kindMeta),
      );
    }
    if (data.containsKey('completed')) {
      context.handle(
        _completedMeta,
        completed.isAcceptableOrUnknown(data['completed']!, _completedMeta),
      );
    }
    if (data.containsKey('goal_id')) {
      context.handle(
        _goalIdMeta,
        goalId.isAcceptableOrUnknown(data['goal_id']!, _goalIdMeta),
      );
    }
    if (data.containsKey('dream_id')) {
      context.handle(
        _dreamIdMeta,
        dreamId.isAcceptableOrUnknown(data['dream_id']!, _dreamIdMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  FocusSession map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return FocusSession(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      startedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}started_at'],
      )!,
      endedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}ended_at'],
      )!,
      plannedSeconds: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}planned_seconds'],
      )!,
      actualSeconds: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}actual_seconds'],
      )!,
      kind: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}kind'],
      )!,
      completed: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}completed'],
      )!,
      goalId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}goal_id'],
      ),
      dreamId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}dream_id'],
      ),
    );
  }

  @override
  $FocusSessionsTable createAlias(String alias) {
    return $FocusSessionsTable(attachedDatabase, alias);
  }
}

class FocusSession extends DataClass implements Insertable<FocusSession> {
  final int id;
  final DateTime startedAt;
  final DateTime endedAt;
  final int plannedSeconds;
  final int actualSeconds;
  final String kind;
  final bool completed;
  final int? goalId;
  final int? dreamId;
  const FocusSession({
    required this.id,
    required this.startedAt,
    required this.endedAt,
    required this.plannedSeconds,
    required this.actualSeconds,
    required this.kind,
    required this.completed,
    this.goalId,
    this.dreamId,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['started_at'] = Variable<DateTime>(startedAt);
    map['ended_at'] = Variable<DateTime>(endedAt);
    map['planned_seconds'] = Variable<int>(plannedSeconds);
    map['actual_seconds'] = Variable<int>(actualSeconds);
    map['kind'] = Variable<String>(kind);
    map['completed'] = Variable<bool>(completed);
    if (!nullToAbsent || goalId != null) {
      map['goal_id'] = Variable<int>(goalId);
    }
    if (!nullToAbsent || dreamId != null) {
      map['dream_id'] = Variable<int>(dreamId);
    }
    return map;
  }

  FocusSessionsCompanion toCompanion(bool nullToAbsent) {
    return FocusSessionsCompanion(
      id: Value(id),
      startedAt: Value(startedAt),
      endedAt: Value(endedAt),
      plannedSeconds: Value(plannedSeconds),
      actualSeconds: Value(actualSeconds),
      kind: Value(kind),
      completed: Value(completed),
      goalId: goalId == null && nullToAbsent
          ? const Value.absent()
          : Value(goalId),
      dreamId: dreamId == null && nullToAbsent
          ? const Value.absent()
          : Value(dreamId),
    );
  }

  factory FocusSession.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return FocusSession(
      id: serializer.fromJson<int>(json['id']),
      startedAt: serializer.fromJson<DateTime>(json['startedAt']),
      endedAt: serializer.fromJson<DateTime>(json['endedAt']),
      plannedSeconds: serializer.fromJson<int>(json['plannedSeconds']),
      actualSeconds: serializer.fromJson<int>(json['actualSeconds']),
      kind: serializer.fromJson<String>(json['kind']),
      completed: serializer.fromJson<bool>(json['completed']),
      goalId: serializer.fromJson<int?>(json['goalId']),
      dreamId: serializer.fromJson<int?>(json['dreamId']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'startedAt': serializer.toJson<DateTime>(startedAt),
      'endedAt': serializer.toJson<DateTime>(endedAt),
      'plannedSeconds': serializer.toJson<int>(plannedSeconds),
      'actualSeconds': serializer.toJson<int>(actualSeconds),
      'kind': serializer.toJson<String>(kind),
      'completed': serializer.toJson<bool>(completed),
      'goalId': serializer.toJson<int?>(goalId),
      'dreamId': serializer.toJson<int?>(dreamId),
    };
  }

  FocusSession copyWith({
    int? id,
    DateTime? startedAt,
    DateTime? endedAt,
    int? plannedSeconds,
    int? actualSeconds,
    String? kind,
    bool? completed,
    Value<int?> goalId = const Value.absent(),
    Value<int?> dreamId = const Value.absent(),
  }) => FocusSession(
    id: id ?? this.id,
    startedAt: startedAt ?? this.startedAt,
    endedAt: endedAt ?? this.endedAt,
    plannedSeconds: plannedSeconds ?? this.plannedSeconds,
    actualSeconds: actualSeconds ?? this.actualSeconds,
    kind: kind ?? this.kind,
    completed: completed ?? this.completed,
    goalId: goalId.present ? goalId.value : this.goalId,
    dreamId: dreamId.present ? dreamId.value : this.dreamId,
  );
  FocusSession copyWithCompanion(FocusSessionsCompanion data) {
    return FocusSession(
      id: data.id.present ? data.id.value : this.id,
      startedAt: data.startedAt.present ? data.startedAt.value : this.startedAt,
      endedAt: data.endedAt.present ? data.endedAt.value : this.endedAt,
      plannedSeconds: data.plannedSeconds.present
          ? data.plannedSeconds.value
          : this.plannedSeconds,
      actualSeconds: data.actualSeconds.present
          ? data.actualSeconds.value
          : this.actualSeconds,
      kind: data.kind.present ? data.kind.value : this.kind,
      completed: data.completed.present ? data.completed.value : this.completed,
      goalId: data.goalId.present ? data.goalId.value : this.goalId,
      dreamId: data.dreamId.present ? data.dreamId.value : this.dreamId,
    );
  }

  @override
  String toString() {
    return (StringBuffer('FocusSession(')
          ..write('id: $id, ')
          ..write('startedAt: $startedAt, ')
          ..write('endedAt: $endedAt, ')
          ..write('plannedSeconds: $plannedSeconds, ')
          ..write('actualSeconds: $actualSeconds, ')
          ..write('kind: $kind, ')
          ..write('completed: $completed, ')
          ..write('goalId: $goalId, ')
          ..write('dreamId: $dreamId')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    startedAt,
    endedAt,
    plannedSeconds,
    actualSeconds,
    kind,
    completed,
    goalId,
    dreamId,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is FocusSession &&
          other.id == this.id &&
          other.startedAt == this.startedAt &&
          other.endedAt == this.endedAt &&
          other.plannedSeconds == this.plannedSeconds &&
          other.actualSeconds == this.actualSeconds &&
          other.kind == this.kind &&
          other.completed == this.completed &&
          other.goalId == this.goalId &&
          other.dreamId == this.dreamId);
}

class FocusSessionsCompanion extends UpdateCompanion<FocusSession> {
  final Value<int> id;
  final Value<DateTime> startedAt;
  final Value<DateTime> endedAt;
  final Value<int> plannedSeconds;
  final Value<int> actualSeconds;
  final Value<String> kind;
  final Value<bool> completed;
  final Value<int?> goalId;
  final Value<int?> dreamId;
  const FocusSessionsCompanion({
    this.id = const Value.absent(),
    this.startedAt = const Value.absent(),
    this.endedAt = const Value.absent(),
    this.plannedSeconds = const Value.absent(),
    this.actualSeconds = const Value.absent(),
    this.kind = const Value.absent(),
    this.completed = const Value.absent(),
    this.goalId = const Value.absent(),
    this.dreamId = const Value.absent(),
  });
  FocusSessionsCompanion.insert({
    this.id = const Value.absent(),
    required DateTime startedAt,
    required DateTime endedAt,
    required int plannedSeconds,
    required int actualSeconds,
    this.kind = const Value.absent(),
    this.completed = const Value.absent(),
    this.goalId = const Value.absent(),
    this.dreamId = const Value.absent(),
  }) : startedAt = Value(startedAt),
       endedAt = Value(endedAt),
       plannedSeconds = Value(plannedSeconds),
       actualSeconds = Value(actualSeconds);
  static Insertable<FocusSession> custom({
    Expression<int>? id,
    Expression<DateTime>? startedAt,
    Expression<DateTime>? endedAt,
    Expression<int>? plannedSeconds,
    Expression<int>? actualSeconds,
    Expression<String>? kind,
    Expression<bool>? completed,
    Expression<int>? goalId,
    Expression<int>? dreamId,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (startedAt != null) 'started_at': startedAt,
      if (endedAt != null) 'ended_at': endedAt,
      if (plannedSeconds != null) 'planned_seconds': plannedSeconds,
      if (actualSeconds != null) 'actual_seconds': actualSeconds,
      if (kind != null) 'kind': kind,
      if (completed != null) 'completed': completed,
      if (goalId != null) 'goal_id': goalId,
      if (dreamId != null) 'dream_id': dreamId,
    });
  }

  FocusSessionsCompanion copyWith({
    Value<int>? id,
    Value<DateTime>? startedAt,
    Value<DateTime>? endedAt,
    Value<int>? plannedSeconds,
    Value<int>? actualSeconds,
    Value<String>? kind,
    Value<bool>? completed,
    Value<int?>? goalId,
    Value<int?>? dreamId,
  }) {
    return FocusSessionsCompanion(
      id: id ?? this.id,
      startedAt: startedAt ?? this.startedAt,
      endedAt: endedAt ?? this.endedAt,
      plannedSeconds: plannedSeconds ?? this.plannedSeconds,
      actualSeconds: actualSeconds ?? this.actualSeconds,
      kind: kind ?? this.kind,
      completed: completed ?? this.completed,
      goalId: goalId ?? this.goalId,
      dreamId: dreamId ?? this.dreamId,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (startedAt.present) {
      map['started_at'] = Variable<DateTime>(startedAt.value);
    }
    if (endedAt.present) {
      map['ended_at'] = Variable<DateTime>(endedAt.value);
    }
    if (plannedSeconds.present) {
      map['planned_seconds'] = Variable<int>(plannedSeconds.value);
    }
    if (actualSeconds.present) {
      map['actual_seconds'] = Variable<int>(actualSeconds.value);
    }
    if (kind.present) {
      map['kind'] = Variable<String>(kind.value);
    }
    if (completed.present) {
      map['completed'] = Variable<bool>(completed.value);
    }
    if (goalId.present) {
      map['goal_id'] = Variable<int>(goalId.value);
    }
    if (dreamId.present) {
      map['dream_id'] = Variable<int>(dreamId.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('FocusSessionsCompanion(')
          ..write('id: $id, ')
          ..write('startedAt: $startedAt, ')
          ..write('endedAt: $endedAt, ')
          ..write('plannedSeconds: $plannedSeconds, ')
          ..write('actualSeconds: $actualSeconds, ')
          ..write('kind: $kind, ')
          ..write('completed: $completed, ')
          ..write('goalId: $goalId, ')
          ..write('dreamId: $dreamId')
          ..write(')'))
        .toString();
  }
}

class $LifeBalanceEntriesTable extends LifeBalanceEntries
    with TableInfo<$LifeBalanceEntriesTable, LifeBalanceEntry> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $LifeBalanceEntriesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _healthMeta = const VerificationMeta('health');
  @override
  late final GeneratedColumn<int> health = GeneratedColumn<int>(
    'health',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _financeMeta = const VerificationMeta(
    'finance',
  );
  @override
  late final GeneratedColumn<int> finance = GeneratedColumn<int>(
    'finance',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _careerMeta = const VerificationMeta('career');
  @override
  late final GeneratedColumn<int> career = GeneratedColumn<int>(
    'career',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _educationMeta = const VerificationMeta(
    'education',
  );
  @override
  late final GeneratedColumn<int> education = GeneratedColumn<int>(
    'education',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _familyMeta = const VerificationMeta('family');
  @override
  late final GeneratedColumn<int> family = GeneratedColumn<int>(
    'family',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _socialMeta = const VerificationMeta('social');
  @override
  late final GeneratedColumn<int> social = GeneratedColumn<int>(
    'social',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _growthMeta = const VerificationMeta('growth');
  @override
  late final GeneratedColumn<int> growth = GeneratedColumn<int>(
    'growth',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _hobbiesMeta = const VerificationMeta(
    'hobbies',
  );
  @override
  late final GeneratedColumn<int> hobbies = GeneratedColumn<int>(
    'hobbies',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _noteMeta = const VerificationMeta('note');
  @override
  late final GeneratedColumn<String> note = GeneratedColumn<String>(
    'note',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant(''),
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    health,
    finance,
    career,
    education,
    family,
    social,
    growth,
    hobbies,
    note,
    createdAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'life_balance_entries';
  @override
  VerificationContext validateIntegrity(
    Insertable<LifeBalanceEntry> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('health')) {
      context.handle(
        _healthMeta,
        health.isAcceptableOrUnknown(data['health']!, _healthMeta),
      );
    } else if (isInserting) {
      context.missing(_healthMeta);
    }
    if (data.containsKey('finance')) {
      context.handle(
        _financeMeta,
        finance.isAcceptableOrUnknown(data['finance']!, _financeMeta),
      );
    } else if (isInserting) {
      context.missing(_financeMeta);
    }
    if (data.containsKey('career')) {
      context.handle(
        _careerMeta,
        career.isAcceptableOrUnknown(data['career']!, _careerMeta),
      );
    } else if (isInserting) {
      context.missing(_careerMeta);
    }
    if (data.containsKey('education')) {
      context.handle(
        _educationMeta,
        education.isAcceptableOrUnknown(data['education']!, _educationMeta),
      );
    } else if (isInserting) {
      context.missing(_educationMeta);
    }
    if (data.containsKey('family')) {
      context.handle(
        _familyMeta,
        family.isAcceptableOrUnknown(data['family']!, _familyMeta),
      );
    } else if (isInserting) {
      context.missing(_familyMeta);
    }
    if (data.containsKey('social')) {
      context.handle(
        _socialMeta,
        social.isAcceptableOrUnknown(data['social']!, _socialMeta),
      );
    } else if (isInserting) {
      context.missing(_socialMeta);
    }
    if (data.containsKey('growth')) {
      context.handle(
        _growthMeta,
        growth.isAcceptableOrUnknown(data['growth']!, _growthMeta),
      );
    } else if (isInserting) {
      context.missing(_growthMeta);
    }
    if (data.containsKey('hobbies')) {
      context.handle(
        _hobbiesMeta,
        hobbies.isAcceptableOrUnknown(data['hobbies']!, _hobbiesMeta),
      );
    } else if (isInserting) {
      context.missing(_hobbiesMeta);
    }
    if (data.containsKey('note')) {
      context.handle(
        _noteMeta,
        note.isAcceptableOrUnknown(data['note']!, _noteMeta),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  LifeBalanceEntry map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LifeBalanceEntry(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      health: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}health'],
      )!,
      finance: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}finance'],
      )!,
      career: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}career'],
      )!,
      education: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}education'],
      )!,
      family: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}family'],
      )!,
      social: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}social'],
      )!,
      growth: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}growth'],
      )!,
      hobbies: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}hobbies'],
      )!,
      note: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}note'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
    );
  }

  @override
  $LifeBalanceEntriesTable createAlias(String alias) {
    return $LifeBalanceEntriesTable(attachedDatabase, alias);
  }
}

class LifeBalanceEntry extends DataClass
    implements Insertable<LifeBalanceEntry> {
  final int id;
  final int health;
  final int finance;
  final int career;
  final int education;
  final int family;
  final int social;
  final int growth;
  final int hobbies;
  final String note;
  final DateTime createdAt;
  const LifeBalanceEntry({
    required this.id,
    required this.health,
    required this.finance,
    required this.career,
    required this.education,
    required this.family,
    required this.social,
    required this.growth,
    required this.hobbies,
    required this.note,
    required this.createdAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['health'] = Variable<int>(health);
    map['finance'] = Variable<int>(finance);
    map['career'] = Variable<int>(career);
    map['education'] = Variable<int>(education);
    map['family'] = Variable<int>(family);
    map['social'] = Variable<int>(social);
    map['growth'] = Variable<int>(growth);
    map['hobbies'] = Variable<int>(hobbies);
    map['note'] = Variable<String>(note);
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  LifeBalanceEntriesCompanion toCompanion(bool nullToAbsent) {
    return LifeBalanceEntriesCompanion(
      id: Value(id),
      health: Value(health),
      finance: Value(finance),
      career: Value(career),
      education: Value(education),
      family: Value(family),
      social: Value(social),
      growth: Value(growth),
      hobbies: Value(hobbies),
      note: Value(note),
      createdAt: Value(createdAt),
    );
  }

  factory LifeBalanceEntry.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LifeBalanceEntry(
      id: serializer.fromJson<int>(json['id']),
      health: serializer.fromJson<int>(json['health']),
      finance: serializer.fromJson<int>(json['finance']),
      career: serializer.fromJson<int>(json['career']),
      education: serializer.fromJson<int>(json['education']),
      family: serializer.fromJson<int>(json['family']),
      social: serializer.fromJson<int>(json['social']),
      growth: serializer.fromJson<int>(json['growth']),
      hobbies: serializer.fromJson<int>(json['hobbies']),
      note: serializer.fromJson<String>(json['note']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'health': serializer.toJson<int>(health),
      'finance': serializer.toJson<int>(finance),
      'career': serializer.toJson<int>(career),
      'education': serializer.toJson<int>(education),
      'family': serializer.toJson<int>(family),
      'social': serializer.toJson<int>(social),
      'growth': serializer.toJson<int>(growth),
      'hobbies': serializer.toJson<int>(hobbies),
      'note': serializer.toJson<String>(note),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  LifeBalanceEntry copyWith({
    int? id,
    int? health,
    int? finance,
    int? career,
    int? education,
    int? family,
    int? social,
    int? growth,
    int? hobbies,
    String? note,
    DateTime? createdAt,
  }) => LifeBalanceEntry(
    id: id ?? this.id,
    health: health ?? this.health,
    finance: finance ?? this.finance,
    career: career ?? this.career,
    education: education ?? this.education,
    family: family ?? this.family,
    social: social ?? this.social,
    growth: growth ?? this.growth,
    hobbies: hobbies ?? this.hobbies,
    note: note ?? this.note,
    createdAt: createdAt ?? this.createdAt,
  );
  LifeBalanceEntry copyWithCompanion(LifeBalanceEntriesCompanion data) {
    return LifeBalanceEntry(
      id: data.id.present ? data.id.value : this.id,
      health: data.health.present ? data.health.value : this.health,
      finance: data.finance.present ? data.finance.value : this.finance,
      career: data.career.present ? data.career.value : this.career,
      education: data.education.present ? data.education.value : this.education,
      family: data.family.present ? data.family.value : this.family,
      social: data.social.present ? data.social.value : this.social,
      growth: data.growth.present ? data.growth.value : this.growth,
      hobbies: data.hobbies.present ? data.hobbies.value : this.hobbies,
      note: data.note.present ? data.note.value : this.note,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LifeBalanceEntry(')
          ..write('id: $id, ')
          ..write('health: $health, ')
          ..write('finance: $finance, ')
          ..write('career: $career, ')
          ..write('education: $education, ')
          ..write('family: $family, ')
          ..write('social: $social, ')
          ..write('growth: $growth, ')
          ..write('hobbies: $hobbies, ')
          ..write('note: $note, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    health,
    finance,
    career,
    education,
    family,
    social,
    growth,
    hobbies,
    note,
    createdAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LifeBalanceEntry &&
          other.id == this.id &&
          other.health == this.health &&
          other.finance == this.finance &&
          other.career == this.career &&
          other.education == this.education &&
          other.family == this.family &&
          other.social == this.social &&
          other.growth == this.growth &&
          other.hobbies == this.hobbies &&
          other.note == this.note &&
          other.createdAt == this.createdAt);
}

class LifeBalanceEntriesCompanion extends UpdateCompanion<LifeBalanceEntry> {
  final Value<int> id;
  final Value<int> health;
  final Value<int> finance;
  final Value<int> career;
  final Value<int> education;
  final Value<int> family;
  final Value<int> social;
  final Value<int> growth;
  final Value<int> hobbies;
  final Value<String> note;
  final Value<DateTime> createdAt;
  const LifeBalanceEntriesCompanion({
    this.id = const Value.absent(),
    this.health = const Value.absent(),
    this.finance = const Value.absent(),
    this.career = const Value.absent(),
    this.education = const Value.absent(),
    this.family = const Value.absent(),
    this.social = const Value.absent(),
    this.growth = const Value.absent(),
    this.hobbies = const Value.absent(),
    this.note = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  LifeBalanceEntriesCompanion.insert({
    this.id = const Value.absent(),
    required int health,
    required int finance,
    required int career,
    required int education,
    required int family,
    required int social,
    required int growth,
    required int hobbies,
    this.note = const Value.absent(),
    this.createdAt = const Value.absent(),
  }) : health = Value(health),
       finance = Value(finance),
       career = Value(career),
       education = Value(education),
       family = Value(family),
       social = Value(social),
       growth = Value(growth),
       hobbies = Value(hobbies);
  static Insertable<LifeBalanceEntry> custom({
    Expression<int>? id,
    Expression<int>? health,
    Expression<int>? finance,
    Expression<int>? career,
    Expression<int>? education,
    Expression<int>? family,
    Expression<int>? social,
    Expression<int>? growth,
    Expression<int>? hobbies,
    Expression<String>? note,
    Expression<DateTime>? createdAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (health != null) 'health': health,
      if (finance != null) 'finance': finance,
      if (career != null) 'career': career,
      if (education != null) 'education': education,
      if (family != null) 'family': family,
      if (social != null) 'social': social,
      if (growth != null) 'growth': growth,
      if (hobbies != null) 'hobbies': hobbies,
      if (note != null) 'note': note,
      if (createdAt != null) 'created_at': createdAt,
    });
  }

  LifeBalanceEntriesCompanion copyWith({
    Value<int>? id,
    Value<int>? health,
    Value<int>? finance,
    Value<int>? career,
    Value<int>? education,
    Value<int>? family,
    Value<int>? social,
    Value<int>? growth,
    Value<int>? hobbies,
    Value<String>? note,
    Value<DateTime>? createdAt,
  }) {
    return LifeBalanceEntriesCompanion(
      id: id ?? this.id,
      health: health ?? this.health,
      finance: finance ?? this.finance,
      career: career ?? this.career,
      education: education ?? this.education,
      family: family ?? this.family,
      social: social ?? this.social,
      growth: growth ?? this.growth,
      hobbies: hobbies ?? this.hobbies,
      note: note ?? this.note,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (health.present) {
      map['health'] = Variable<int>(health.value);
    }
    if (finance.present) {
      map['finance'] = Variable<int>(finance.value);
    }
    if (career.present) {
      map['career'] = Variable<int>(career.value);
    }
    if (education.present) {
      map['education'] = Variable<int>(education.value);
    }
    if (family.present) {
      map['family'] = Variable<int>(family.value);
    }
    if (social.present) {
      map['social'] = Variable<int>(social.value);
    }
    if (growth.present) {
      map['growth'] = Variable<int>(growth.value);
    }
    if (hobbies.present) {
      map['hobbies'] = Variable<int>(hobbies.value);
    }
    if (note.present) {
      map['note'] = Variable<String>(note.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('LifeBalanceEntriesCompanion(')
          ..write('id: $id, ')
          ..write('health: $health, ')
          ..write('finance: $finance, ')
          ..write('career: $career, ')
          ..write('education: $education, ')
          ..write('family: $family, ')
          ..write('social: $social, ')
          ..write('growth: $growth, ')
          ..write('hobbies: $hobbies, ')
          ..write('note: $note, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }
}

class $RemindersTable extends Reminders
    with TableInfo<$RemindersTable, Reminder> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $RemindersTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _kindMeta = const VerificationMeta('kind');
  @override
  late final GeneratedColumn<String> kind = GeneratedColumn<String>(
    'kind',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _refIdMeta = const VerificationMeta('refId');
  @override
  late final GeneratedColumn<int> refId = GeneratedColumn<int>(
    'ref_id',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _titleMeta = const VerificationMeta('title');
  @override
  late final GeneratedColumn<String> title = GeneratedColumn<String>(
    'title',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _bodyMeta = const VerificationMeta('body');
  @override
  late final GeneratedColumn<String> body = GeneratedColumn<String>(
    'body',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant(''),
  );
  static const VerificationMeta _atMeta = const VerificationMeta('at');
  @override
  late final GeneratedColumn<DateTime> at = GeneratedColumn<DateTime>(
    'at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _repeatMeta = const VerificationMeta('repeat');
  @override
  late final GeneratedColumn<String> repeat = GeneratedColumn<String>(
    'repeat',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('none'),
  );
  static const VerificationMeta _weekdaysMaskMeta = const VerificationMeta(
    'weekdaysMask',
  );
  @override
  late final GeneratedColumn<int> weekdaysMask = GeneratedColumn<int>(
    'weekdays_mask',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _enabledMeta = const VerificationMeta(
    'enabled',
  );
  @override
  late final GeneratedColumn<bool> enabled = GeneratedColumn<bool>(
    'enabled',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("enabled" IN (0, 1))',
    ),
    defaultValue: const Constant(true),
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    kind,
    refId,
    title,
    body,
    at,
    repeat,
    weekdaysMask,
    enabled,
    createdAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'reminders';
  @override
  VerificationContext validateIntegrity(
    Insertable<Reminder> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('kind')) {
      context.handle(
        _kindMeta,
        kind.isAcceptableOrUnknown(data['kind']!, _kindMeta),
      );
    } else if (isInserting) {
      context.missing(_kindMeta);
    }
    if (data.containsKey('ref_id')) {
      context.handle(
        _refIdMeta,
        refId.isAcceptableOrUnknown(data['ref_id']!, _refIdMeta),
      );
    }
    if (data.containsKey('title')) {
      context.handle(
        _titleMeta,
        title.isAcceptableOrUnknown(data['title']!, _titleMeta),
      );
    } else if (isInserting) {
      context.missing(_titleMeta);
    }
    if (data.containsKey('body')) {
      context.handle(
        _bodyMeta,
        body.isAcceptableOrUnknown(data['body']!, _bodyMeta),
      );
    }
    if (data.containsKey('at')) {
      context.handle(_atMeta, at.isAcceptableOrUnknown(data['at']!, _atMeta));
    } else if (isInserting) {
      context.missing(_atMeta);
    }
    if (data.containsKey('repeat')) {
      context.handle(
        _repeatMeta,
        repeat.isAcceptableOrUnknown(data['repeat']!, _repeatMeta),
      );
    }
    if (data.containsKey('weekdays_mask')) {
      context.handle(
        _weekdaysMaskMeta,
        weekdaysMask.isAcceptableOrUnknown(
          data['weekdays_mask']!,
          _weekdaysMaskMeta,
        ),
      );
    }
    if (data.containsKey('enabled')) {
      context.handle(
        _enabledMeta,
        enabled.isAcceptableOrUnknown(data['enabled']!, _enabledMeta),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  Reminder map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return Reminder(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      kind: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}kind'],
      )!,
      refId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}ref_id'],
      ),
      title: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}title'],
      )!,
      body: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}body'],
      )!,
      at: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}at'],
      )!,
      repeat: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}repeat'],
      )!,
      weekdaysMask: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}weekdays_mask'],
      ),
      enabled: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}enabled'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
    );
  }

  @override
  $RemindersTable createAlias(String alias) {
    return $RemindersTable(attachedDatabase, alias);
  }
}

class Reminder extends DataClass implements Insertable<Reminder> {
  final int id;
  final String kind;
  final int? refId;
  final String title;
  final String body;
  final DateTime at;
  final String repeat;
  final int? weekdaysMask;
  final bool enabled;
  final DateTime createdAt;
  const Reminder({
    required this.id,
    required this.kind,
    this.refId,
    required this.title,
    required this.body,
    required this.at,
    required this.repeat,
    this.weekdaysMask,
    required this.enabled,
    required this.createdAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['kind'] = Variable<String>(kind);
    if (!nullToAbsent || refId != null) {
      map['ref_id'] = Variable<int>(refId);
    }
    map['title'] = Variable<String>(title);
    map['body'] = Variable<String>(body);
    map['at'] = Variable<DateTime>(at);
    map['repeat'] = Variable<String>(repeat);
    if (!nullToAbsent || weekdaysMask != null) {
      map['weekdays_mask'] = Variable<int>(weekdaysMask);
    }
    map['enabled'] = Variable<bool>(enabled);
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  RemindersCompanion toCompanion(bool nullToAbsent) {
    return RemindersCompanion(
      id: Value(id),
      kind: Value(kind),
      refId: refId == null && nullToAbsent
          ? const Value.absent()
          : Value(refId),
      title: Value(title),
      body: Value(body),
      at: Value(at),
      repeat: Value(repeat),
      weekdaysMask: weekdaysMask == null && nullToAbsent
          ? const Value.absent()
          : Value(weekdaysMask),
      enabled: Value(enabled),
      createdAt: Value(createdAt),
    );
  }

  factory Reminder.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return Reminder(
      id: serializer.fromJson<int>(json['id']),
      kind: serializer.fromJson<String>(json['kind']),
      refId: serializer.fromJson<int?>(json['refId']),
      title: serializer.fromJson<String>(json['title']),
      body: serializer.fromJson<String>(json['body']),
      at: serializer.fromJson<DateTime>(json['at']),
      repeat: serializer.fromJson<String>(json['repeat']),
      weekdaysMask: serializer.fromJson<int?>(json['weekdaysMask']),
      enabled: serializer.fromJson<bool>(json['enabled']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'kind': serializer.toJson<String>(kind),
      'refId': serializer.toJson<int?>(refId),
      'title': serializer.toJson<String>(title),
      'body': serializer.toJson<String>(body),
      'at': serializer.toJson<DateTime>(at),
      'repeat': serializer.toJson<String>(repeat),
      'weekdaysMask': serializer.toJson<int?>(weekdaysMask),
      'enabled': serializer.toJson<bool>(enabled),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  Reminder copyWith({
    int? id,
    String? kind,
    Value<int?> refId = const Value.absent(),
    String? title,
    String? body,
    DateTime? at,
    String? repeat,
    Value<int?> weekdaysMask = const Value.absent(),
    bool? enabled,
    DateTime? createdAt,
  }) => Reminder(
    id: id ?? this.id,
    kind: kind ?? this.kind,
    refId: refId.present ? refId.value : this.refId,
    title: title ?? this.title,
    body: body ?? this.body,
    at: at ?? this.at,
    repeat: repeat ?? this.repeat,
    weekdaysMask: weekdaysMask.present ? weekdaysMask.value : this.weekdaysMask,
    enabled: enabled ?? this.enabled,
    createdAt: createdAt ?? this.createdAt,
  );
  Reminder copyWithCompanion(RemindersCompanion data) {
    return Reminder(
      id: data.id.present ? data.id.value : this.id,
      kind: data.kind.present ? data.kind.value : this.kind,
      refId: data.refId.present ? data.refId.value : this.refId,
      title: data.title.present ? data.title.value : this.title,
      body: data.body.present ? data.body.value : this.body,
      at: data.at.present ? data.at.value : this.at,
      repeat: data.repeat.present ? data.repeat.value : this.repeat,
      weekdaysMask: data.weekdaysMask.present
          ? data.weekdaysMask.value
          : this.weekdaysMask,
      enabled: data.enabled.present ? data.enabled.value : this.enabled,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('Reminder(')
          ..write('id: $id, ')
          ..write('kind: $kind, ')
          ..write('refId: $refId, ')
          ..write('title: $title, ')
          ..write('body: $body, ')
          ..write('at: $at, ')
          ..write('repeat: $repeat, ')
          ..write('weekdaysMask: $weekdaysMask, ')
          ..write('enabled: $enabled, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    kind,
    refId,
    title,
    body,
    at,
    repeat,
    weekdaysMask,
    enabled,
    createdAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is Reminder &&
          other.id == this.id &&
          other.kind == this.kind &&
          other.refId == this.refId &&
          other.title == this.title &&
          other.body == this.body &&
          other.at == this.at &&
          other.repeat == this.repeat &&
          other.weekdaysMask == this.weekdaysMask &&
          other.enabled == this.enabled &&
          other.createdAt == this.createdAt);
}

class RemindersCompanion extends UpdateCompanion<Reminder> {
  final Value<int> id;
  final Value<String> kind;
  final Value<int?> refId;
  final Value<String> title;
  final Value<String> body;
  final Value<DateTime> at;
  final Value<String> repeat;
  final Value<int?> weekdaysMask;
  final Value<bool> enabled;
  final Value<DateTime> createdAt;
  const RemindersCompanion({
    this.id = const Value.absent(),
    this.kind = const Value.absent(),
    this.refId = const Value.absent(),
    this.title = const Value.absent(),
    this.body = const Value.absent(),
    this.at = const Value.absent(),
    this.repeat = const Value.absent(),
    this.weekdaysMask = const Value.absent(),
    this.enabled = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  RemindersCompanion.insert({
    this.id = const Value.absent(),
    required String kind,
    this.refId = const Value.absent(),
    required String title,
    this.body = const Value.absent(),
    required DateTime at,
    this.repeat = const Value.absent(),
    this.weekdaysMask = const Value.absent(),
    this.enabled = const Value.absent(),
    this.createdAt = const Value.absent(),
  }) : kind = Value(kind),
       title = Value(title),
       at = Value(at);
  static Insertable<Reminder> custom({
    Expression<int>? id,
    Expression<String>? kind,
    Expression<int>? refId,
    Expression<String>? title,
    Expression<String>? body,
    Expression<DateTime>? at,
    Expression<String>? repeat,
    Expression<int>? weekdaysMask,
    Expression<bool>? enabled,
    Expression<DateTime>? createdAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (kind != null) 'kind': kind,
      if (refId != null) 'ref_id': refId,
      if (title != null) 'title': title,
      if (body != null) 'body': body,
      if (at != null) 'at': at,
      if (repeat != null) 'repeat': repeat,
      if (weekdaysMask != null) 'weekdays_mask': weekdaysMask,
      if (enabled != null) 'enabled': enabled,
      if (createdAt != null) 'created_at': createdAt,
    });
  }

  RemindersCompanion copyWith({
    Value<int>? id,
    Value<String>? kind,
    Value<int?>? refId,
    Value<String>? title,
    Value<String>? body,
    Value<DateTime>? at,
    Value<String>? repeat,
    Value<int?>? weekdaysMask,
    Value<bool>? enabled,
    Value<DateTime>? createdAt,
  }) {
    return RemindersCompanion(
      id: id ?? this.id,
      kind: kind ?? this.kind,
      refId: refId ?? this.refId,
      title: title ?? this.title,
      body: body ?? this.body,
      at: at ?? this.at,
      repeat: repeat ?? this.repeat,
      weekdaysMask: weekdaysMask ?? this.weekdaysMask,
      enabled: enabled ?? this.enabled,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (kind.present) {
      map['kind'] = Variable<String>(kind.value);
    }
    if (refId.present) {
      map['ref_id'] = Variable<int>(refId.value);
    }
    if (title.present) {
      map['title'] = Variable<String>(title.value);
    }
    if (body.present) {
      map['body'] = Variable<String>(body.value);
    }
    if (at.present) {
      map['at'] = Variable<DateTime>(at.value);
    }
    if (repeat.present) {
      map['repeat'] = Variable<String>(repeat.value);
    }
    if (weekdaysMask.present) {
      map['weekdays_mask'] = Variable<int>(weekdaysMask.value);
    }
    if (enabled.present) {
      map['enabled'] = Variable<bool>(enabled.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('RemindersCompanion(')
          ..write('id: $id, ')
          ..write('kind: $kind, ')
          ..write('refId: $refId, ')
          ..write('title: $title, ')
          ..write('body: $body, ')
          ..write('at: $at, ')
          ..write('repeat: $repeat, ')
          ..write('weekdaysMask: $weekdaysMask, ')
          ..write('enabled: $enabled, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }
}

class $ActivityLogTable extends ActivityLog
    with TableInfo<$ActivityLogTable, ActivityLogEntry> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $ActivityLogTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _typeMeta = const VerificationMeta('type');
  @override
  late final GeneratedColumn<String> type = GeneratedColumn<String>(
    'type',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _refIdMeta = const VerificationMeta('refId');
  @override
  late final GeneratedColumn<int> refId = GeneratedColumn<int>(
    'ref_id',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _titleMeta = const VerificationMeta('title');
  @override
  late final GeneratedColumn<String> title = GeneratedColumn<String>(
    'title',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _pointsMeta = const VerificationMeta('points');
  @override
  late final GeneratedColumn<int> points = GeneratedColumn<int>(
    'points',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    type,
    refId,
    title,
    points,
    createdAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'activity_log';
  @override
  VerificationContext validateIntegrity(
    Insertable<ActivityLogEntry> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('type')) {
      context.handle(
        _typeMeta,
        type.isAcceptableOrUnknown(data['type']!, _typeMeta),
      );
    } else if (isInserting) {
      context.missing(_typeMeta);
    }
    if (data.containsKey('ref_id')) {
      context.handle(
        _refIdMeta,
        refId.isAcceptableOrUnknown(data['ref_id']!, _refIdMeta),
      );
    }
    if (data.containsKey('title')) {
      context.handle(
        _titleMeta,
        title.isAcceptableOrUnknown(data['title']!, _titleMeta),
      );
    } else if (isInserting) {
      context.missing(_titleMeta);
    }
    if (data.containsKey('points')) {
      context.handle(
        _pointsMeta,
        points.isAcceptableOrUnknown(data['points']!, _pointsMeta),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  ActivityLogEntry map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return ActivityLogEntry(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      type: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}type'],
      )!,
      refId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}ref_id'],
      ),
      title: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}title'],
      )!,
      points: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}points'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
    );
  }

  @override
  $ActivityLogTable createAlias(String alias) {
    return $ActivityLogTable(attachedDatabase, alias);
  }
}

class ActivityLogEntry extends DataClass
    implements Insertable<ActivityLogEntry> {
  final int id;
  final String type;
  final int? refId;
  final String title;
  final int points;
  final DateTime createdAt;
  const ActivityLogEntry({
    required this.id,
    required this.type,
    this.refId,
    required this.title,
    required this.points,
    required this.createdAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['type'] = Variable<String>(type);
    if (!nullToAbsent || refId != null) {
      map['ref_id'] = Variable<int>(refId);
    }
    map['title'] = Variable<String>(title);
    map['points'] = Variable<int>(points);
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  ActivityLogCompanion toCompanion(bool nullToAbsent) {
    return ActivityLogCompanion(
      id: Value(id),
      type: Value(type),
      refId: refId == null && nullToAbsent
          ? const Value.absent()
          : Value(refId),
      title: Value(title),
      points: Value(points),
      createdAt: Value(createdAt),
    );
  }

  factory ActivityLogEntry.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return ActivityLogEntry(
      id: serializer.fromJson<int>(json['id']),
      type: serializer.fromJson<String>(json['type']),
      refId: serializer.fromJson<int?>(json['refId']),
      title: serializer.fromJson<String>(json['title']),
      points: serializer.fromJson<int>(json['points']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'type': serializer.toJson<String>(type),
      'refId': serializer.toJson<int?>(refId),
      'title': serializer.toJson<String>(title),
      'points': serializer.toJson<int>(points),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  ActivityLogEntry copyWith({
    int? id,
    String? type,
    Value<int?> refId = const Value.absent(),
    String? title,
    int? points,
    DateTime? createdAt,
  }) => ActivityLogEntry(
    id: id ?? this.id,
    type: type ?? this.type,
    refId: refId.present ? refId.value : this.refId,
    title: title ?? this.title,
    points: points ?? this.points,
    createdAt: createdAt ?? this.createdAt,
  );
  ActivityLogEntry copyWithCompanion(ActivityLogCompanion data) {
    return ActivityLogEntry(
      id: data.id.present ? data.id.value : this.id,
      type: data.type.present ? data.type.value : this.type,
      refId: data.refId.present ? data.refId.value : this.refId,
      title: data.title.present ? data.title.value : this.title,
      points: data.points.present ? data.points.value : this.points,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('ActivityLogEntry(')
          ..write('id: $id, ')
          ..write('type: $type, ')
          ..write('refId: $refId, ')
          ..write('title: $title, ')
          ..write('points: $points, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(id, type, refId, title, points, createdAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is ActivityLogEntry &&
          other.id == this.id &&
          other.type == this.type &&
          other.refId == this.refId &&
          other.title == this.title &&
          other.points == this.points &&
          other.createdAt == this.createdAt);
}

class ActivityLogCompanion extends UpdateCompanion<ActivityLogEntry> {
  final Value<int> id;
  final Value<String> type;
  final Value<int?> refId;
  final Value<String> title;
  final Value<int> points;
  final Value<DateTime> createdAt;
  const ActivityLogCompanion({
    this.id = const Value.absent(),
    this.type = const Value.absent(),
    this.refId = const Value.absent(),
    this.title = const Value.absent(),
    this.points = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  ActivityLogCompanion.insert({
    this.id = const Value.absent(),
    required String type,
    this.refId = const Value.absent(),
    required String title,
    this.points = const Value.absent(),
    this.createdAt = const Value.absent(),
  }) : type = Value(type),
       title = Value(title);
  static Insertable<ActivityLogEntry> custom({
    Expression<int>? id,
    Expression<String>? type,
    Expression<int>? refId,
    Expression<String>? title,
    Expression<int>? points,
    Expression<DateTime>? createdAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (type != null) 'type': type,
      if (refId != null) 'ref_id': refId,
      if (title != null) 'title': title,
      if (points != null) 'points': points,
      if (createdAt != null) 'created_at': createdAt,
    });
  }

  ActivityLogCompanion copyWith({
    Value<int>? id,
    Value<String>? type,
    Value<int?>? refId,
    Value<String>? title,
    Value<int>? points,
    Value<DateTime>? createdAt,
  }) {
    return ActivityLogCompanion(
      id: id ?? this.id,
      type: type ?? this.type,
      refId: refId ?? this.refId,
      title: title ?? this.title,
      points: points ?? this.points,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (type.present) {
      map['type'] = Variable<String>(type.value);
    }
    if (refId.present) {
      map['ref_id'] = Variable<int>(refId.value);
    }
    if (title.present) {
      map['title'] = Variable<String>(title.value);
    }
    if (points.present) {
      map['points'] = Variable<int>(points.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('ActivityLogCompanion(')
          ..write('id: $id, ')
          ..write('type: $type, ')
          ..write('refId: $refId, ')
          ..write('title: $title, ')
          ..write('points: $points, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }
}

abstract class _$AppDatabase extends GeneratedDatabase {
  _$AppDatabase(QueryExecutor e) : super(e);
  $AppDatabaseManager get managers => $AppDatabaseManager(this);
  late final $UserProfilesTable userProfiles = $UserProfilesTable(this);
  late final $AppSettingsTable appSettings = $AppSettingsTable(this);
  late final $DreamCategoriesTable dreamCategories = $DreamCategoriesTable(
    this,
  );
  late final $DreamsTable dreams = $DreamsTable(this);
  late final $DreamMilestonesTable dreamMilestones = $DreamMilestonesTable(
    this,
  );
  late final $DreamMediaTable dreamMedia = $DreamMediaTable(this);
  late final $GoalsTable goals = $GoalsTable(this);
  late final $TasksTable tasks = $TasksTable(this);
  late final $HabitsTable habits = $HabitsTable(this);
  late final $HabitCompletionsTable habitCompletions = $HabitCompletionsTable(
    this,
  );
  late final $SavingsGoalsTable savingsGoals = $SavingsGoalsTable(this);
  late final $SavingsTransactionsTable savingsTransactions =
      $SavingsTransactionsTable(this);
  late final $JournalEntriesTable journalEntries = $JournalEntriesTable(this);
  late final $TimeCapsulesTable timeCapsules = $TimeCapsulesTable(this);
  late final $JournalMediaTable journalMedia = $JournalMediaTable(this);
  late final $GardenTreesTable gardenTrees = $GardenTreesTable(this);
  late final $AchievementsTable achievements = $AchievementsTable(this);
  late final $FocusSessionsTable focusSessions = $FocusSessionsTable(this);
  late final $LifeBalanceEntriesTable lifeBalanceEntries =
      $LifeBalanceEntriesTable(this);
  late final $RemindersTable reminders = $RemindersTable(this);
  late final $ActivityLogTable activityLog = $ActivityLogTable(this);
  late final Index idxDreamsCategory = Index(
    'idx_dreams_category',
    'CREATE INDEX idx_dreams_category ON dreams (category_id)',
  );
  late final Index idxDreamsStatus = Index(
    'idx_dreams_status',
    'CREATE INDEX idx_dreams_status ON dreams (archived_at, is_completed)',
  );
  late final Index idxMilestonesDream = Index(
    'idx_milestones_dream',
    'CREATE INDEX idx_milestones_dream ON dream_milestones (dream_id, sort_order)',
  );
  late final Index idxMediaDream = Index(
    'idx_media_dream',
    'CREATE INDEX idx_media_dream ON dream_media (dream_id)',
  );
  late final Index idxMediaBoard = Index(
    'idx_media_board',
    'CREATE INDEX idx_media_board ON dream_media (show_on_board, sort_order)',
  );
  late final Index idxGoalsDream = Index(
    'idx_goals_dream',
    'CREATE INDEX idx_goals_dream ON goals (dream_id)',
  );
  late final Index idxTasksDue = Index(
    'idx_tasks_due',
    'CREATE INDEX idx_tasks_due ON tasks (is_completed, due_date)',
  );
  late final Index idxTasksGoal = Index(
    'idx_tasks_goal',
    'CREATE INDEX idx_tasks_goal ON tasks (goal_id)',
  );
  late final Index idxHabitCompletionDay = Index(
    'idx_habit_completion_day',
    'CREATE INDEX idx_habit_completion_day ON habit_completions (day)',
  );
  late final Index idxTxGoalDate = Index(
    'idx_tx_goal_date',
    'CREATE INDEX idx_tx_goal_date ON savings_transactions (savings_goal_id, date)',
  );
  late final Index idxJournalDate = Index(
    'idx_journal_date',
    'CREATE INDEX idx_journal_date ON journal_entries (date)',
  );
  late final Index idxFocusStarted = Index(
    'idx_focus_started',
    'CREATE INDEX idx_focus_started ON focus_sessions (started_at)',
  );
  late final Index idxReminderRef = Index(
    'idx_reminder_ref',
    'CREATE INDEX idx_reminder_ref ON reminders (kind, ref_id)',
  );
  late final Index idxActivityDate = Index(
    'idx_activity_date',
    'CREATE INDEX idx_activity_date ON activity_log (created_at)',
  );
  @override
  Iterable<TableInfo<Table, Object?>> get allTables =>
      allSchemaEntities.whereType<TableInfo<Table, Object?>>();
  @override
  List<DatabaseSchemaEntity> get allSchemaEntities => [
    userProfiles,
    appSettings,
    dreamCategories,
    dreams,
    dreamMilestones,
    dreamMedia,
    goals,
    tasks,
    habits,
    habitCompletions,
    savingsGoals,
    savingsTransactions,
    journalEntries,
    timeCapsules,
    journalMedia,
    gardenTrees,
    achievements,
    focusSessions,
    lifeBalanceEntries,
    reminders,
    activityLog,
    idxDreamsCategory,
    idxDreamsStatus,
    idxMilestonesDream,
    idxMediaDream,
    idxMediaBoard,
    idxGoalsDream,
    idxTasksDue,
    idxTasksGoal,
    idxHabitCompletionDay,
    idxTxGoalDate,
    idxJournalDate,
    idxFocusStarted,
    idxReminderRef,
    idxActivityDate,
  ];
  @override
  StreamQueryUpdateRules get streamUpdateRules => const StreamQueryUpdateRules([
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'dreams',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('dream_milestones', kind: UpdateKind.delete)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'dreams',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('dream_media', kind: UpdateKind.delete)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'dreams',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('goals', kind: UpdateKind.update)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'goals',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('tasks', kind: UpdateKind.delete)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'dreams',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('tasks', kind: UpdateKind.update)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'dreams',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('habits', kind: UpdateKind.update)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'habits',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('habit_completions', kind: UpdateKind.delete)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'dreams',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('savings_goals', kind: UpdateKind.update)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'savings_goals',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('savings_transactions', kind: UpdateKind.delete)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'journal_entries',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('journal_media', kind: UpdateKind.delete)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'time_capsules',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('journal_media', kind: UpdateKind.delete)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'dreams',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('garden_trees', kind: UpdateKind.delete)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'goals',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('focus_sessions', kind: UpdateKind.update)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'dreams',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('focus_sessions', kind: UpdateKind.update)],
    ),
  ]);
}

typedef $$UserProfilesTableCreateCompanionBuilder =
    UserProfilesCompanion Function({
      Value<int> id,
      Value<String> name,
      Value<String?> avatarPath,
      Value<String> focusCategories,
      Value<DateTime> createdAt,
    });
typedef $$UserProfilesTableUpdateCompanionBuilder =
    UserProfilesCompanion Function({
      Value<int> id,
      Value<String> name,
      Value<String?> avatarPath,
      Value<String> focusCategories,
      Value<DateTime> createdAt,
    });

class $$UserProfilesTableFilterComposer
    extends Composer<_$AppDatabase, $UserProfilesTable> {
  $$UserProfilesTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get name => $composableBuilder(
    column: $table.name,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get avatarPath => $composableBuilder(
    column: $table.avatarPath,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get focusCategories => $composableBuilder(
    column: $table.focusCategories,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $$UserProfilesTableOrderingComposer
    extends Composer<_$AppDatabase, $UserProfilesTable> {
  $$UserProfilesTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get name => $composableBuilder(
    column: $table.name,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get avatarPath => $composableBuilder(
    column: $table.avatarPath,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get focusCategories => $composableBuilder(
    column: $table.focusCategories,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$UserProfilesTableAnnotationComposer
    extends Composer<_$AppDatabase, $UserProfilesTable> {
  $$UserProfilesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get name =>
      $composableBuilder(column: $table.name, builder: (column) => column);

  GeneratedColumn<String> get avatarPath => $composableBuilder(
    column: $table.avatarPath,
    builder: (column) => column,
  );

  GeneratedColumn<String> get focusCategories => $composableBuilder(
    column: $table.focusCategories,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);
}

class $$UserProfilesTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $UserProfilesTable,
          UserProfile,
          $$UserProfilesTableFilterComposer,
          $$UserProfilesTableOrderingComposer,
          $$UserProfilesTableAnnotationComposer,
          $$UserProfilesTableCreateCompanionBuilder,
          $$UserProfilesTableUpdateCompanionBuilder,
          (
            UserProfile,
            BaseReferences<_$AppDatabase, $UserProfilesTable, UserProfile>,
          ),
          UserProfile,
          PrefetchHooks Function()
        > {
  $$UserProfilesTableTableManager(_$AppDatabase db, $UserProfilesTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$UserProfilesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$UserProfilesTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$UserProfilesTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> name = const Value.absent(),
                Value<String?> avatarPath = const Value.absent(),
                Value<String> focusCategories = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => UserProfilesCompanion(
                id: id,
                name: name,
                avatarPath: avatarPath,
                focusCategories: focusCategories,
                createdAt: createdAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> name = const Value.absent(),
                Value<String?> avatarPath = const Value.absent(),
                Value<String> focusCategories = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => UserProfilesCompanion.insert(
                id: id,
                name: name,
                avatarPath: avatarPath,
                focusCategories: focusCategories,
                createdAt: createdAt,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$UserProfilesTable, UserProfile>(table),
                  BaseReferences<
                    _$AppDatabase,
                    $UserProfilesTable,
                    UserProfile
                  >(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$UserProfilesTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $UserProfilesTable,
      UserProfile,
      $$UserProfilesTableFilterComposer,
      $$UserProfilesTableOrderingComposer,
      $$UserProfilesTableAnnotationComposer,
      $$UserProfilesTableCreateCompanionBuilder,
      $$UserProfilesTableUpdateCompanionBuilder,
      (
        UserProfile,
        BaseReferences<_$AppDatabase, $UserProfilesTable, UserProfile>,
      ),
      UserProfile,
      PrefetchHooks Function()
    >;
typedef $$AppSettingsTableCreateCompanionBuilder =
    AppSettingsCompanion Function({
      required String key,
      required String value,
      Value<int> rowid,
    });
typedef $$AppSettingsTableUpdateCompanionBuilder =
    AppSettingsCompanion Function({
      Value<String> key,
      Value<String> value,
      Value<int> rowid,
    });

class $$AppSettingsTableFilterComposer
    extends Composer<_$AppDatabase, $AppSettingsTable> {
  $$AppSettingsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get key => $composableBuilder(
    column: $table.key,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get value => $composableBuilder(
    column: $table.value,
    builder: (column) => ColumnFilters(column),
  );
}

class $$AppSettingsTableOrderingComposer
    extends Composer<_$AppDatabase, $AppSettingsTable> {
  $$AppSettingsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get key => $composableBuilder(
    column: $table.key,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get value => $composableBuilder(
    column: $table.value,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$AppSettingsTableAnnotationComposer
    extends Composer<_$AppDatabase, $AppSettingsTable> {
  $$AppSettingsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get key =>
      $composableBuilder(column: $table.key, builder: (column) => column);

  GeneratedColumn<String> get value =>
      $composableBuilder(column: $table.value, builder: (column) => column);
}

class $$AppSettingsTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $AppSettingsTable,
          AppSetting,
          $$AppSettingsTableFilterComposer,
          $$AppSettingsTableOrderingComposer,
          $$AppSettingsTableAnnotationComposer,
          $$AppSettingsTableCreateCompanionBuilder,
          $$AppSettingsTableUpdateCompanionBuilder,
          (
            AppSetting,
            BaseReferences<_$AppDatabase, $AppSettingsTable, AppSetting>,
          ),
          AppSetting,
          PrefetchHooks Function()
        > {
  $$AppSettingsTableTableManager(_$AppDatabase db, $AppSettingsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$AppSettingsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$AppSettingsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$AppSettingsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> key = const Value.absent(),
                Value<String> value = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => AppSettingsCompanion(key: key, value: value, rowid: rowid),
          createCompanionCallback:
              ({
                required String key,
                required String value,
                Value<int> rowid = const Value.absent(),
              }) => AppSettingsCompanion.insert(
                key: key,
                value: value,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$AppSettingsTable, AppSetting>(table),
                  BaseReferences<_$AppDatabase, $AppSettingsTable, AppSetting>(
                    db,
                    table,
                    e,
                  ),
                ),
              )
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$AppSettingsTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $AppSettingsTable,
      AppSetting,
      $$AppSettingsTableFilterComposer,
      $$AppSettingsTableOrderingComposer,
      $$AppSettingsTableAnnotationComposer,
      $$AppSettingsTableCreateCompanionBuilder,
      $$AppSettingsTableUpdateCompanionBuilder,
      (
        AppSetting,
        BaseReferences<_$AppDatabase, $AppSettingsTable, AppSetting>,
      ),
      AppSetting,
      PrefetchHooks Function()
    >;
typedef $$DreamCategoriesTableCreateCompanionBuilder =
    DreamCategoriesCompanion Function({
      Value<int> id,
      required String name,
      Value<String> iconKey,
      required int color,
      Value<String> plantType,
      Value<bool> isBuiltIn,
      Value<int> sortOrder,
    });
typedef $$DreamCategoriesTableUpdateCompanionBuilder =
    DreamCategoriesCompanion Function({
      Value<int> id,
      Value<String> name,
      Value<String> iconKey,
      Value<int> color,
      Value<String> plantType,
      Value<bool> isBuiltIn,
      Value<int> sortOrder,
    });

final class $$DreamCategoriesTableReferences
    extends
        BaseReferences<_$AppDatabase, $DreamCategoriesTable, DreamCategory> {
  $$DreamCategoriesTableReferences(
    super.$_db,
    super.$_table,
    super.$_typedResult,
  );

  static MultiTypedResultKey<$DreamsTable, List<Dream>> _dreamsRefsTable(
    _$AppDatabase db,
  ) => MultiTypedResultKey.fromTable(
    db.dreams,
    aliasName: 'dream_categories__id__dreams__category_id',
  );

  $$DreamsTableProcessedTableManager get dreamsRefs {
    final manager = $$DreamsTableTableManager(
      $_db,
      $_db.dreams,
    ).filter((f) => f.categoryId.id.sqlEquals($_itemColumn<int>('id')!));

    final cache = $_typedResult.readTableOrNull(_dreamsRefsTable($_db));
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }
}

class $$DreamCategoriesTableFilterComposer
    extends Composer<_$AppDatabase, $DreamCategoriesTable> {
  $$DreamCategoriesTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get name => $composableBuilder(
    column: $table.name,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get iconKey => $composableBuilder(
    column: $table.iconKey,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get color => $composableBuilder(
    column: $table.color,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get plantType => $composableBuilder(
    column: $table.plantType,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get isBuiltIn => $composableBuilder(
    column: $table.isBuiltIn,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get sortOrder => $composableBuilder(
    column: $table.sortOrder,
    builder: (column) => ColumnFilters(column),
  );

  Expression<bool> dreamsRefs(
    Expression<bool> Function($$DreamsTableFilterComposer f) f,
  ) {
    final $$DreamsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.dreams,
      getReferencedColumn: (t) => t.categoryId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamsTableFilterComposer(
            $db: $db,
            $table: $db.dreams,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }
}

class $$DreamCategoriesTableOrderingComposer
    extends Composer<_$AppDatabase, $DreamCategoriesTable> {
  $$DreamCategoriesTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get name => $composableBuilder(
    column: $table.name,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get iconKey => $composableBuilder(
    column: $table.iconKey,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get color => $composableBuilder(
    column: $table.color,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get plantType => $composableBuilder(
    column: $table.plantType,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get isBuiltIn => $composableBuilder(
    column: $table.isBuiltIn,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get sortOrder => $composableBuilder(
    column: $table.sortOrder,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$DreamCategoriesTableAnnotationComposer
    extends Composer<_$AppDatabase, $DreamCategoriesTable> {
  $$DreamCategoriesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get name =>
      $composableBuilder(column: $table.name, builder: (column) => column);

  GeneratedColumn<String> get iconKey =>
      $composableBuilder(column: $table.iconKey, builder: (column) => column);

  GeneratedColumn<int> get color =>
      $composableBuilder(column: $table.color, builder: (column) => column);

  GeneratedColumn<String> get plantType =>
      $composableBuilder(column: $table.plantType, builder: (column) => column);

  GeneratedColumn<bool> get isBuiltIn =>
      $composableBuilder(column: $table.isBuiltIn, builder: (column) => column);

  GeneratedColumn<int> get sortOrder =>
      $composableBuilder(column: $table.sortOrder, builder: (column) => column);

  Expression<T> dreamsRefs<T extends Object>(
    Expression<T> Function($$DreamsTableAnnotationComposer a) f,
  ) {
    final $$DreamsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.dreams,
      getReferencedColumn: (t) => t.categoryId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamsTableAnnotationComposer(
            $db: $db,
            $table: $db.dreams,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }
}

class $$DreamCategoriesTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $DreamCategoriesTable,
          DreamCategory,
          $$DreamCategoriesTableFilterComposer,
          $$DreamCategoriesTableOrderingComposer,
          $$DreamCategoriesTableAnnotationComposer,
          $$DreamCategoriesTableCreateCompanionBuilder,
          $$DreamCategoriesTableUpdateCompanionBuilder,
          (DreamCategory, $$DreamCategoriesTableReferences),
          DreamCategory,
          PrefetchHooks Function({bool dreamsRefs})
        > {
  $$DreamCategoriesTableTableManager(
    _$AppDatabase db,
    $DreamCategoriesTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$DreamCategoriesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$DreamCategoriesTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$DreamCategoriesTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> name = const Value.absent(),
                Value<String> iconKey = const Value.absent(),
                Value<int> color = const Value.absent(),
                Value<String> plantType = const Value.absent(),
                Value<bool> isBuiltIn = const Value.absent(),
                Value<int> sortOrder = const Value.absent(),
              }) => DreamCategoriesCompanion(
                id: id,
                name: name,
                iconKey: iconKey,
                color: color,
                plantType: plantType,
                isBuiltIn: isBuiltIn,
                sortOrder: sortOrder,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required String name,
                Value<String> iconKey = const Value.absent(),
                required int color,
                Value<String> plantType = const Value.absent(),
                Value<bool> isBuiltIn = const Value.absent(),
                Value<int> sortOrder = const Value.absent(),
              }) => DreamCategoriesCompanion.insert(
                id: id,
                name: name,
                iconKey: iconKey,
                color: color,
                plantType: plantType,
                isBuiltIn: isBuiltIn,
                sortOrder: sortOrder,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$DreamCategoriesTable, DreamCategory>(table),
                  $$DreamCategoriesTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({dreamsRefs = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [if (dreamsRefs) db.dreams],
              addJoins: null,
              getPrefetchedDataCallback: (items) async {
                return [
                  if (dreamsRefs)
                    await $_getPrefetchedDataByColumns<
                      DreamCategory,
                      $DreamCategoriesTable,
                      Dream,
                      $DreamsTable
                    >(
                      currentTable: table,
                      referencedTable: $$DreamCategoriesTableReferences
                          ._dreamsRefsTable(db),
                      managerFromTypedResult: (p0) =>
                          $$DreamCategoriesTableReferences(
                            db,
                            table,
                            p0,
                          ).dreamsRefs,
                      currentColumn: 'id',
                      referencedColumn: 'category_id',
                      typedResults: items,
                    ),
                ];
              },
            );
          },
        ),
      );
}

typedef $$DreamCategoriesTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $DreamCategoriesTable,
      DreamCategory,
      $$DreamCategoriesTableFilterComposer,
      $$DreamCategoriesTableOrderingComposer,
      $$DreamCategoriesTableAnnotationComposer,
      $$DreamCategoriesTableCreateCompanionBuilder,
      $$DreamCategoriesTableUpdateCompanionBuilder,
      (DreamCategory, $$DreamCategoriesTableReferences),
      DreamCategory,
      PrefetchHooks Function({bool dreamsRefs})
    >;
typedef $$DreamsTableCreateCompanionBuilder =
    DreamsCompanion Function({
      Value<int> id,
      required String title,
      Value<String> description,
      Value<String?> coverPath,
      required int categoryId,
      Value<DateTime?> targetDate,
      Value<int> priority,
      Value<String> notes,
      Value<String> motivation,
      Value<int?> budgetMinor,
      Value<String> currency,
      Value<bool> weightedProgress,
      Value<bool> isCompleted,
      Value<DateTime?> completedAt,
      Value<bool> isHidden,
      Value<DateTime?> archivedAt,
      Value<DateTime> createdAt,
      Value<DateTime> updatedAt,
    });
typedef $$DreamsTableUpdateCompanionBuilder =
    DreamsCompanion Function({
      Value<int> id,
      Value<String> title,
      Value<String> description,
      Value<String?> coverPath,
      Value<int> categoryId,
      Value<DateTime?> targetDate,
      Value<int> priority,
      Value<String> notes,
      Value<String> motivation,
      Value<int?> budgetMinor,
      Value<String> currency,
      Value<bool> weightedProgress,
      Value<bool> isCompleted,
      Value<DateTime?> completedAt,
      Value<bool> isHidden,
      Value<DateTime?> archivedAt,
      Value<DateTime> createdAt,
      Value<DateTime> updatedAt,
    });

final class $$DreamsTableReferences
    extends BaseReferences<_$AppDatabase, $DreamsTable, Dream> {
  $$DreamsTableReferences(super.$_db, super.$_table, super.$_typedResult);

  static $DreamCategoriesTable _categoryIdTable(_$AppDatabase db) => db
      .dreamCategories
      .createAlias('dreams__category_id__dream_categories__id');

  $$DreamCategoriesTableProcessedTableManager get categoryId {
    final $_column = $_itemColumn<int>('category_id')!;

    final manager = $$DreamCategoriesTableTableManager(
      $_db,
      $_db.dreamCategories,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_categoryIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }

  static MultiTypedResultKey<$DreamMilestonesTable, List<DreamMilestone>>
  _dreamMilestonesRefsTable(_$AppDatabase db) => MultiTypedResultKey.fromTable(
    db.dreamMilestones,
    aliasName: 'dreams__id__dream_milestones__dream_id',
  );

  $$DreamMilestonesTableProcessedTableManager get dreamMilestonesRefs {
    final manager = $$DreamMilestonesTableTableManager(
      $_db,
      $_db.dreamMilestones,
    ).filter((f) => f.dreamId.id.sqlEquals($_itemColumn<int>('id')!));

    final cache = $_typedResult.readTableOrNull(
      _dreamMilestonesRefsTable($_db),
    );
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }

  static MultiTypedResultKey<$DreamMediaTable, List<DreamMediaItem>>
  _dreamMediaRefsTable(_$AppDatabase db) => MultiTypedResultKey.fromTable(
    db.dreamMedia,
    aliasName: 'dreams__id__dream_media__dream_id',
  );

  $$DreamMediaTableProcessedTableManager get dreamMediaRefs {
    final manager = $$DreamMediaTableTableManager(
      $_db,
      $_db.dreamMedia,
    ).filter((f) => f.dreamId.id.sqlEquals($_itemColumn<int>('id')!));

    final cache = $_typedResult.readTableOrNull(_dreamMediaRefsTable($_db));
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }

  static MultiTypedResultKey<$GoalsTable, List<Goal>> _goalsRefsTable(
    _$AppDatabase db,
  ) => MultiTypedResultKey.fromTable(
    db.goals,
    aliasName: 'dreams__id__goals__dream_id',
  );

  $$GoalsTableProcessedTableManager get goalsRefs {
    final manager = $$GoalsTableTableManager(
      $_db,
      $_db.goals,
    ).filter((f) => f.dreamId.id.sqlEquals($_itemColumn<int>('id')!));

    final cache = $_typedResult.readTableOrNull(_goalsRefsTable($_db));
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }

  static MultiTypedResultKey<$TasksTable, List<TaskItem>> _tasksRefsTable(
    _$AppDatabase db,
  ) => MultiTypedResultKey.fromTable(
    db.tasks,
    aliasName: 'dreams__id__tasks__dream_id',
  );

  $$TasksTableProcessedTableManager get tasksRefs {
    final manager = $$TasksTableTableManager(
      $_db,
      $_db.tasks,
    ).filter((f) => f.dreamId.id.sqlEquals($_itemColumn<int>('id')!));

    final cache = $_typedResult.readTableOrNull(_tasksRefsTable($_db));
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }

  static MultiTypedResultKey<$HabitsTable, List<Habit>> _habitsRefsTable(
    _$AppDatabase db,
  ) => MultiTypedResultKey.fromTable(
    db.habits,
    aliasName: 'dreams__id__habits__dream_id',
  );

  $$HabitsTableProcessedTableManager get habitsRefs {
    final manager = $$HabitsTableTableManager(
      $_db,
      $_db.habits,
    ).filter((f) => f.dreamId.id.sqlEquals($_itemColumn<int>('id')!));

    final cache = $_typedResult.readTableOrNull(_habitsRefsTable($_db));
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }

  static MultiTypedResultKey<$SavingsGoalsTable, List<SavingsGoal>>
  _savingsGoalsRefsTable(_$AppDatabase db) => MultiTypedResultKey.fromTable(
    db.savingsGoals,
    aliasName: 'dreams__id__savings_goals__dream_id',
  );

  $$SavingsGoalsTableProcessedTableManager get savingsGoalsRefs {
    final manager = $$SavingsGoalsTableTableManager(
      $_db,
      $_db.savingsGoals,
    ).filter((f) => f.dreamId.id.sqlEquals($_itemColumn<int>('id')!));

    final cache = $_typedResult.readTableOrNull(_savingsGoalsRefsTable($_db));
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }

  static MultiTypedResultKey<$GardenTreesTable, List<GardenTree>>
  _gardenTreesRefsTable(_$AppDatabase db) => MultiTypedResultKey.fromTable(
    db.gardenTrees,
    aliasName: 'dreams__id__garden_trees__dream_id',
  );

  $$GardenTreesTableProcessedTableManager get gardenTreesRefs {
    final manager = $$GardenTreesTableTableManager(
      $_db,
      $_db.gardenTrees,
    ).filter((f) => f.dreamId.id.sqlEquals($_itemColumn<int>('id')!));

    final cache = $_typedResult.readTableOrNull(_gardenTreesRefsTable($_db));
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }

  static MultiTypedResultKey<$FocusSessionsTable, List<FocusSession>>
  _focusSessionsRefsTable(_$AppDatabase db) => MultiTypedResultKey.fromTable(
    db.focusSessions,
    aliasName: 'dreams__id__focus_sessions__dream_id',
  );

  $$FocusSessionsTableProcessedTableManager get focusSessionsRefs {
    final manager = $$FocusSessionsTableTableManager(
      $_db,
      $_db.focusSessions,
    ).filter((f) => f.dreamId.id.sqlEquals($_itemColumn<int>('id')!));

    final cache = $_typedResult.readTableOrNull(_focusSessionsRefsTable($_db));
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }
}

class $$DreamsTableFilterComposer
    extends Composer<_$AppDatabase, $DreamsTable> {
  $$DreamsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get description => $composableBuilder(
    column: $table.description,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get coverPath => $composableBuilder(
    column: $table.coverPath,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get targetDate => $composableBuilder(
    column: $table.targetDate,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get priority => $composableBuilder(
    column: $table.priority,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get notes => $composableBuilder(
    column: $table.notes,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get motivation => $composableBuilder(
    column: $table.motivation,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get budgetMinor => $composableBuilder(
    column: $table.budgetMinor,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get currency => $composableBuilder(
    column: $table.currency,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get weightedProgress => $composableBuilder(
    column: $table.weightedProgress,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get isCompleted => $composableBuilder(
    column: $table.isCompleted,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get completedAt => $composableBuilder(
    column: $table.completedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get isHidden => $composableBuilder(
    column: $table.isHidden,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get archivedAt => $composableBuilder(
    column: $table.archivedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnFilters(column),
  );

  $$DreamCategoriesTableFilterComposer get categoryId {
    final $$DreamCategoriesTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.categoryId,
      referencedTable: $db.dreamCategories,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamCategoriesTableFilterComposer(
            $db: $db,
            $table: $db.dreamCategories,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  Expression<bool> dreamMilestonesRefs(
    Expression<bool> Function($$DreamMilestonesTableFilterComposer f) f,
  ) {
    final $$DreamMilestonesTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.dreamMilestones,
      getReferencedColumn: (t) => t.dreamId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamMilestonesTableFilterComposer(
            $db: $db,
            $table: $db.dreamMilestones,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<bool> dreamMediaRefs(
    Expression<bool> Function($$DreamMediaTableFilterComposer f) f,
  ) {
    final $$DreamMediaTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.dreamMedia,
      getReferencedColumn: (t) => t.dreamId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamMediaTableFilterComposer(
            $db: $db,
            $table: $db.dreamMedia,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<bool> goalsRefs(
    Expression<bool> Function($$GoalsTableFilterComposer f) f,
  ) {
    final $$GoalsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.goals,
      getReferencedColumn: (t) => t.dreamId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$GoalsTableFilterComposer(
            $db: $db,
            $table: $db.goals,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<bool> tasksRefs(
    Expression<bool> Function($$TasksTableFilterComposer f) f,
  ) {
    final $$TasksTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.tasks,
      getReferencedColumn: (t) => t.dreamId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$TasksTableFilterComposer(
            $db: $db,
            $table: $db.tasks,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<bool> habitsRefs(
    Expression<bool> Function($$HabitsTableFilterComposer f) f,
  ) {
    final $$HabitsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.habits,
      getReferencedColumn: (t) => t.dreamId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$HabitsTableFilterComposer(
            $db: $db,
            $table: $db.habits,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<bool> savingsGoalsRefs(
    Expression<bool> Function($$SavingsGoalsTableFilterComposer f) f,
  ) {
    final $$SavingsGoalsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.savingsGoals,
      getReferencedColumn: (t) => t.dreamId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SavingsGoalsTableFilterComposer(
            $db: $db,
            $table: $db.savingsGoals,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<bool> gardenTreesRefs(
    Expression<bool> Function($$GardenTreesTableFilterComposer f) f,
  ) {
    final $$GardenTreesTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.gardenTrees,
      getReferencedColumn: (t) => t.dreamId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$GardenTreesTableFilterComposer(
            $db: $db,
            $table: $db.gardenTrees,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<bool> focusSessionsRefs(
    Expression<bool> Function($$FocusSessionsTableFilterComposer f) f,
  ) {
    final $$FocusSessionsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.focusSessions,
      getReferencedColumn: (t) => t.dreamId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$FocusSessionsTableFilterComposer(
            $db: $db,
            $table: $db.focusSessions,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }
}

class $$DreamsTableOrderingComposer
    extends Composer<_$AppDatabase, $DreamsTable> {
  $$DreamsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get description => $composableBuilder(
    column: $table.description,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get coverPath => $composableBuilder(
    column: $table.coverPath,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get targetDate => $composableBuilder(
    column: $table.targetDate,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get priority => $composableBuilder(
    column: $table.priority,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get notes => $composableBuilder(
    column: $table.notes,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get motivation => $composableBuilder(
    column: $table.motivation,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get budgetMinor => $composableBuilder(
    column: $table.budgetMinor,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get currency => $composableBuilder(
    column: $table.currency,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get weightedProgress => $composableBuilder(
    column: $table.weightedProgress,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get isCompleted => $composableBuilder(
    column: $table.isCompleted,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get completedAt => $composableBuilder(
    column: $table.completedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get isHidden => $composableBuilder(
    column: $table.isHidden,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get archivedAt => $composableBuilder(
    column: $table.archivedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnOrderings(column),
  );

  $$DreamCategoriesTableOrderingComposer get categoryId {
    final $$DreamCategoriesTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.categoryId,
      referencedTable: $db.dreamCategories,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamCategoriesTableOrderingComposer(
            $db: $db,
            $table: $db.dreamCategories,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$DreamsTableAnnotationComposer
    extends Composer<_$AppDatabase, $DreamsTable> {
  $$DreamsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get title =>
      $composableBuilder(column: $table.title, builder: (column) => column);

  GeneratedColumn<String> get description => $composableBuilder(
    column: $table.description,
    builder: (column) => column,
  );

  GeneratedColumn<String> get coverPath =>
      $composableBuilder(column: $table.coverPath, builder: (column) => column);

  GeneratedColumn<DateTime> get targetDate => $composableBuilder(
    column: $table.targetDate,
    builder: (column) => column,
  );

  GeneratedColumn<int> get priority =>
      $composableBuilder(column: $table.priority, builder: (column) => column);

  GeneratedColumn<String> get notes =>
      $composableBuilder(column: $table.notes, builder: (column) => column);

  GeneratedColumn<String> get motivation => $composableBuilder(
    column: $table.motivation,
    builder: (column) => column,
  );

  GeneratedColumn<int> get budgetMinor => $composableBuilder(
    column: $table.budgetMinor,
    builder: (column) => column,
  );

  GeneratedColumn<String> get currency =>
      $composableBuilder(column: $table.currency, builder: (column) => column);

  GeneratedColumn<bool> get weightedProgress => $composableBuilder(
    column: $table.weightedProgress,
    builder: (column) => column,
  );

  GeneratedColumn<bool> get isCompleted => $composableBuilder(
    column: $table.isCompleted,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get completedAt => $composableBuilder(
    column: $table.completedAt,
    builder: (column) => column,
  );

  GeneratedColumn<bool> get isHidden =>
      $composableBuilder(column: $table.isHidden, builder: (column) => column);

  GeneratedColumn<DateTime> get archivedAt => $composableBuilder(
    column: $table.archivedAt,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);

  $$DreamCategoriesTableAnnotationComposer get categoryId {
    final $$DreamCategoriesTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.categoryId,
      referencedTable: $db.dreamCategories,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamCategoriesTableAnnotationComposer(
            $db: $db,
            $table: $db.dreamCategories,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  Expression<T> dreamMilestonesRefs<T extends Object>(
    Expression<T> Function($$DreamMilestonesTableAnnotationComposer a) f,
  ) {
    final $$DreamMilestonesTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.dreamMilestones,
      getReferencedColumn: (t) => t.dreamId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamMilestonesTableAnnotationComposer(
            $db: $db,
            $table: $db.dreamMilestones,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<T> dreamMediaRefs<T extends Object>(
    Expression<T> Function($$DreamMediaTableAnnotationComposer a) f,
  ) {
    final $$DreamMediaTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.dreamMedia,
      getReferencedColumn: (t) => t.dreamId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamMediaTableAnnotationComposer(
            $db: $db,
            $table: $db.dreamMedia,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<T> goalsRefs<T extends Object>(
    Expression<T> Function($$GoalsTableAnnotationComposer a) f,
  ) {
    final $$GoalsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.goals,
      getReferencedColumn: (t) => t.dreamId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$GoalsTableAnnotationComposer(
            $db: $db,
            $table: $db.goals,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<T> tasksRefs<T extends Object>(
    Expression<T> Function($$TasksTableAnnotationComposer a) f,
  ) {
    final $$TasksTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.tasks,
      getReferencedColumn: (t) => t.dreamId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$TasksTableAnnotationComposer(
            $db: $db,
            $table: $db.tasks,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<T> habitsRefs<T extends Object>(
    Expression<T> Function($$HabitsTableAnnotationComposer a) f,
  ) {
    final $$HabitsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.habits,
      getReferencedColumn: (t) => t.dreamId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$HabitsTableAnnotationComposer(
            $db: $db,
            $table: $db.habits,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<T> savingsGoalsRefs<T extends Object>(
    Expression<T> Function($$SavingsGoalsTableAnnotationComposer a) f,
  ) {
    final $$SavingsGoalsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.savingsGoals,
      getReferencedColumn: (t) => t.dreamId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SavingsGoalsTableAnnotationComposer(
            $db: $db,
            $table: $db.savingsGoals,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<T> gardenTreesRefs<T extends Object>(
    Expression<T> Function($$GardenTreesTableAnnotationComposer a) f,
  ) {
    final $$GardenTreesTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.gardenTrees,
      getReferencedColumn: (t) => t.dreamId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$GardenTreesTableAnnotationComposer(
            $db: $db,
            $table: $db.gardenTrees,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<T> focusSessionsRefs<T extends Object>(
    Expression<T> Function($$FocusSessionsTableAnnotationComposer a) f,
  ) {
    final $$FocusSessionsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.focusSessions,
      getReferencedColumn: (t) => t.dreamId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$FocusSessionsTableAnnotationComposer(
            $db: $db,
            $table: $db.focusSessions,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }
}

class $$DreamsTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $DreamsTable,
          Dream,
          $$DreamsTableFilterComposer,
          $$DreamsTableOrderingComposer,
          $$DreamsTableAnnotationComposer,
          $$DreamsTableCreateCompanionBuilder,
          $$DreamsTableUpdateCompanionBuilder,
          (Dream, $$DreamsTableReferences),
          Dream,
          PrefetchHooks Function({
            bool categoryId,
            bool dreamMilestonesRefs,
            bool dreamMediaRefs,
            bool goalsRefs,
            bool tasksRefs,
            bool habitsRefs,
            bool savingsGoalsRefs,
            bool gardenTreesRefs,
            bool focusSessionsRefs,
          })
        > {
  $$DreamsTableTableManager(_$AppDatabase db, $DreamsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$DreamsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$DreamsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$DreamsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> title = const Value.absent(),
                Value<String> description = const Value.absent(),
                Value<String?> coverPath = const Value.absent(),
                Value<int> categoryId = const Value.absent(),
                Value<DateTime?> targetDate = const Value.absent(),
                Value<int> priority = const Value.absent(),
                Value<String> notes = const Value.absent(),
                Value<String> motivation = const Value.absent(),
                Value<int?> budgetMinor = const Value.absent(),
                Value<String> currency = const Value.absent(),
                Value<bool> weightedProgress = const Value.absent(),
                Value<bool> isCompleted = const Value.absent(),
                Value<DateTime?> completedAt = const Value.absent(),
                Value<bool> isHidden = const Value.absent(),
                Value<DateTime?> archivedAt = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
              }) => DreamsCompanion(
                id: id,
                title: title,
                description: description,
                coverPath: coverPath,
                categoryId: categoryId,
                targetDate: targetDate,
                priority: priority,
                notes: notes,
                motivation: motivation,
                budgetMinor: budgetMinor,
                currency: currency,
                weightedProgress: weightedProgress,
                isCompleted: isCompleted,
                completedAt: completedAt,
                isHidden: isHidden,
                archivedAt: archivedAt,
                createdAt: createdAt,
                updatedAt: updatedAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required String title,
                Value<String> description = const Value.absent(),
                Value<String?> coverPath = const Value.absent(),
                required int categoryId,
                Value<DateTime?> targetDate = const Value.absent(),
                Value<int> priority = const Value.absent(),
                Value<String> notes = const Value.absent(),
                Value<String> motivation = const Value.absent(),
                Value<int?> budgetMinor = const Value.absent(),
                Value<String> currency = const Value.absent(),
                Value<bool> weightedProgress = const Value.absent(),
                Value<bool> isCompleted = const Value.absent(),
                Value<DateTime?> completedAt = const Value.absent(),
                Value<bool> isHidden = const Value.absent(),
                Value<DateTime?> archivedAt = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
              }) => DreamsCompanion.insert(
                id: id,
                title: title,
                description: description,
                coverPath: coverPath,
                categoryId: categoryId,
                targetDate: targetDate,
                priority: priority,
                notes: notes,
                motivation: motivation,
                budgetMinor: budgetMinor,
                currency: currency,
                weightedProgress: weightedProgress,
                isCompleted: isCompleted,
                completedAt: completedAt,
                isHidden: isHidden,
                archivedAt: archivedAt,
                createdAt: createdAt,
                updatedAt: updatedAt,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$DreamsTable, Dream>(table),
                  $$DreamsTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback:
              ({
                categoryId = false,
                dreamMilestonesRefs = false,
                dreamMediaRefs = false,
                goalsRefs = false,
                tasksRefs = false,
                habitsRefs = false,
                savingsGoalsRefs = false,
                gardenTreesRefs = false,
                focusSessionsRefs = false,
              }) {
                return PrefetchHooks(
                  db: db,
                  explicitlyWatchedTables: [
                    if (dreamMilestonesRefs) db.dreamMilestones,
                    if (dreamMediaRefs) db.dreamMedia,
                    if (goalsRefs) db.goals,
                    if (tasksRefs) db.tasks,
                    if (habitsRefs) db.habits,
                    if (savingsGoalsRefs) db.savingsGoals,
                    if (gardenTreesRefs) db.gardenTrees,
                    if (focusSessionsRefs) db.focusSessions,
                  ],
                  addJoins:
                      <
                        T extends TableManagerState<
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic
                        >
                      >(state) {
                        if (categoryId) {
                          state =
                              state.withJoin(
                                    currentTable: table,
                                    currentColumn: table.categoryId,
                                    referencedTable: $$DreamsTableReferences
                                        ._categoryIdTable(db),
                                    referencedColumn: $$DreamsTableReferences
                                        ._categoryIdTable(db)
                                        .id,
                                  )
                                  as T;
                        }

                        return state;
                      },
                  getPrefetchedDataCallback: (items) async {
                    return [
                      if (dreamMilestonesRefs)
                        await $_getPrefetchedDataByColumns<
                          Dream,
                          $DreamsTable,
                          DreamMilestone,
                          $DreamMilestonesTable
                        >(
                          currentTable: table,
                          referencedTable: $$DreamsTableReferences
                              ._dreamMilestonesRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$DreamsTableReferences(
                                db,
                                table,
                                p0,
                              ).dreamMilestonesRefs,
                          currentColumn: 'id',
                          referencedColumn: 'dream_id',
                          typedResults: items,
                        ),
                      if (dreamMediaRefs)
                        await $_getPrefetchedDataByColumns<
                          Dream,
                          $DreamsTable,
                          DreamMediaItem,
                          $DreamMediaTable
                        >(
                          currentTable: table,
                          referencedTable: $$DreamsTableReferences
                              ._dreamMediaRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$DreamsTableReferences(
                                db,
                                table,
                                p0,
                              ).dreamMediaRefs,
                          currentColumn: 'id',
                          referencedColumn: 'dream_id',
                          typedResults: items,
                        ),
                      if (goalsRefs)
                        await $_getPrefetchedDataByColumns<
                          Dream,
                          $DreamsTable,
                          Goal,
                          $GoalsTable
                        >(
                          currentTable: table,
                          referencedTable: $$DreamsTableReferences
                              ._goalsRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$DreamsTableReferences(db, table, p0).goalsRefs,
                          currentColumn: 'id',
                          referencedColumn: 'dream_id',
                          typedResults: items,
                        ),
                      if (tasksRefs)
                        await $_getPrefetchedDataByColumns<
                          Dream,
                          $DreamsTable,
                          TaskItem,
                          $TasksTable
                        >(
                          currentTable: table,
                          referencedTable: $$DreamsTableReferences
                              ._tasksRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$DreamsTableReferences(db, table, p0).tasksRefs,
                          currentColumn: 'id',
                          referencedColumn: 'dream_id',
                          typedResults: items,
                        ),
                      if (habitsRefs)
                        await $_getPrefetchedDataByColumns<
                          Dream,
                          $DreamsTable,
                          Habit,
                          $HabitsTable
                        >(
                          currentTable: table,
                          referencedTable: $$DreamsTableReferences
                              ._habitsRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$DreamsTableReferences(db, table, p0).habitsRefs,
                          currentColumn: 'id',
                          referencedColumn: 'dream_id',
                          typedResults: items,
                        ),
                      if (savingsGoalsRefs)
                        await $_getPrefetchedDataByColumns<
                          Dream,
                          $DreamsTable,
                          SavingsGoal,
                          $SavingsGoalsTable
                        >(
                          currentTable: table,
                          referencedTable: $$DreamsTableReferences
                              ._savingsGoalsRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$DreamsTableReferences(
                                db,
                                table,
                                p0,
                              ).savingsGoalsRefs,
                          currentColumn: 'id',
                          referencedColumn: 'dream_id',
                          typedResults: items,
                        ),
                      if (gardenTreesRefs)
                        await $_getPrefetchedDataByColumns<
                          Dream,
                          $DreamsTable,
                          GardenTree,
                          $GardenTreesTable
                        >(
                          currentTable: table,
                          referencedTable: $$DreamsTableReferences
                              ._gardenTreesRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$DreamsTableReferences(
                                db,
                                table,
                                p0,
                              ).gardenTreesRefs,
                          currentColumn: 'id',
                          referencedColumn: 'dream_id',
                          typedResults: items,
                        ),
                      if (focusSessionsRefs)
                        await $_getPrefetchedDataByColumns<
                          Dream,
                          $DreamsTable,
                          FocusSession,
                          $FocusSessionsTable
                        >(
                          currentTable: table,
                          referencedTable: $$DreamsTableReferences
                              ._focusSessionsRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$DreamsTableReferences(
                                db,
                                table,
                                p0,
                              ).focusSessionsRefs,
                          currentColumn: 'id',
                          referencedColumn: 'dream_id',
                          typedResults: items,
                        ),
                    ];
                  },
                );
              },
        ),
      );
}

typedef $$DreamsTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $DreamsTable,
      Dream,
      $$DreamsTableFilterComposer,
      $$DreamsTableOrderingComposer,
      $$DreamsTableAnnotationComposer,
      $$DreamsTableCreateCompanionBuilder,
      $$DreamsTableUpdateCompanionBuilder,
      (Dream, $$DreamsTableReferences),
      Dream,
      PrefetchHooks Function({
        bool categoryId,
        bool dreamMilestonesRefs,
        bool dreamMediaRefs,
        bool goalsRefs,
        bool tasksRefs,
        bool habitsRefs,
        bool savingsGoalsRefs,
        bool gardenTreesRefs,
        bool focusSessionsRefs,
      })
    >;
typedef $$DreamMilestonesTableCreateCompanionBuilder =
    DreamMilestonesCompanion Function({
      Value<int> id,
      required int dreamId,
      required String title,
      Value<String> description,
      Value<DateTime?> targetDate,
      Value<bool> isCompleted,
      Value<DateTime?> completedAt,
      Value<int> weight,
      Value<String?> photoPath,
      Value<String> note,
      Value<DateTime?> reminderAt,
      Value<int> sortOrder,
    });
typedef $$DreamMilestonesTableUpdateCompanionBuilder =
    DreamMilestonesCompanion Function({
      Value<int> id,
      Value<int> dreamId,
      Value<String> title,
      Value<String> description,
      Value<DateTime?> targetDate,
      Value<bool> isCompleted,
      Value<DateTime?> completedAt,
      Value<int> weight,
      Value<String?> photoPath,
      Value<String> note,
      Value<DateTime?> reminderAt,
      Value<int> sortOrder,
    });

final class $$DreamMilestonesTableReferences
    extends
        BaseReferences<_$AppDatabase, $DreamMilestonesTable, DreamMilestone> {
  $$DreamMilestonesTableReferences(
    super.$_db,
    super.$_table,
    super.$_typedResult,
  );

  static $DreamsTable _dreamIdTable(_$AppDatabase db) =>
      db.dreams.createAlias('dream_milestones__dream_id__dreams__id');

  $$DreamsTableProcessedTableManager get dreamId {
    final $_column = $_itemColumn<int>('dream_id')!;

    final manager = $$DreamsTableTableManager(
      $_db,
      $_db.dreams,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_dreamIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }
}

class $$DreamMilestonesTableFilterComposer
    extends Composer<_$AppDatabase, $DreamMilestonesTable> {
  $$DreamMilestonesTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get description => $composableBuilder(
    column: $table.description,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get targetDate => $composableBuilder(
    column: $table.targetDate,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get isCompleted => $composableBuilder(
    column: $table.isCompleted,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get completedAt => $composableBuilder(
    column: $table.completedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get weight => $composableBuilder(
    column: $table.weight,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get photoPath => $composableBuilder(
    column: $table.photoPath,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get note => $composableBuilder(
    column: $table.note,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get reminderAt => $composableBuilder(
    column: $table.reminderAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get sortOrder => $composableBuilder(
    column: $table.sortOrder,
    builder: (column) => ColumnFilters(column),
  );

  $$DreamsTableFilterComposer get dreamId {
    final $$DreamsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.dreamId,
      referencedTable: $db.dreams,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamsTableFilterComposer(
            $db: $db,
            $table: $db.dreams,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$DreamMilestonesTableOrderingComposer
    extends Composer<_$AppDatabase, $DreamMilestonesTable> {
  $$DreamMilestonesTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get description => $composableBuilder(
    column: $table.description,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get targetDate => $composableBuilder(
    column: $table.targetDate,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get isCompleted => $composableBuilder(
    column: $table.isCompleted,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get completedAt => $composableBuilder(
    column: $table.completedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get weight => $composableBuilder(
    column: $table.weight,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get photoPath => $composableBuilder(
    column: $table.photoPath,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get note => $composableBuilder(
    column: $table.note,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get reminderAt => $composableBuilder(
    column: $table.reminderAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get sortOrder => $composableBuilder(
    column: $table.sortOrder,
    builder: (column) => ColumnOrderings(column),
  );

  $$DreamsTableOrderingComposer get dreamId {
    final $$DreamsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.dreamId,
      referencedTable: $db.dreams,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamsTableOrderingComposer(
            $db: $db,
            $table: $db.dreams,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$DreamMilestonesTableAnnotationComposer
    extends Composer<_$AppDatabase, $DreamMilestonesTable> {
  $$DreamMilestonesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get title =>
      $composableBuilder(column: $table.title, builder: (column) => column);

  GeneratedColumn<String> get description => $composableBuilder(
    column: $table.description,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get targetDate => $composableBuilder(
    column: $table.targetDate,
    builder: (column) => column,
  );

  GeneratedColumn<bool> get isCompleted => $composableBuilder(
    column: $table.isCompleted,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get completedAt => $composableBuilder(
    column: $table.completedAt,
    builder: (column) => column,
  );

  GeneratedColumn<int> get weight =>
      $composableBuilder(column: $table.weight, builder: (column) => column);

  GeneratedColumn<String> get photoPath =>
      $composableBuilder(column: $table.photoPath, builder: (column) => column);

  GeneratedColumn<String> get note =>
      $composableBuilder(column: $table.note, builder: (column) => column);

  GeneratedColumn<DateTime> get reminderAt => $composableBuilder(
    column: $table.reminderAt,
    builder: (column) => column,
  );

  GeneratedColumn<int> get sortOrder =>
      $composableBuilder(column: $table.sortOrder, builder: (column) => column);

  $$DreamsTableAnnotationComposer get dreamId {
    final $$DreamsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.dreamId,
      referencedTable: $db.dreams,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamsTableAnnotationComposer(
            $db: $db,
            $table: $db.dreams,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$DreamMilestonesTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $DreamMilestonesTable,
          DreamMilestone,
          $$DreamMilestonesTableFilterComposer,
          $$DreamMilestonesTableOrderingComposer,
          $$DreamMilestonesTableAnnotationComposer,
          $$DreamMilestonesTableCreateCompanionBuilder,
          $$DreamMilestonesTableUpdateCompanionBuilder,
          (DreamMilestone, $$DreamMilestonesTableReferences),
          DreamMilestone,
          PrefetchHooks Function({bool dreamId})
        > {
  $$DreamMilestonesTableTableManager(
    _$AppDatabase db,
    $DreamMilestonesTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$DreamMilestonesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$DreamMilestonesTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$DreamMilestonesTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<int> dreamId = const Value.absent(),
                Value<String> title = const Value.absent(),
                Value<String> description = const Value.absent(),
                Value<DateTime?> targetDate = const Value.absent(),
                Value<bool> isCompleted = const Value.absent(),
                Value<DateTime?> completedAt = const Value.absent(),
                Value<int> weight = const Value.absent(),
                Value<String?> photoPath = const Value.absent(),
                Value<String> note = const Value.absent(),
                Value<DateTime?> reminderAt = const Value.absent(),
                Value<int> sortOrder = const Value.absent(),
              }) => DreamMilestonesCompanion(
                id: id,
                dreamId: dreamId,
                title: title,
                description: description,
                targetDate: targetDate,
                isCompleted: isCompleted,
                completedAt: completedAt,
                weight: weight,
                photoPath: photoPath,
                note: note,
                reminderAt: reminderAt,
                sortOrder: sortOrder,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required int dreamId,
                required String title,
                Value<String> description = const Value.absent(),
                Value<DateTime?> targetDate = const Value.absent(),
                Value<bool> isCompleted = const Value.absent(),
                Value<DateTime?> completedAt = const Value.absent(),
                Value<int> weight = const Value.absent(),
                Value<String?> photoPath = const Value.absent(),
                Value<String> note = const Value.absent(),
                Value<DateTime?> reminderAt = const Value.absent(),
                Value<int> sortOrder = const Value.absent(),
              }) => DreamMilestonesCompanion.insert(
                id: id,
                dreamId: dreamId,
                title: title,
                description: description,
                targetDate: targetDate,
                isCompleted: isCompleted,
                completedAt: completedAt,
                weight: weight,
                photoPath: photoPath,
                note: note,
                reminderAt: reminderAt,
                sortOrder: sortOrder,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$DreamMilestonesTable, DreamMilestone>(table),
                  $$DreamMilestonesTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({dreamId = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [],
              addJoins:
                  <
                    T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic
                    >
                  >(state) {
                    if (dreamId) {
                      state =
                          state.withJoin(
                                currentTable: table,
                                currentColumn: table.dreamId,
                                referencedTable:
                                    $$DreamMilestonesTableReferences
                                        ._dreamIdTable(db),
                                referencedColumn:
                                    $$DreamMilestonesTableReferences
                                        ._dreamIdTable(db)
                                        .id,
                              )
                              as T;
                    }

                    return state;
                  },
              getPrefetchedDataCallback: (items) async {
                return [];
              },
            );
          },
        ),
      );
}

typedef $$DreamMilestonesTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $DreamMilestonesTable,
      DreamMilestone,
      $$DreamMilestonesTableFilterComposer,
      $$DreamMilestonesTableOrderingComposer,
      $$DreamMilestonesTableAnnotationComposer,
      $$DreamMilestonesTableCreateCompanionBuilder,
      $$DreamMilestonesTableUpdateCompanionBuilder,
      (DreamMilestone, $$DreamMilestonesTableReferences),
      DreamMilestone,
      PrefetchHooks Function({bool dreamId})
    >;
typedef $$DreamMediaTableCreateCompanionBuilder =
    DreamMediaCompanion Function({
      Value<int> id,
      Value<int?> dreamId,
      Value<String> kind,
      Value<String?> path,
      Value<String> caption,
      Value<String> boardCategory,
      Value<bool> showOnBoard,
      Value<int?> color,
      Value<int> sortOrder,
      Value<DateTime> createdAt,
    });
typedef $$DreamMediaTableUpdateCompanionBuilder =
    DreamMediaCompanion Function({
      Value<int> id,
      Value<int?> dreamId,
      Value<String> kind,
      Value<String?> path,
      Value<String> caption,
      Value<String> boardCategory,
      Value<bool> showOnBoard,
      Value<int?> color,
      Value<int> sortOrder,
      Value<DateTime> createdAt,
    });

final class $$DreamMediaTableReferences
    extends BaseReferences<_$AppDatabase, $DreamMediaTable, DreamMediaItem> {
  $$DreamMediaTableReferences(super.$_db, super.$_table, super.$_typedResult);

  static $DreamsTable _dreamIdTable(_$AppDatabase db) =>
      db.dreams.createAlias('dream_media__dream_id__dreams__id');

  $$DreamsTableProcessedTableManager? get dreamId {
    final $_column = $_itemColumn<int>('dream_id');
    if ($_column == null) return null;
    final manager = $$DreamsTableTableManager(
      $_db,
      $_db.dreams,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_dreamIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }
}

class $$DreamMediaTableFilterComposer
    extends Composer<_$AppDatabase, $DreamMediaTable> {
  $$DreamMediaTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get path => $composableBuilder(
    column: $table.path,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get caption => $composableBuilder(
    column: $table.caption,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get boardCategory => $composableBuilder(
    column: $table.boardCategory,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get showOnBoard => $composableBuilder(
    column: $table.showOnBoard,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get color => $composableBuilder(
    column: $table.color,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get sortOrder => $composableBuilder(
    column: $table.sortOrder,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  $$DreamsTableFilterComposer get dreamId {
    final $$DreamsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.dreamId,
      referencedTable: $db.dreams,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamsTableFilterComposer(
            $db: $db,
            $table: $db.dreams,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$DreamMediaTableOrderingComposer
    extends Composer<_$AppDatabase, $DreamMediaTable> {
  $$DreamMediaTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get path => $composableBuilder(
    column: $table.path,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get caption => $composableBuilder(
    column: $table.caption,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get boardCategory => $composableBuilder(
    column: $table.boardCategory,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get showOnBoard => $composableBuilder(
    column: $table.showOnBoard,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get color => $composableBuilder(
    column: $table.color,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get sortOrder => $composableBuilder(
    column: $table.sortOrder,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  $$DreamsTableOrderingComposer get dreamId {
    final $$DreamsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.dreamId,
      referencedTable: $db.dreams,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamsTableOrderingComposer(
            $db: $db,
            $table: $db.dreams,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$DreamMediaTableAnnotationComposer
    extends Composer<_$AppDatabase, $DreamMediaTable> {
  $$DreamMediaTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get kind =>
      $composableBuilder(column: $table.kind, builder: (column) => column);

  GeneratedColumn<String> get path =>
      $composableBuilder(column: $table.path, builder: (column) => column);

  GeneratedColumn<String> get caption =>
      $composableBuilder(column: $table.caption, builder: (column) => column);

  GeneratedColumn<String> get boardCategory => $composableBuilder(
    column: $table.boardCategory,
    builder: (column) => column,
  );

  GeneratedColumn<bool> get showOnBoard => $composableBuilder(
    column: $table.showOnBoard,
    builder: (column) => column,
  );

  GeneratedColumn<int> get color =>
      $composableBuilder(column: $table.color, builder: (column) => column);

  GeneratedColumn<int> get sortOrder =>
      $composableBuilder(column: $table.sortOrder, builder: (column) => column);

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  $$DreamsTableAnnotationComposer get dreamId {
    final $$DreamsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.dreamId,
      referencedTable: $db.dreams,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamsTableAnnotationComposer(
            $db: $db,
            $table: $db.dreams,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$DreamMediaTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $DreamMediaTable,
          DreamMediaItem,
          $$DreamMediaTableFilterComposer,
          $$DreamMediaTableOrderingComposer,
          $$DreamMediaTableAnnotationComposer,
          $$DreamMediaTableCreateCompanionBuilder,
          $$DreamMediaTableUpdateCompanionBuilder,
          (DreamMediaItem, $$DreamMediaTableReferences),
          DreamMediaItem,
          PrefetchHooks Function({bool dreamId})
        > {
  $$DreamMediaTableTableManager(_$AppDatabase db, $DreamMediaTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$DreamMediaTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$DreamMediaTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$DreamMediaTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<int?> dreamId = const Value.absent(),
                Value<String> kind = const Value.absent(),
                Value<String?> path = const Value.absent(),
                Value<String> caption = const Value.absent(),
                Value<String> boardCategory = const Value.absent(),
                Value<bool> showOnBoard = const Value.absent(),
                Value<int?> color = const Value.absent(),
                Value<int> sortOrder = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => DreamMediaCompanion(
                id: id,
                dreamId: dreamId,
                kind: kind,
                path: path,
                caption: caption,
                boardCategory: boardCategory,
                showOnBoard: showOnBoard,
                color: color,
                sortOrder: sortOrder,
                createdAt: createdAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<int?> dreamId = const Value.absent(),
                Value<String> kind = const Value.absent(),
                Value<String?> path = const Value.absent(),
                Value<String> caption = const Value.absent(),
                Value<String> boardCategory = const Value.absent(),
                Value<bool> showOnBoard = const Value.absent(),
                Value<int?> color = const Value.absent(),
                Value<int> sortOrder = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => DreamMediaCompanion.insert(
                id: id,
                dreamId: dreamId,
                kind: kind,
                path: path,
                caption: caption,
                boardCategory: boardCategory,
                showOnBoard: showOnBoard,
                color: color,
                sortOrder: sortOrder,
                createdAt: createdAt,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$DreamMediaTable, DreamMediaItem>(table),
                  $$DreamMediaTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({dreamId = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [],
              addJoins:
                  <
                    T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic
                    >
                  >(state) {
                    if (dreamId) {
                      state =
                          state.withJoin(
                                currentTable: table,
                                currentColumn: table.dreamId,
                                referencedTable: $$DreamMediaTableReferences
                                    ._dreamIdTable(db),
                                referencedColumn: $$DreamMediaTableReferences
                                    ._dreamIdTable(db)
                                    .id,
                              )
                              as T;
                    }

                    return state;
                  },
              getPrefetchedDataCallback: (items) async {
                return [];
              },
            );
          },
        ),
      );
}

typedef $$DreamMediaTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $DreamMediaTable,
      DreamMediaItem,
      $$DreamMediaTableFilterComposer,
      $$DreamMediaTableOrderingComposer,
      $$DreamMediaTableAnnotationComposer,
      $$DreamMediaTableCreateCompanionBuilder,
      $$DreamMediaTableUpdateCompanionBuilder,
      (DreamMediaItem, $$DreamMediaTableReferences),
      DreamMediaItem,
      PrefetchHooks Function({bool dreamId})
    >;
typedef $$GoalsTableCreateCompanionBuilder =
    GoalsCompanion Function({
      Value<int> id,
      required String title,
      Value<String> description,
      Value<String> period,
      Value<int?> dreamId,
      Value<int> priority,
      Value<DateTime> startDate,
      Value<DateTime?> dueDate,
      Value<bool> isCompleted,
      Value<DateTime?> completedAt,
      Value<DateTime?> archivedAt,
      Value<DateTime> createdAt,
    });
typedef $$GoalsTableUpdateCompanionBuilder =
    GoalsCompanion Function({
      Value<int> id,
      Value<String> title,
      Value<String> description,
      Value<String> period,
      Value<int?> dreamId,
      Value<int> priority,
      Value<DateTime> startDate,
      Value<DateTime?> dueDate,
      Value<bool> isCompleted,
      Value<DateTime?> completedAt,
      Value<DateTime?> archivedAt,
      Value<DateTime> createdAt,
    });

final class $$GoalsTableReferences
    extends BaseReferences<_$AppDatabase, $GoalsTable, Goal> {
  $$GoalsTableReferences(super.$_db, super.$_table, super.$_typedResult);

  static $DreamsTable _dreamIdTable(_$AppDatabase db) =>
      db.dreams.createAlias('goals__dream_id__dreams__id');

  $$DreamsTableProcessedTableManager? get dreamId {
    final $_column = $_itemColumn<int>('dream_id');
    if ($_column == null) return null;
    final manager = $$DreamsTableTableManager(
      $_db,
      $_db.dreams,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_dreamIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }

  static MultiTypedResultKey<$TasksTable, List<TaskItem>> _tasksRefsTable(
    _$AppDatabase db,
  ) => MultiTypedResultKey.fromTable(
    db.tasks,
    aliasName: 'goals__id__tasks__goal_id',
  );

  $$TasksTableProcessedTableManager get tasksRefs {
    final manager = $$TasksTableTableManager(
      $_db,
      $_db.tasks,
    ).filter((f) => f.goalId.id.sqlEquals($_itemColumn<int>('id')!));

    final cache = $_typedResult.readTableOrNull(_tasksRefsTable($_db));
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }

  static MultiTypedResultKey<$FocusSessionsTable, List<FocusSession>>
  _focusSessionsRefsTable(_$AppDatabase db) => MultiTypedResultKey.fromTable(
    db.focusSessions,
    aliasName: 'goals__id__focus_sessions__goal_id',
  );

  $$FocusSessionsTableProcessedTableManager get focusSessionsRefs {
    final manager = $$FocusSessionsTableTableManager(
      $_db,
      $_db.focusSessions,
    ).filter((f) => f.goalId.id.sqlEquals($_itemColumn<int>('id')!));

    final cache = $_typedResult.readTableOrNull(_focusSessionsRefsTable($_db));
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }
}

class $$GoalsTableFilterComposer extends Composer<_$AppDatabase, $GoalsTable> {
  $$GoalsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get description => $composableBuilder(
    column: $table.description,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get period => $composableBuilder(
    column: $table.period,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get priority => $composableBuilder(
    column: $table.priority,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get startDate => $composableBuilder(
    column: $table.startDate,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get dueDate => $composableBuilder(
    column: $table.dueDate,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get isCompleted => $composableBuilder(
    column: $table.isCompleted,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get completedAt => $composableBuilder(
    column: $table.completedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get archivedAt => $composableBuilder(
    column: $table.archivedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  $$DreamsTableFilterComposer get dreamId {
    final $$DreamsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.dreamId,
      referencedTable: $db.dreams,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamsTableFilterComposer(
            $db: $db,
            $table: $db.dreams,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  Expression<bool> tasksRefs(
    Expression<bool> Function($$TasksTableFilterComposer f) f,
  ) {
    final $$TasksTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.tasks,
      getReferencedColumn: (t) => t.goalId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$TasksTableFilterComposer(
            $db: $db,
            $table: $db.tasks,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<bool> focusSessionsRefs(
    Expression<bool> Function($$FocusSessionsTableFilterComposer f) f,
  ) {
    final $$FocusSessionsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.focusSessions,
      getReferencedColumn: (t) => t.goalId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$FocusSessionsTableFilterComposer(
            $db: $db,
            $table: $db.focusSessions,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }
}

class $$GoalsTableOrderingComposer
    extends Composer<_$AppDatabase, $GoalsTable> {
  $$GoalsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get description => $composableBuilder(
    column: $table.description,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get period => $composableBuilder(
    column: $table.period,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get priority => $composableBuilder(
    column: $table.priority,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get startDate => $composableBuilder(
    column: $table.startDate,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get dueDate => $composableBuilder(
    column: $table.dueDate,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get isCompleted => $composableBuilder(
    column: $table.isCompleted,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get completedAt => $composableBuilder(
    column: $table.completedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get archivedAt => $composableBuilder(
    column: $table.archivedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  $$DreamsTableOrderingComposer get dreamId {
    final $$DreamsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.dreamId,
      referencedTable: $db.dreams,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamsTableOrderingComposer(
            $db: $db,
            $table: $db.dreams,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$GoalsTableAnnotationComposer
    extends Composer<_$AppDatabase, $GoalsTable> {
  $$GoalsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get title =>
      $composableBuilder(column: $table.title, builder: (column) => column);

  GeneratedColumn<String> get description => $composableBuilder(
    column: $table.description,
    builder: (column) => column,
  );

  GeneratedColumn<String> get period =>
      $composableBuilder(column: $table.period, builder: (column) => column);

  GeneratedColumn<int> get priority =>
      $composableBuilder(column: $table.priority, builder: (column) => column);

  GeneratedColumn<DateTime> get startDate =>
      $composableBuilder(column: $table.startDate, builder: (column) => column);

  GeneratedColumn<DateTime> get dueDate =>
      $composableBuilder(column: $table.dueDate, builder: (column) => column);

  GeneratedColumn<bool> get isCompleted => $composableBuilder(
    column: $table.isCompleted,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get completedAt => $composableBuilder(
    column: $table.completedAt,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get archivedAt => $composableBuilder(
    column: $table.archivedAt,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  $$DreamsTableAnnotationComposer get dreamId {
    final $$DreamsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.dreamId,
      referencedTable: $db.dreams,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamsTableAnnotationComposer(
            $db: $db,
            $table: $db.dreams,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  Expression<T> tasksRefs<T extends Object>(
    Expression<T> Function($$TasksTableAnnotationComposer a) f,
  ) {
    final $$TasksTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.tasks,
      getReferencedColumn: (t) => t.goalId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$TasksTableAnnotationComposer(
            $db: $db,
            $table: $db.tasks,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<T> focusSessionsRefs<T extends Object>(
    Expression<T> Function($$FocusSessionsTableAnnotationComposer a) f,
  ) {
    final $$FocusSessionsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.focusSessions,
      getReferencedColumn: (t) => t.goalId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$FocusSessionsTableAnnotationComposer(
            $db: $db,
            $table: $db.focusSessions,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }
}

class $$GoalsTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $GoalsTable,
          Goal,
          $$GoalsTableFilterComposer,
          $$GoalsTableOrderingComposer,
          $$GoalsTableAnnotationComposer,
          $$GoalsTableCreateCompanionBuilder,
          $$GoalsTableUpdateCompanionBuilder,
          (Goal, $$GoalsTableReferences),
          Goal,
          PrefetchHooks Function({
            bool dreamId,
            bool tasksRefs,
            bool focusSessionsRefs,
          })
        > {
  $$GoalsTableTableManager(_$AppDatabase db, $GoalsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$GoalsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$GoalsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$GoalsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> title = const Value.absent(),
                Value<String> description = const Value.absent(),
                Value<String> period = const Value.absent(),
                Value<int?> dreamId = const Value.absent(),
                Value<int> priority = const Value.absent(),
                Value<DateTime> startDate = const Value.absent(),
                Value<DateTime?> dueDate = const Value.absent(),
                Value<bool> isCompleted = const Value.absent(),
                Value<DateTime?> completedAt = const Value.absent(),
                Value<DateTime?> archivedAt = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => GoalsCompanion(
                id: id,
                title: title,
                description: description,
                period: period,
                dreamId: dreamId,
                priority: priority,
                startDate: startDate,
                dueDate: dueDate,
                isCompleted: isCompleted,
                completedAt: completedAt,
                archivedAt: archivedAt,
                createdAt: createdAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required String title,
                Value<String> description = const Value.absent(),
                Value<String> period = const Value.absent(),
                Value<int?> dreamId = const Value.absent(),
                Value<int> priority = const Value.absent(),
                Value<DateTime> startDate = const Value.absent(),
                Value<DateTime?> dueDate = const Value.absent(),
                Value<bool> isCompleted = const Value.absent(),
                Value<DateTime?> completedAt = const Value.absent(),
                Value<DateTime?> archivedAt = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => GoalsCompanion.insert(
                id: id,
                title: title,
                description: description,
                period: period,
                dreamId: dreamId,
                priority: priority,
                startDate: startDate,
                dueDate: dueDate,
                isCompleted: isCompleted,
                completedAt: completedAt,
                archivedAt: archivedAt,
                createdAt: createdAt,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$GoalsTable, Goal>(table),
                  $$GoalsTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback:
              ({
                dreamId = false,
                tasksRefs = false,
                focusSessionsRefs = false,
              }) {
                return PrefetchHooks(
                  db: db,
                  explicitlyWatchedTables: [
                    if (tasksRefs) db.tasks,
                    if (focusSessionsRefs) db.focusSessions,
                  ],
                  addJoins:
                      <
                        T extends TableManagerState<
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic
                        >
                      >(state) {
                        if (dreamId) {
                          state =
                              state.withJoin(
                                    currentTable: table,
                                    currentColumn: table.dreamId,
                                    referencedTable: $$GoalsTableReferences
                                        ._dreamIdTable(db),
                                    referencedColumn: $$GoalsTableReferences
                                        ._dreamIdTable(db)
                                        .id,
                                  )
                                  as T;
                        }

                        return state;
                      },
                  getPrefetchedDataCallback: (items) async {
                    return [
                      if (tasksRefs)
                        await $_getPrefetchedDataByColumns<
                          Goal,
                          $GoalsTable,
                          TaskItem,
                          $TasksTable
                        >(
                          currentTable: table,
                          referencedTable: $$GoalsTableReferences
                              ._tasksRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$GoalsTableReferences(db, table, p0).tasksRefs,
                          currentColumn: 'id',
                          referencedColumn: 'goal_id',
                          typedResults: items,
                        ),
                      if (focusSessionsRefs)
                        await $_getPrefetchedDataByColumns<
                          Goal,
                          $GoalsTable,
                          FocusSession,
                          $FocusSessionsTable
                        >(
                          currentTable: table,
                          referencedTable: $$GoalsTableReferences
                              ._focusSessionsRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$GoalsTableReferences(
                                db,
                                table,
                                p0,
                              ).focusSessionsRefs,
                          currentColumn: 'id',
                          referencedColumn: 'goal_id',
                          typedResults: items,
                        ),
                    ];
                  },
                );
              },
        ),
      );
}

typedef $$GoalsTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $GoalsTable,
      Goal,
      $$GoalsTableFilterComposer,
      $$GoalsTableOrderingComposer,
      $$GoalsTableAnnotationComposer,
      $$GoalsTableCreateCompanionBuilder,
      $$GoalsTableUpdateCompanionBuilder,
      (Goal, $$GoalsTableReferences),
      Goal,
      PrefetchHooks Function({
        bool dreamId,
        bool tasksRefs,
        bool focusSessionsRefs,
      })
    >;
typedef $$TasksTableCreateCompanionBuilder =
    TasksCompanion Function({
      Value<int> id,
      Value<int?> goalId,
      Value<int?> dreamId,
      required String title,
      Value<String> note,
      Value<DateTime?> dueDate,
      Value<int?> dueMinutes,
      Value<int> priority,
      Value<String> repeatRule,
      Value<bool> isCompleted,
      Value<DateTime?> completedAt,
      Value<bool> reminder,
      Value<DateTime> createdAt,
    });
typedef $$TasksTableUpdateCompanionBuilder =
    TasksCompanion Function({
      Value<int> id,
      Value<int?> goalId,
      Value<int?> dreamId,
      Value<String> title,
      Value<String> note,
      Value<DateTime?> dueDate,
      Value<int?> dueMinutes,
      Value<int> priority,
      Value<String> repeatRule,
      Value<bool> isCompleted,
      Value<DateTime?> completedAt,
      Value<bool> reminder,
      Value<DateTime> createdAt,
    });

final class $$TasksTableReferences
    extends BaseReferences<_$AppDatabase, $TasksTable, TaskItem> {
  $$TasksTableReferences(super.$_db, super.$_table, super.$_typedResult);

  static $GoalsTable _goalIdTable(_$AppDatabase db) =>
      db.goals.createAlias('tasks__goal_id__goals__id');

  $$GoalsTableProcessedTableManager? get goalId {
    final $_column = $_itemColumn<int>('goal_id');
    if ($_column == null) return null;
    final manager = $$GoalsTableTableManager(
      $_db,
      $_db.goals,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_goalIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }

  static $DreamsTable _dreamIdTable(_$AppDatabase db) =>
      db.dreams.createAlias('tasks__dream_id__dreams__id');

  $$DreamsTableProcessedTableManager? get dreamId {
    final $_column = $_itemColumn<int>('dream_id');
    if ($_column == null) return null;
    final manager = $$DreamsTableTableManager(
      $_db,
      $_db.dreams,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_dreamIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }
}

class $$TasksTableFilterComposer extends Composer<_$AppDatabase, $TasksTable> {
  $$TasksTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get note => $composableBuilder(
    column: $table.note,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get dueDate => $composableBuilder(
    column: $table.dueDate,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get dueMinutes => $composableBuilder(
    column: $table.dueMinutes,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get priority => $composableBuilder(
    column: $table.priority,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get repeatRule => $composableBuilder(
    column: $table.repeatRule,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get isCompleted => $composableBuilder(
    column: $table.isCompleted,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get completedAt => $composableBuilder(
    column: $table.completedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get reminder => $composableBuilder(
    column: $table.reminder,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  $$GoalsTableFilterComposer get goalId {
    final $$GoalsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.goalId,
      referencedTable: $db.goals,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$GoalsTableFilterComposer(
            $db: $db,
            $table: $db.goals,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$DreamsTableFilterComposer get dreamId {
    final $$DreamsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.dreamId,
      referencedTable: $db.dreams,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamsTableFilterComposer(
            $db: $db,
            $table: $db.dreams,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$TasksTableOrderingComposer
    extends Composer<_$AppDatabase, $TasksTable> {
  $$TasksTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get note => $composableBuilder(
    column: $table.note,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get dueDate => $composableBuilder(
    column: $table.dueDate,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get dueMinutes => $composableBuilder(
    column: $table.dueMinutes,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get priority => $composableBuilder(
    column: $table.priority,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get repeatRule => $composableBuilder(
    column: $table.repeatRule,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get isCompleted => $composableBuilder(
    column: $table.isCompleted,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get completedAt => $composableBuilder(
    column: $table.completedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get reminder => $composableBuilder(
    column: $table.reminder,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  $$GoalsTableOrderingComposer get goalId {
    final $$GoalsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.goalId,
      referencedTable: $db.goals,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$GoalsTableOrderingComposer(
            $db: $db,
            $table: $db.goals,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$DreamsTableOrderingComposer get dreamId {
    final $$DreamsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.dreamId,
      referencedTable: $db.dreams,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamsTableOrderingComposer(
            $db: $db,
            $table: $db.dreams,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$TasksTableAnnotationComposer
    extends Composer<_$AppDatabase, $TasksTable> {
  $$TasksTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get title =>
      $composableBuilder(column: $table.title, builder: (column) => column);

  GeneratedColumn<String> get note =>
      $composableBuilder(column: $table.note, builder: (column) => column);

  GeneratedColumn<DateTime> get dueDate =>
      $composableBuilder(column: $table.dueDate, builder: (column) => column);

  GeneratedColumn<int> get dueMinutes => $composableBuilder(
    column: $table.dueMinutes,
    builder: (column) => column,
  );

  GeneratedColumn<int> get priority =>
      $composableBuilder(column: $table.priority, builder: (column) => column);

  GeneratedColumn<String> get repeatRule => $composableBuilder(
    column: $table.repeatRule,
    builder: (column) => column,
  );

  GeneratedColumn<bool> get isCompleted => $composableBuilder(
    column: $table.isCompleted,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get completedAt => $composableBuilder(
    column: $table.completedAt,
    builder: (column) => column,
  );

  GeneratedColumn<bool> get reminder =>
      $composableBuilder(column: $table.reminder, builder: (column) => column);

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  $$GoalsTableAnnotationComposer get goalId {
    final $$GoalsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.goalId,
      referencedTable: $db.goals,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$GoalsTableAnnotationComposer(
            $db: $db,
            $table: $db.goals,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$DreamsTableAnnotationComposer get dreamId {
    final $$DreamsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.dreamId,
      referencedTable: $db.dreams,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamsTableAnnotationComposer(
            $db: $db,
            $table: $db.dreams,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$TasksTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $TasksTable,
          TaskItem,
          $$TasksTableFilterComposer,
          $$TasksTableOrderingComposer,
          $$TasksTableAnnotationComposer,
          $$TasksTableCreateCompanionBuilder,
          $$TasksTableUpdateCompanionBuilder,
          (TaskItem, $$TasksTableReferences),
          TaskItem,
          PrefetchHooks Function({bool goalId, bool dreamId})
        > {
  $$TasksTableTableManager(_$AppDatabase db, $TasksTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$TasksTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$TasksTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$TasksTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<int?> goalId = const Value.absent(),
                Value<int?> dreamId = const Value.absent(),
                Value<String> title = const Value.absent(),
                Value<String> note = const Value.absent(),
                Value<DateTime?> dueDate = const Value.absent(),
                Value<int?> dueMinutes = const Value.absent(),
                Value<int> priority = const Value.absent(),
                Value<String> repeatRule = const Value.absent(),
                Value<bool> isCompleted = const Value.absent(),
                Value<DateTime?> completedAt = const Value.absent(),
                Value<bool> reminder = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => TasksCompanion(
                id: id,
                goalId: goalId,
                dreamId: dreamId,
                title: title,
                note: note,
                dueDate: dueDate,
                dueMinutes: dueMinutes,
                priority: priority,
                repeatRule: repeatRule,
                isCompleted: isCompleted,
                completedAt: completedAt,
                reminder: reminder,
                createdAt: createdAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<int?> goalId = const Value.absent(),
                Value<int?> dreamId = const Value.absent(),
                required String title,
                Value<String> note = const Value.absent(),
                Value<DateTime?> dueDate = const Value.absent(),
                Value<int?> dueMinutes = const Value.absent(),
                Value<int> priority = const Value.absent(),
                Value<String> repeatRule = const Value.absent(),
                Value<bool> isCompleted = const Value.absent(),
                Value<DateTime?> completedAt = const Value.absent(),
                Value<bool> reminder = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => TasksCompanion.insert(
                id: id,
                goalId: goalId,
                dreamId: dreamId,
                title: title,
                note: note,
                dueDate: dueDate,
                dueMinutes: dueMinutes,
                priority: priority,
                repeatRule: repeatRule,
                isCompleted: isCompleted,
                completedAt: completedAt,
                reminder: reminder,
                createdAt: createdAt,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$TasksTable, TaskItem>(table),
                  $$TasksTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({goalId = false, dreamId = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [],
              addJoins:
                  <
                    T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic
                    >
                  >(state) {
                    if (goalId) {
                      state =
                          state.withJoin(
                                currentTable: table,
                                currentColumn: table.goalId,
                                referencedTable: $$TasksTableReferences
                                    ._goalIdTable(db),
                                referencedColumn: $$TasksTableReferences
                                    ._goalIdTable(db)
                                    .id,
                              )
                              as T;
                    }
                    if (dreamId) {
                      state =
                          state.withJoin(
                                currentTable: table,
                                currentColumn: table.dreamId,
                                referencedTable: $$TasksTableReferences
                                    ._dreamIdTable(db),
                                referencedColumn: $$TasksTableReferences
                                    ._dreamIdTable(db)
                                    .id,
                              )
                              as T;
                    }

                    return state;
                  },
              getPrefetchedDataCallback: (items) async {
                return [];
              },
            );
          },
        ),
      );
}

typedef $$TasksTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $TasksTable,
      TaskItem,
      $$TasksTableFilterComposer,
      $$TasksTableOrderingComposer,
      $$TasksTableAnnotationComposer,
      $$TasksTableCreateCompanionBuilder,
      $$TasksTableUpdateCompanionBuilder,
      (TaskItem, $$TasksTableReferences),
      TaskItem,
      PrefetchHooks Function({bool goalId, bool dreamId})
    >;
typedef $$HabitsTableCreateCompanionBuilder =
    HabitsCompanion Function({
      Value<int> id,
      required String title,
      Value<String> iconKey,
      required int color,
      Value<int> weekdaysMask,
      Value<int?> reminderMinutes,
      Value<int?> challengeDays,
      Value<int?> dreamId,
      Value<DateTime> startDate,
      Value<DateTime?> archivedAt,
      Value<DateTime> createdAt,
    });
typedef $$HabitsTableUpdateCompanionBuilder =
    HabitsCompanion Function({
      Value<int> id,
      Value<String> title,
      Value<String> iconKey,
      Value<int> color,
      Value<int> weekdaysMask,
      Value<int?> reminderMinutes,
      Value<int?> challengeDays,
      Value<int?> dreamId,
      Value<DateTime> startDate,
      Value<DateTime?> archivedAt,
      Value<DateTime> createdAt,
    });

final class $$HabitsTableReferences
    extends BaseReferences<_$AppDatabase, $HabitsTable, Habit> {
  $$HabitsTableReferences(super.$_db, super.$_table, super.$_typedResult);

  static $DreamsTable _dreamIdTable(_$AppDatabase db) =>
      db.dreams.createAlias('habits__dream_id__dreams__id');

  $$DreamsTableProcessedTableManager? get dreamId {
    final $_column = $_itemColumn<int>('dream_id');
    if ($_column == null) return null;
    final manager = $$DreamsTableTableManager(
      $_db,
      $_db.dreams,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_dreamIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }

  static MultiTypedResultKey<$HabitCompletionsTable, List<HabitCompletion>>
  _habitCompletionsRefsTable(_$AppDatabase db) => MultiTypedResultKey.fromTable(
    db.habitCompletions,
    aliasName: 'habits__id__habit_completions__habit_id',
  );

  $$HabitCompletionsTableProcessedTableManager get habitCompletionsRefs {
    final manager = $$HabitCompletionsTableTableManager(
      $_db,
      $_db.habitCompletions,
    ).filter((f) => f.habitId.id.sqlEquals($_itemColumn<int>('id')!));

    final cache = $_typedResult.readTableOrNull(
      _habitCompletionsRefsTable($_db),
    );
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }
}

class $$HabitsTableFilterComposer
    extends Composer<_$AppDatabase, $HabitsTable> {
  $$HabitsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get iconKey => $composableBuilder(
    column: $table.iconKey,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get color => $composableBuilder(
    column: $table.color,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get weekdaysMask => $composableBuilder(
    column: $table.weekdaysMask,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get reminderMinutes => $composableBuilder(
    column: $table.reminderMinutes,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get challengeDays => $composableBuilder(
    column: $table.challengeDays,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get startDate => $composableBuilder(
    column: $table.startDate,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get archivedAt => $composableBuilder(
    column: $table.archivedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  $$DreamsTableFilterComposer get dreamId {
    final $$DreamsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.dreamId,
      referencedTable: $db.dreams,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamsTableFilterComposer(
            $db: $db,
            $table: $db.dreams,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  Expression<bool> habitCompletionsRefs(
    Expression<bool> Function($$HabitCompletionsTableFilterComposer f) f,
  ) {
    final $$HabitCompletionsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.habitCompletions,
      getReferencedColumn: (t) => t.habitId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$HabitCompletionsTableFilterComposer(
            $db: $db,
            $table: $db.habitCompletions,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }
}

class $$HabitsTableOrderingComposer
    extends Composer<_$AppDatabase, $HabitsTable> {
  $$HabitsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get iconKey => $composableBuilder(
    column: $table.iconKey,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get color => $composableBuilder(
    column: $table.color,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get weekdaysMask => $composableBuilder(
    column: $table.weekdaysMask,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get reminderMinutes => $composableBuilder(
    column: $table.reminderMinutes,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get challengeDays => $composableBuilder(
    column: $table.challengeDays,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get startDate => $composableBuilder(
    column: $table.startDate,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get archivedAt => $composableBuilder(
    column: $table.archivedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  $$DreamsTableOrderingComposer get dreamId {
    final $$DreamsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.dreamId,
      referencedTable: $db.dreams,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamsTableOrderingComposer(
            $db: $db,
            $table: $db.dreams,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$HabitsTableAnnotationComposer
    extends Composer<_$AppDatabase, $HabitsTable> {
  $$HabitsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get title =>
      $composableBuilder(column: $table.title, builder: (column) => column);

  GeneratedColumn<String> get iconKey =>
      $composableBuilder(column: $table.iconKey, builder: (column) => column);

  GeneratedColumn<int> get color =>
      $composableBuilder(column: $table.color, builder: (column) => column);

  GeneratedColumn<int> get weekdaysMask => $composableBuilder(
    column: $table.weekdaysMask,
    builder: (column) => column,
  );

  GeneratedColumn<int> get reminderMinutes => $composableBuilder(
    column: $table.reminderMinutes,
    builder: (column) => column,
  );

  GeneratedColumn<int> get challengeDays => $composableBuilder(
    column: $table.challengeDays,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get startDate =>
      $composableBuilder(column: $table.startDate, builder: (column) => column);

  GeneratedColumn<DateTime> get archivedAt => $composableBuilder(
    column: $table.archivedAt,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  $$DreamsTableAnnotationComposer get dreamId {
    final $$DreamsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.dreamId,
      referencedTable: $db.dreams,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamsTableAnnotationComposer(
            $db: $db,
            $table: $db.dreams,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  Expression<T> habitCompletionsRefs<T extends Object>(
    Expression<T> Function($$HabitCompletionsTableAnnotationComposer a) f,
  ) {
    final $$HabitCompletionsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.habitCompletions,
      getReferencedColumn: (t) => t.habitId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$HabitCompletionsTableAnnotationComposer(
            $db: $db,
            $table: $db.habitCompletions,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }
}

class $$HabitsTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $HabitsTable,
          Habit,
          $$HabitsTableFilterComposer,
          $$HabitsTableOrderingComposer,
          $$HabitsTableAnnotationComposer,
          $$HabitsTableCreateCompanionBuilder,
          $$HabitsTableUpdateCompanionBuilder,
          (Habit, $$HabitsTableReferences),
          Habit,
          PrefetchHooks Function({bool dreamId, bool habitCompletionsRefs})
        > {
  $$HabitsTableTableManager(_$AppDatabase db, $HabitsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$HabitsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$HabitsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$HabitsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> title = const Value.absent(),
                Value<String> iconKey = const Value.absent(),
                Value<int> color = const Value.absent(),
                Value<int> weekdaysMask = const Value.absent(),
                Value<int?> reminderMinutes = const Value.absent(),
                Value<int?> challengeDays = const Value.absent(),
                Value<int?> dreamId = const Value.absent(),
                Value<DateTime> startDate = const Value.absent(),
                Value<DateTime?> archivedAt = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => HabitsCompanion(
                id: id,
                title: title,
                iconKey: iconKey,
                color: color,
                weekdaysMask: weekdaysMask,
                reminderMinutes: reminderMinutes,
                challengeDays: challengeDays,
                dreamId: dreamId,
                startDate: startDate,
                archivedAt: archivedAt,
                createdAt: createdAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required String title,
                Value<String> iconKey = const Value.absent(),
                required int color,
                Value<int> weekdaysMask = const Value.absent(),
                Value<int?> reminderMinutes = const Value.absent(),
                Value<int?> challengeDays = const Value.absent(),
                Value<int?> dreamId = const Value.absent(),
                Value<DateTime> startDate = const Value.absent(),
                Value<DateTime?> archivedAt = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => HabitsCompanion.insert(
                id: id,
                title: title,
                iconKey: iconKey,
                color: color,
                weekdaysMask: weekdaysMask,
                reminderMinutes: reminderMinutes,
                challengeDays: challengeDays,
                dreamId: dreamId,
                startDate: startDate,
                archivedAt: archivedAt,
                createdAt: createdAt,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$HabitsTable, Habit>(table),
                  $$HabitsTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback:
              ({dreamId = false, habitCompletionsRefs = false}) {
                return PrefetchHooks(
                  db: db,
                  explicitlyWatchedTables: [
                    if (habitCompletionsRefs) db.habitCompletions,
                  ],
                  addJoins:
                      <
                        T extends TableManagerState<
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic
                        >
                      >(state) {
                        if (dreamId) {
                          state =
                              state.withJoin(
                                    currentTable: table,
                                    currentColumn: table.dreamId,
                                    referencedTable: $$HabitsTableReferences
                                        ._dreamIdTable(db),
                                    referencedColumn: $$HabitsTableReferences
                                        ._dreamIdTable(db)
                                        .id,
                                  )
                                  as T;
                        }

                        return state;
                      },
                  getPrefetchedDataCallback: (items) async {
                    return [
                      if (habitCompletionsRefs)
                        await $_getPrefetchedDataByColumns<
                          Habit,
                          $HabitsTable,
                          HabitCompletion,
                          $HabitCompletionsTable
                        >(
                          currentTable: table,
                          referencedTable: $$HabitsTableReferences
                              ._habitCompletionsRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$HabitsTableReferences(
                                db,
                                table,
                                p0,
                              ).habitCompletionsRefs,
                          currentColumn: 'id',
                          referencedColumn: 'habit_id',
                          typedResults: items,
                        ),
                    ];
                  },
                );
              },
        ),
      );
}

typedef $$HabitsTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $HabitsTable,
      Habit,
      $$HabitsTableFilterComposer,
      $$HabitsTableOrderingComposer,
      $$HabitsTableAnnotationComposer,
      $$HabitsTableCreateCompanionBuilder,
      $$HabitsTableUpdateCompanionBuilder,
      (Habit, $$HabitsTableReferences),
      Habit,
      PrefetchHooks Function({bool dreamId, bool habitCompletionsRefs})
    >;
typedef $$HabitCompletionsTableCreateCompanionBuilder =
    HabitCompletionsCompanion Function({
      Value<int> id,
      required int habitId,
      required DateTime day,
      Value<DateTime> createdAt,
    });
typedef $$HabitCompletionsTableUpdateCompanionBuilder =
    HabitCompletionsCompanion Function({
      Value<int> id,
      Value<int> habitId,
      Value<DateTime> day,
      Value<DateTime> createdAt,
    });

final class $$HabitCompletionsTableReferences
    extends
        BaseReferences<_$AppDatabase, $HabitCompletionsTable, HabitCompletion> {
  $$HabitCompletionsTableReferences(
    super.$_db,
    super.$_table,
    super.$_typedResult,
  );

  static $HabitsTable _habitIdTable(_$AppDatabase db) =>
      db.habits.createAlias('habit_completions__habit_id__habits__id');

  $$HabitsTableProcessedTableManager get habitId {
    final $_column = $_itemColumn<int>('habit_id')!;

    final manager = $$HabitsTableTableManager(
      $_db,
      $_db.habits,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_habitIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }
}

class $$HabitCompletionsTableFilterComposer
    extends Composer<_$AppDatabase, $HabitCompletionsTable> {
  $$HabitCompletionsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get day => $composableBuilder(
    column: $table.day,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  $$HabitsTableFilterComposer get habitId {
    final $$HabitsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.habitId,
      referencedTable: $db.habits,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$HabitsTableFilterComposer(
            $db: $db,
            $table: $db.habits,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$HabitCompletionsTableOrderingComposer
    extends Composer<_$AppDatabase, $HabitCompletionsTable> {
  $$HabitCompletionsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get day => $composableBuilder(
    column: $table.day,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  $$HabitsTableOrderingComposer get habitId {
    final $$HabitsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.habitId,
      referencedTable: $db.habits,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$HabitsTableOrderingComposer(
            $db: $db,
            $table: $db.habits,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$HabitCompletionsTableAnnotationComposer
    extends Composer<_$AppDatabase, $HabitCompletionsTable> {
  $$HabitCompletionsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<DateTime> get day =>
      $composableBuilder(column: $table.day, builder: (column) => column);

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  $$HabitsTableAnnotationComposer get habitId {
    final $$HabitsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.habitId,
      referencedTable: $db.habits,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$HabitsTableAnnotationComposer(
            $db: $db,
            $table: $db.habits,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$HabitCompletionsTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $HabitCompletionsTable,
          HabitCompletion,
          $$HabitCompletionsTableFilterComposer,
          $$HabitCompletionsTableOrderingComposer,
          $$HabitCompletionsTableAnnotationComposer,
          $$HabitCompletionsTableCreateCompanionBuilder,
          $$HabitCompletionsTableUpdateCompanionBuilder,
          (HabitCompletion, $$HabitCompletionsTableReferences),
          HabitCompletion,
          PrefetchHooks Function({bool habitId})
        > {
  $$HabitCompletionsTableTableManager(
    _$AppDatabase db,
    $HabitCompletionsTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$HabitCompletionsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$HabitCompletionsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$HabitCompletionsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<int> habitId = const Value.absent(),
                Value<DateTime> day = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => HabitCompletionsCompanion(
                id: id,
                habitId: habitId,
                day: day,
                createdAt: createdAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required int habitId,
                required DateTime day,
                Value<DateTime> createdAt = const Value.absent(),
              }) => HabitCompletionsCompanion.insert(
                id: id,
                habitId: habitId,
                day: day,
                createdAt: createdAt,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$HabitCompletionsTable, HabitCompletion>(table),
                  $$HabitCompletionsTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({habitId = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [],
              addJoins:
                  <
                    T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic
                    >
                  >(state) {
                    if (habitId) {
                      state =
                          state.withJoin(
                                currentTable: table,
                                currentColumn: table.habitId,
                                referencedTable:
                                    $$HabitCompletionsTableReferences
                                        ._habitIdTable(db),
                                referencedColumn:
                                    $$HabitCompletionsTableReferences
                                        ._habitIdTable(db)
                                        .id,
                              )
                              as T;
                    }

                    return state;
                  },
              getPrefetchedDataCallback: (items) async {
                return [];
              },
            );
          },
        ),
      );
}

typedef $$HabitCompletionsTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $HabitCompletionsTable,
      HabitCompletion,
      $$HabitCompletionsTableFilterComposer,
      $$HabitCompletionsTableOrderingComposer,
      $$HabitCompletionsTableAnnotationComposer,
      $$HabitCompletionsTableCreateCompanionBuilder,
      $$HabitCompletionsTableUpdateCompanionBuilder,
      (HabitCompletion, $$HabitCompletionsTableReferences),
      HabitCompletion,
      PrefetchHooks Function({bool habitId})
    >;
typedef $$SavingsGoalsTableCreateCompanionBuilder =
    SavingsGoalsCompanion Function({
      Value<int> id,
      required String title,
      Value<int?> dreamId,
      required int targetMinor,
      Value<String> currency,
      Value<int?> monthlyTargetMinor,
      Value<DateTime?> targetDate,
      Value<bool> isCompleted,
      Value<DateTime?> archivedAt,
      Value<DateTime> createdAt,
    });
typedef $$SavingsGoalsTableUpdateCompanionBuilder =
    SavingsGoalsCompanion Function({
      Value<int> id,
      Value<String> title,
      Value<int?> dreamId,
      Value<int> targetMinor,
      Value<String> currency,
      Value<int?> monthlyTargetMinor,
      Value<DateTime?> targetDate,
      Value<bool> isCompleted,
      Value<DateTime?> archivedAt,
      Value<DateTime> createdAt,
    });

final class $$SavingsGoalsTableReferences
    extends BaseReferences<_$AppDatabase, $SavingsGoalsTable, SavingsGoal> {
  $$SavingsGoalsTableReferences(super.$_db, super.$_table, super.$_typedResult);

  static $DreamsTable _dreamIdTable(_$AppDatabase db) =>
      db.dreams.createAlias('savings_goals__dream_id__dreams__id');

  $$DreamsTableProcessedTableManager? get dreamId {
    final $_column = $_itemColumn<int>('dream_id');
    if ($_column == null) return null;
    final manager = $$DreamsTableTableManager(
      $_db,
      $_db.dreams,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_dreamIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }

  static MultiTypedResultKey<
    $SavingsTransactionsTable,
    List<SavingsTransaction>
  >
  _savingsTransactionsRefsTable(_$AppDatabase db) =>
      MultiTypedResultKey.fromTable(
        db.savingsTransactions,
        aliasName: 'savings_goals__id__savings_transactions__savings_goal_id',
      );

  $$SavingsTransactionsTableProcessedTableManager get savingsTransactionsRefs {
    final manager = $$SavingsTransactionsTableTableManager(
      $_db,
      $_db.savingsTransactions,
    ).filter((f) => f.savingsGoalId.id.sqlEquals($_itemColumn<int>('id')!));

    final cache = $_typedResult.readTableOrNull(
      _savingsTransactionsRefsTable($_db),
    );
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }
}

class $$SavingsGoalsTableFilterComposer
    extends Composer<_$AppDatabase, $SavingsGoalsTable> {
  $$SavingsGoalsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get targetMinor => $composableBuilder(
    column: $table.targetMinor,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get currency => $composableBuilder(
    column: $table.currency,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get monthlyTargetMinor => $composableBuilder(
    column: $table.monthlyTargetMinor,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get targetDate => $composableBuilder(
    column: $table.targetDate,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get isCompleted => $composableBuilder(
    column: $table.isCompleted,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get archivedAt => $composableBuilder(
    column: $table.archivedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  $$DreamsTableFilterComposer get dreamId {
    final $$DreamsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.dreamId,
      referencedTable: $db.dreams,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamsTableFilterComposer(
            $db: $db,
            $table: $db.dreams,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  Expression<bool> savingsTransactionsRefs(
    Expression<bool> Function($$SavingsTransactionsTableFilterComposer f) f,
  ) {
    final $$SavingsTransactionsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.savingsTransactions,
      getReferencedColumn: (t) => t.savingsGoalId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SavingsTransactionsTableFilterComposer(
            $db: $db,
            $table: $db.savingsTransactions,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }
}

class $$SavingsGoalsTableOrderingComposer
    extends Composer<_$AppDatabase, $SavingsGoalsTable> {
  $$SavingsGoalsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get targetMinor => $composableBuilder(
    column: $table.targetMinor,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get currency => $composableBuilder(
    column: $table.currency,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get monthlyTargetMinor => $composableBuilder(
    column: $table.monthlyTargetMinor,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get targetDate => $composableBuilder(
    column: $table.targetDate,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get isCompleted => $composableBuilder(
    column: $table.isCompleted,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get archivedAt => $composableBuilder(
    column: $table.archivedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  $$DreamsTableOrderingComposer get dreamId {
    final $$DreamsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.dreamId,
      referencedTable: $db.dreams,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamsTableOrderingComposer(
            $db: $db,
            $table: $db.dreams,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$SavingsGoalsTableAnnotationComposer
    extends Composer<_$AppDatabase, $SavingsGoalsTable> {
  $$SavingsGoalsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get title =>
      $composableBuilder(column: $table.title, builder: (column) => column);

  GeneratedColumn<int> get targetMinor => $composableBuilder(
    column: $table.targetMinor,
    builder: (column) => column,
  );

  GeneratedColumn<String> get currency =>
      $composableBuilder(column: $table.currency, builder: (column) => column);

  GeneratedColumn<int> get monthlyTargetMinor => $composableBuilder(
    column: $table.monthlyTargetMinor,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get targetDate => $composableBuilder(
    column: $table.targetDate,
    builder: (column) => column,
  );

  GeneratedColumn<bool> get isCompleted => $composableBuilder(
    column: $table.isCompleted,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get archivedAt => $composableBuilder(
    column: $table.archivedAt,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  $$DreamsTableAnnotationComposer get dreamId {
    final $$DreamsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.dreamId,
      referencedTable: $db.dreams,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamsTableAnnotationComposer(
            $db: $db,
            $table: $db.dreams,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  Expression<T> savingsTransactionsRefs<T extends Object>(
    Expression<T> Function($$SavingsTransactionsTableAnnotationComposer a) f,
  ) {
    final $$SavingsTransactionsTableAnnotationComposer composer =
        $composerBuilder(
          composer: this,
          getCurrentColumn: (t) => t.id,
          referencedTable: $db.savingsTransactions,
          getReferencedColumn: (t) => t.savingsGoalId,
          builder:
              (
                joinBuilder, {
                $addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer,
              }) => $$SavingsTransactionsTableAnnotationComposer(
                $db: $db,
                $table: $db.savingsTransactions,
                $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
                joinBuilder: joinBuilder,
                $removeJoinBuilderFromRootComposer:
                    $removeJoinBuilderFromRootComposer,
              ),
        );
    return f(composer);
  }
}

class $$SavingsGoalsTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $SavingsGoalsTable,
          SavingsGoal,
          $$SavingsGoalsTableFilterComposer,
          $$SavingsGoalsTableOrderingComposer,
          $$SavingsGoalsTableAnnotationComposer,
          $$SavingsGoalsTableCreateCompanionBuilder,
          $$SavingsGoalsTableUpdateCompanionBuilder,
          (SavingsGoal, $$SavingsGoalsTableReferences),
          SavingsGoal,
          PrefetchHooks Function({bool dreamId, bool savingsTransactionsRefs})
        > {
  $$SavingsGoalsTableTableManager(_$AppDatabase db, $SavingsGoalsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$SavingsGoalsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$SavingsGoalsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$SavingsGoalsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> title = const Value.absent(),
                Value<int?> dreamId = const Value.absent(),
                Value<int> targetMinor = const Value.absent(),
                Value<String> currency = const Value.absent(),
                Value<int?> monthlyTargetMinor = const Value.absent(),
                Value<DateTime?> targetDate = const Value.absent(),
                Value<bool> isCompleted = const Value.absent(),
                Value<DateTime?> archivedAt = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => SavingsGoalsCompanion(
                id: id,
                title: title,
                dreamId: dreamId,
                targetMinor: targetMinor,
                currency: currency,
                monthlyTargetMinor: monthlyTargetMinor,
                targetDate: targetDate,
                isCompleted: isCompleted,
                archivedAt: archivedAt,
                createdAt: createdAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required String title,
                Value<int?> dreamId = const Value.absent(),
                required int targetMinor,
                Value<String> currency = const Value.absent(),
                Value<int?> monthlyTargetMinor = const Value.absent(),
                Value<DateTime?> targetDate = const Value.absent(),
                Value<bool> isCompleted = const Value.absent(),
                Value<DateTime?> archivedAt = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => SavingsGoalsCompanion.insert(
                id: id,
                title: title,
                dreamId: dreamId,
                targetMinor: targetMinor,
                currency: currency,
                monthlyTargetMinor: monthlyTargetMinor,
                targetDate: targetDate,
                isCompleted: isCompleted,
                archivedAt: archivedAt,
                createdAt: createdAt,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$SavingsGoalsTable, SavingsGoal>(table),
                  $$SavingsGoalsTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback:
              ({dreamId = false, savingsTransactionsRefs = false}) {
                return PrefetchHooks(
                  db: db,
                  explicitlyWatchedTables: [
                    if (savingsTransactionsRefs) db.savingsTransactions,
                  ],
                  addJoins:
                      <
                        T extends TableManagerState<
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic
                        >
                      >(state) {
                        if (dreamId) {
                          state =
                              state.withJoin(
                                    currentTable: table,
                                    currentColumn: table.dreamId,
                                    referencedTable:
                                        $$SavingsGoalsTableReferences
                                            ._dreamIdTable(db),
                                    referencedColumn:
                                        $$SavingsGoalsTableReferences
                                            ._dreamIdTable(db)
                                            .id,
                                  )
                                  as T;
                        }

                        return state;
                      },
                  getPrefetchedDataCallback: (items) async {
                    return [
                      if (savingsTransactionsRefs)
                        await $_getPrefetchedDataByColumns<
                          SavingsGoal,
                          $SavingsGoalsTable,
                          SavingsTransaction,
                          $SavingsTransactionsTable
                        >(
                          currentTable: table,
                          referencedTable: $$SavingsGoalsTableReferences
                              ._savingsTransactionsRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$SavingsGoalsTableReferences(
                                db,
                                table,
                                p0,
                              ).savingsTransactionsRefs,
                          currentColumn: 'id',
                          referencedColumn: 'savings_goal_id',
                          typedResults: items,
                        ),
                    ];
                  },
                );
              },
        ),
      );
}

typedef $$SavingsGoalsTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $SavingsGoalsTable,
      SavingsGoal,
      $$SavingsGoalsTableFilterComposer,
      $$SavingsGoalsTableOrderingComposer,
      $$SavingsGoalsTableAnnotationComposer,
      $$SavingsGoalsTableCreateCompanionBuilder,
      $$SavingsGoalsTableUpdateCompanionBuilder,
      (SavingsGoal, $$SavingsGoalsTableReferences),
      SavingsGoal,
      PrefetchHooks Function({bool dreamId, bool savingsTransactionsRefs})
    >;
typedef $$SavingsTransactionsTableCreateCompanionBuilder =
    SavingsTransactionsCompanion Function({
      Value<int> id,
      Value<int?> savingsGoalId,
      required String kind,
      required int amountMinor,
      Value<String> currency,
      Value<int?> originalAmountMinor,
      Value<String?> originalCurrency,
      Value<String?> manualRate,
      Value<String> note,
      required DateTime date,
      Value<DateTime> createdAt,
    });
typedef $$SavingsTransactionsTableUpdateCompanionBuilder =
    SavingsTransactionsCompanion Function({
      Value<int> id,
      Value<int?> savingsGoalId,
      Value<String> kind,
      Value<int> amountMinor,
      Value<String> currency,
      Value<int?> originalAmountMinor,
      Value<String?> originalCurrency,
      Value<String?> manualRate,
      Value<String> note,
      Value<DateTime> date,
      Value<DateTime> createdAt,
    });

final class $$SavingsTransactionsTableReferences
    extends
        BaseReferences<
          _$AppDatabase,
          $SavingsTransactionsTable,
          SavingsTransaction
        > {
  $$SavingsTransactionsTableReferences(
    super.$_db,
    super.$_table,
    super.$_typedResult,
  );

  static $SavingsGoalsTable _savingsGoalIdTable(_$AppDatabase db) => db
      .savingsGoals
      .createAlias('savings_transactions__savings_goal_id__savings_goals__id');

  $$SavingsGoalsTableProcessedTableManager? get savingsGoalId {
    final $_column = $_itemColumn<int>('savings_goal_id');
    if ($_column == null) return null;
    final manager = $$SavingsGoalsTableTableManager(
      $_db,
      $_db.savingsGoals,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_savingsGoalIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }
}

class $$SavingsTransactionsTableFilterComposer
    extends Composer<_$AppDatabase, $SavingsTransactionsTable> {
  $$SavingsTransactionsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get amountMinor => $composableBuilder(
    column: $table.amountMinor,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get currency => $composableBuilder(
    column: $table.currency,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get originalAmountMinor => $composableBuilder(
    column: $table.originalAmountMinor,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get originalCurrency => $composableBuilder(
    column: $table.originalCurrency,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get manualRate => $composableBuilder(
    column: $table.manualRate,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get note => $composableBuilder(
    column: $table.note,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get date => $composableBuilder(
    column: $table.date,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  $$SavingsGoalsTableFilterComposer get savingsGoalId {
    final $$SavingsGoalsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.savingsGoalId,
      referencedTable: $db.savingsGoals,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SavingsGoalsTableFilterComposer(
            $db: $db,
            $table: $db.savingsGoals,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$SavingsTransactionsTableOrderingComposer
    extends Composer<_$AppDatabase, $SavingsTransactionsTable> {
  $$SavingsTransactionsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get amountMinor => $composableBuilder(
    column: $table.amountMinor,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get currency => $composableBuilder(
    column: $table.currency,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get originalAmountMinor => $composableBuilder(
    column: $table.originalAmountMinor,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get originalCurrency => $composableBuilder(
    column: $table.originalCurrency,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get manualRate => $composableBuilder(
    column: $table.manualRate,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get note => $composableBuilder(
    column: $table.note,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get date => $composableBuilder(
    column: $table.date,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  $$SavingsGoalsTableOrderingComposer get savingsGoalId {
    final $$SavingsGoalsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.savingsGoalId,
      referencedTable: $db.savingsGoals,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SavingsGoalsTableOrderingComposer(
            $db: $db,
            $table: $db.savingsGoals,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$SavingsTransactionsTableAnnotationComposer
    extends Composer<_$AppDatabase, $SavingsTransactionsTable> {
  $$SavingsTransactionsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get kind =>
      $composableBuilder(column: $table.kind, builder: (column) => column);

  GeneratedColumn<int> get amountMinor => $composableBuilder(
    column: $table.amountMinor,
    builder: (column) => column,
  );

  GeneratedColumn<String> get currency =>
      $composableBuilder(column: $table.currency, builder: (column) => column);

  GeneratedColumn<int> get originalAmountMinor => $composableBuilder(
    column: $table.originalAmountMinor,
    builder: (column) => column,
  );

  GeneratedColumn<String> get originalCurrency => $composableBuilder(
    column: $table.originalCurrency,
    builder: (column) => column,
  );

  GeneratedColumn<String> get manualRate => $composableBuilder(
    column: $table.manualRate,
    builder: (column) => column,
  );

  GeneratedColumn<String> get note =>
      $composableBuilder(column: $table.note, builder: (column) => column);

  GeneratedColumn<DateTime> get date =>
      $composableBuilder(column: $table.date, builder: (column) => column);

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  $$SavingsGoalsTableAnnotationComposer get savingsGoalId {
    final $$SavingsGoalsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.savingsGoalId,
      referencedTable: $db.savingsGoals,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SavingsGoalsTableAnnotationComposer(
            $db: $db,
            $table: $db.savingsGoals,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$SavingsTransactionsTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $SavingsTransactionsTable,
          SavingsTransaction,
          $$SavingsTransactionsTableFilterComposer,
          $$SavingsTransactionsTableOrderingComposer,
          $$SavingsTransactionsTableAnnotationComposer,
          $$SavingsTransactionsTableCreateCompanionBuilder,
          $$SavingsTransactionsTableUpdateCompanionBuilder,
          (SavingsTransaction, $$SavingsTransactionsTableReferences),
          SavingsTransaction,
          PrefetchHooks Function({bool savingsGoalId})
        > {
  $$SavingsTransactionsTableTableManager(
    _$AppDatabase db,
    $SavingsTransactionsTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$SavingsTransactionsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$SavingsTransactionsTableOrderingComposer(
                $db: db,
                $table: table,
              ),
          createComputedFieldComposer: () =>
              $$SavingsTransactionsTableAnnotationComposer(
                $db: db,
                $table: table,
              ),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<int?> savingsGoalId = const Value.absent(),
                Value<String> kind = const Value.absent(),
                Value<int> amountMinor = const Value.absent(),
                Value<String> currency = const Value.absent(),
                Value<int?> originalAmountMinor = const Value.absent(),
                Value<String?> originalCurrency = const Value.absent(),
                Value<String?> manualRate = const Value.absent(),
                Value<String> note = const Value.absent(),
                Value<DateTime> date = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => SavingsTransactionsCompanion(
                id: id,
                savingsGoalId: savingsGoalId,
                kind: kind,
                amountMinor: amountMinor,
                currency: currency,
                originalAmountMinor: originalAmountMinor,
                originalCurrency: originalCurrency,
                manualRate: manualRate,
                note: note,
                date: date,
                createdAt: createdAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<int?> savingsGoalId = const Value.absent(),
                required String kind,
                required int amountMinor,
                Value<String> currency = const Value.absent(),
                Value<int?> originalAmountMinor = const Value.absent(),
                Value<String?> originalCurrency = const Value.absent(),
                Value<String?> manualRate = const Value.absent(),
                Value<String> note = const Value.absent(),
                required DateTime date,
                Value<DateTime> createdAt = const Value.absent(),
              }) => SavingsTransactionsCompanion.insert(
                id: id,
                savingsGoalId: savingsGoalId,
                kind: kind,
                amountMinor: amountMinor,
                currency: currency,
                originalAmountMinor: originalAmountMinor,
                originalCurrency: originalCurrency,
                manualRate: manualRate,
                note: note,
                date: date,
                createdAt: createdAt,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$SavingsTransactionsTable, SavingsTransaction>(
                    table,
                  ),
                  $$SavingsTransactionsTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({savingsGoalId = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [],
              addJoins:
                  <
                    T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic
                    >
                  >(state) {
                    if (savingsGoalId) {
                      state =
                          state.withJoin(
                                currentTable: table,
                                currentColumn: table.savingsGoalId,
                                referencedTable:
                                    $$SavingsTransactionsTableReferences
                                        ._savingsGoalIdTable(db),
                                referencedColumn:
                                    $$SavingsTransactionsTableReferences
                                        ._savingsGoalIdTable(db)
                                        .id,
                              )
                              as T;
                    }

                    return state;
                  },
              getPrefetchedDataCallback: (items) async {
                return [];
              },
            );
          },
        ),
      );
}

typedef $$SavingsTransactionsTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $SavingsTransactionsTable,
      SavingsTransaction,
      $$SavingsTransactionsTableFilterComposer,
      $$SavingsTransactionsTableOrderingComposer,
      $$SavingsTransactionsTableAnnotationComposer,
      $$SavingsTransactionsTableCreateCompanionBuilder,
      $$SavingsTransactionsTableUpdateCompanionBuilder,
      (SavingsTransaction, $$SavingsTransactionsTableReferences),
      SavingsTransaction,
      PrefetchHooks Function({bool savingsGoalId})
    >;
typedef $$JournalEntriesTableCreateCompanionBuilder =
    JournalEntriesCompanion Function({
      Value<int> id,
      required DateTime date,
      Value<String> title,
      Value<String> body,
      Value<int> mood,
      Value<String> tags,
      Value<bool> isImportant,
      Value<bool> isPrivate,
      Value<DateTime> createdAt,
      Value<DateTime> updatedAt,
    });
typedef $$JournalEntriesTableUpdateCompanionBuilder =
    JournalEntriesCompanion Function({
      Value<int> id,
      Value<DateTime> date,
      Value<String> title,
      Value<String> body,
      Value<int> mood,
      Value<String> tags,
      Value<bool> isImportant,
      Value<bool> isPrivate,
      Value<DateTime> createdAt,
      Value<DateTime> updatedAt,
    });

final class $$JournalEntriesTableReferences
    extends BaseReferences<_$AppDatabase, $JournalEntriesTable, JournalEntry> {
  $$JournalEntriesTableReferences(
    super.$_db,
    super.$_table,
    super.$_typedResult,
  );

  static MultiTypedResultKey<$JournalMediaTable, List<JournalMediaItem>>
  _journalMediaRefsTable(_$AppDatabase db) => MultiTypedResultKey.fromTable(
    db.journalMedia,
    aliasName: 'journal_entries__id__journal_media__entry_id',
  );

  $$JournalMediaTableProcessedTableManager get journalMediaRefs {
    final manager = $$JournalMediaTableTableManager(
      $_db,
      $_db.journalMedia,
    ).filter((f) => f.entryId.id.sqlEquals($_itemColumn<int>('id')!));

    final cache = $_typedResult.readTableOrNull(_journalMediaRefsTable($_db));
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }
}

class $$JournalEntriesTableFilterComposer
    extends Composer<_$AppDatabase, $JournalEntriesTable> {
  $$JournalEntriesTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get date => $composableBuilder(
    column: $table.date,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get body => $composableBuilder(
    column: $table.body,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get mood => $composableBuilder(
    column: $table.mood,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get tags => $composableBuilder(
    column: $table.tags,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get isImportant => $composableBuilder(
    column: $table.isImportant,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get isPrivate => $composableBuilder(
    column: $table.isPrivate,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnFilters(column),
  );

  Expression<bool> journalMediaRefs(
    Expression<bool> Function($$JournalMediaTableFilterComposer f) f,
  ) {
    final $$JournalMediaTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.journalMedia,
      getReferencedColumn: (t) => t.entryId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$JournalMediaTableFilterComposer(
            $db: $db,
            $table: $db.journalMedia,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }
}

class $$JournalEntriesTableOrderingComposer
    extends Composer<_$AppDatabase, $JournalEntriesTable> {
  $$JournalEntriesTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get date => $composableBuilder(
    column: $table.date,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get body => $composableBuilder(
    column: $table.body,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get mood => $composableBuilder(
    column: $table.mood,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get tags => $composableBuilder(
    column: $table.tags,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get isImportant => $composableBuilder(
    column: $table.isImportant,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get isPrivate => $composableBuilder(
    column: $table.isPrivate,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$JournalEntriesTableAnnotationComposer
    extends Composer<_$AppDatabase, $JournalEntriesTable> {
  $$JournalEntriesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<DateTime> get date =>
      $composableBuilder(column: $table.date, builder: (column) => column);

  GeneratedColumn<String> get title =>
      $composableBuilder(column: $table.title, builder: (column) => column);

  GeneratedColumn<String> get body =>
      $composableBuilder(column: $table.body, builder: (column) => column);

  GeneratedColumn<int> get mood =>
      $composableBuilder(column: $table.mood, builder: (column) => column);

  GeneratedColumn<String> get tags =>
      $composableBuilder(column: $table.tags, builder: (column) => column);

  GeneratedColumn<bool> get isImportant => $composableBuilder(
    column: $table.isImportant,
    builder: (column) => column,
  );

  GeneratedColumn<bool> get isPrivate =>
      $composableBuilder(column: $table.isPrivate, builder: (column) => column);

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);

  Expression<T> journalMediaRefs<T extends Object>(
    Expression<T> Function($$JournalMediaTableAnnotationComposer a) f,
  ) {
    final $$JournalMediaTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.journalMedia,
      getReferencedColumn: (t) => t.entryId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$JournalMediaTableAnnotationComposer(
            $db: $db,
            $table: $db.journalMedia,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }
}

class $$JournalEntriesTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $JournalEntriesTable,
          JournalEntry,
          $$JournalEntriesTableFilterComposer,
          $$JournalEntriesTableOrderingComposer,
          $$JournalEntriesTableAnnotationComposer,
          $$JournalEntriesTableCreateCompanionBuilder,
          $$JournalEntriesTableUpdateCompanionBuilder,
          (JournalEntry, $$JournalEntriesTableReferences),
          JournalEntry,
          PrefetchHooks Function({bool journalMediaRefs})
        > {
  $$JournalEntriesTableTableManager(
    _$AppDatabase db,
    $JournalEntriesTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$JournalEntriesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$JournalEntriesTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$JournalEntriesTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<DateTime> date = const Value.absent(),
                Value<String> title = const Value.absent(),
                Value<String> body = const Value.absent(),
                Value<int> mood = const Value.absent(),
                Value<String> tags = const Value.absent(),
                Value<bool> isImportant = const Value.absent(),
                Value<bool> isPrivate = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
              }) => JournalEntriesCompanion(
                id: id,
                date: date,
                title: title,
                body: body,
                mood: mood,
                tags: tags,
                isImportant: isImportant,
                isPrivate: isPrivate,
                createdAt: createdAt,
                updatedAt: updatedAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required DateTime date,
                Value<String> title = const Value.absent(),
                Value<String> body = const Value.absent(),
                Value<int> mood = const Value.absent(),
                Value<String> tags = const Value.absent(),
                Value<bool> isImportant = const Value.absent(),
                Value<bool> isPrivate = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
              }) => JournalEntriesCompanion.insert(
                id: id,
                date: date,
                title: title,
                body: body,
                mood: mood,
                tags: tags,
                isImportant: isImportant,
                isPrivate: isPrivate,
                createdAt: createdAt,
                updatedAt: updatedAt,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$JournalEntriesTable, JournalEntry>(table),
                  $$JournalEntriesTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({journalMediaRefs = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [if (journalMediaRefs) db.journalMedia],
              addJoins: null,
              getPrefetchedDataCallback: (items) async {
                return [
                  if (journalMediaRefs)
                    await $_getPrefetchedDataByColumns<
                      JournalEntry,
                      $JournalEntriesTable,
                      JournalMediaItem,
                      $JournalMediaTable
                    >(
                      currentTable: table,
                      referencedTable: $$JournalEntriesTableReferences
                          ._journalMediaRefsTable(db),
                      managerFromTypedResult: (p0) =>
                          $$JournalEntriesTableReferences(
                            db,
                            table,
                            p0,
                          ).journalMediaRefs,
                      currentColumn: 'id',
                      referencedColumn: 'entry_id',
                      typedResults: items,
                    ),
                ];
              },
            );
          },
        ),
      );
}

typedef $$JournalEntriesTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $JournalEntriesTable,
      JournalEntry,
      $$JournalEntriesTableFilterComposer,
      $$JournalEntriesTableOrderingComposer,
      $$JournalEntriesTableAnnotationComposer,
      $$JournalEntriesTableCreateCompanionBuilder,
      $$JournalEntriesTableUpdateCompanionBuilder,
      (JournalEntry, $$JournalEntriesTableReferences),
      JournalEntry,
      PrefetchHooks Function({bool journalMediaRefs})
    >;
typedef $$TimeCapsulesTableCreateCompanionBuilder =
    TimeCapsulesCompanion Function({
      Value<int> id,
      required String title,
      Value<String> letter,
      Value<String> dreamsSnapshot,
      Value<String> goalsText,
      required DateTime openAt,
      Value<DateTime?> openedAt,
      Value<DateTime> createdAt,
    });
typedef $$TimeCapsulesTableUpdateCompanionBuilder =
    TimeCapsulesCompanion Function({
      Value<int> id,
      Value<String> title,
      Value<String> letter,
      Value<String> dreamsSnapshot,
      Value<String> goalsText,
      Value<DateTime> openAt,
      Value<DateTime?> openedAt,
      Value<DateTime> createdAt,
    });

final class $$TimeCapsulesTableReferences
    extends BaseReferences<_$AppDatabase, $TimeCapsulesTable, TimeCapsule> {
  $$TimeCapsulesTableReferences(super.$_db, super.$_table, super.$_typedResult);

  static MultiTypedResultKey<$JournalMediaTable, List<JournalMediaItem>>
  _journalMediaRefsTable(_$AppDatabase db) => MultiTypedResultKey.fromTable(
    db.journalMedia,
    aliasName: 'time_capsules__id__journal_media__capsule_id',
  );

  $$JournalMediaTableProcessedTableManager get journalMediaRefs {
    final manager = $$JournalMediaTableTableManager(
      $_db,
      $_db.journalMedia,
    ).filter((f) => f.capsuleId.id.sqlEquals($_itemColumn<int>('id')!));

    final cache = $_typedResult.readTableOrNull(_journalMediaRefsTable($_db));
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }
}

class $$TimeCapsulesTableFilterComposer
    extends Composer<_$AppDatabase, $TimeCapsulesTable> {
  $$TimeCapsulesTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get letter => $composableBuilder(
    column: $table.letter,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get dreamsSnapshot => $composableBuilder(
    column: $table.dreamsSnapshot,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get goalsText => $composableBuilder(
    column: $table.goalsText,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get openAt => $composableBuilder(
    column: $table.openAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get openedAt => $composableBuilder(
    column: $table.openedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  Expression<bool> journalMediaRefs(
    Expression<bool> Function($$JournalMediaTableFilterComposer f) f,
  ) {
    final $$JournalMediaTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.journalMedia,
      getReferencedColumn: (t) => t.capsuleId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$JournalMediaTableFilterComposer(
            $db: $db,
            $table: $db.journalMedia,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }
}

class $$TimeCapsulesTableOrderingComposer
    extends Composer<_$AppDatabase, $TimeCapsulesTable> {
  $$TimeCapsulesTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get letter => $composableBuilder(
    column: $table.letter,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get dreamsSnapshot => $composableBuilder(
    column: $table.dreamsSnapshot,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get goalsText => $composableBuilder(
    column: $table.goalsText,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get openAt => $composableBuilder(
    column: $table.openAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get openedAt => $composableBuilder(
    column: $table.openedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$TimeCapsulesTableAnnotationComposer
    extends Composer<_$AppDatabase, $TimeCapsulesTable> {
  $$TimeCapsulesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get title =>
      $composableBuilder(column: $table.title, builder: (column) => column);

  GeneratedColumn<String> get letter =>
      $composableBuilder(column: $table.letter, builder: (column) => column);

  GeneratedColumn<String> get dreamsSnapshot => $composableBuilder(
    column: $table.dreamsSnapshot,
    builder: (column) => column,
  );

  GeneratedColumn<String> get goalsText =>
      $composableBuilder(column: $table.goalsText, builder: (column) => column);

  GeneratedColumn<DateTime> get openAt =>
      $composableBuilder(column: $table.openAt, builder: (column) => column);

  GeneratedColumn<DateTime> get openedAt =>
      $composableBuilder(column: $table.openedAt, builder: (column) => column);

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  Expression<T> journalMediaRefs<T extends Object>(
    Expression<T> Function($$JournalMediaTableAnnotationComposer a) f,
  ) {
    final $$JournalMediaTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.journalMedia,
      getReferencedColumn: (t) => t.capsuleId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$JournalMediaTableAnnotationComposer(
            $db: $db,
            $table: $db.journalMedia,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }
}

class $$TimeCapsulesTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $TimeCapsulesTable,
          TimeCapsule,
          $$TimeCapsulesTableFilterComposer,
          $$TimeCapsulesTableOrderingComposer,
          $$TimeCapsulesTableAnnotationComposer,
          $$TimeCapsulesTableCreateCompanionBuilder,
          $$TimeCapsulesTableUpdateCompanionBuilder,
          (TimeCapsule, $$TimeCapsulesTableReferences),
          TimeCapsule,
          PrefetchHooks Function({bool journalMediaRefs})
        > {
  $$TimeCapsulesTableTableManager(_$AppDatabase db, $TimeCapsulesTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$TimeCapsulesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$TimeCapsulesTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$TimeCapsulesTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> title = const Value.absent(),
                Value<String> letter = const Value.absent(),
                Value<String> dreamsSnapshot = const Value.absent(),
                Value<String> goalsText = const Value.absent(),
                Value<DateTime> openAt = const Value.absent(),
                Value<DateTime?> openedAt = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => TimeCapsulesCompanion(
                id: id,
                title: title,
                letter: letter,
                dreamsSnapshot: dreamsSnapshot,
                goalsText: goalsText,
                openAt: openAt,
                openedAt: openedAt,
                createdAt: createdAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required String title,
                Value<String> letter = const Value.absent(),
                Value<String> dreamsSnapshot = const Value.absent(),
                Value<String> goalsText = const Value.absent(),
                required DateTime openAt,
                Value<DateTime?> openedAt = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => TimeCapsulesCompanion.insert(
                id: id,
                title: title,
                letter: letter,
                dreamsSnapshot: dreamsSnapshot,
                goalsText: goalsText,
                openAt: openAt,
                openedAt: openedAt,
                createdAt: createdAt,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$TimeCapsulesTable, TimeCapsule>(table),
                  $$TimeCapsulesTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({journalMediaRefs = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [if (journalMediaRefs) db.journalMedia],
              addJoins: null,
              getPrefetchedDataCallback: (items) async {
                return [
                  if (journalMediaRefs)
                    await $_getPrefetchedDataByColumns<
                      TimeCapsule,
                      $TimeCapsulesTable,
                      JournalMediaItem,
                      $JournalMediaTable
                    >(
                      currentTable: table,
                      referencedTable: $$TimeCapsulesTableReferences
                          ._journalMediaRefsTable(db),
                      managerFromTypedResult: (p0) =>
                          $$TimeCapsulesTableReferences(
                            db,
                            table,
                            p0,
                          ).journalMediaRefs,
                      currentColumn: 'id',
                      referencedColumn: 'capsule_id',
                      typedResults: items,
                    ),
                ];
              },
            );
          },
        ),
      );
}

typedef $$TimeCapsulesTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $TimeCapsulesTable,
      TimeCapsule,
      $$TimeCapsulesTableFilterComposer,
      $$TimeCapsulesTableOrderingComposer,
      $$TimeCapsulesTableAnnotationComposer,
      $$TimeCapsulesTableCreateCompanionBuilder,
      $$TimeCapsulesTableUpdateCompanionBuilder,
      (TimeCapsule, $$TimeCapsulesTableReferences),
      TimeCapsule,
      PrefetchHooks Function({bool journalMediaRefs})
    >;
typedef $$JournalMediaTableCreateCompanionBuilder =
    JournalMediaCompanion Function({
      Value<int> id,
      Value<int?> entryId,
      Value<int?> capsuleId,
      required String kind,
      required String path,
      Value<int?> durationMs,
      Value<DateTime> createdAt,
    });
typedef $$JournalMediaTableUpdateCompanionBuilder =
    JournalMediaCompanion Function({
      Value<int> id,
      Value<int?> entryId,
      Value<int?> capsuleId,
      Value<String> kind,
      Value<String> path,
      Value<int?> durationMs,
      Value<DateTime> createdAt,
    });

final class $$JournalMediaTableReferences
    extends
        BaseReferences<_$AppDatabase, $JournalMediaTable, JournalMediaItem> {
  $$JournalMediaTableReferences(super.$_db, super.$_table, super.$_typedResult);

  static $JournalEntriesTable _entryIdTable(_$AppDatabase db) => db
      .journalEntries
      .createAlias('journal_media__entry_id__journal_entries__id');

  $$JournalEntriesTableProcessedTableManager? get entryId {
    final $_column = $_itemColumn<int>('entry_id');
    if ($_column == null) return null;
    final manager = $$JournalEntriesTableTableManager(
      $_db,
      $_db.journalEntries,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_entryIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }

  static $TimeCapsulesTable _capsuleIdTable(_$AppDatabase db) => db.timeCapsules
      .createAlias('journal_media__capsule_id__time_capsules__id');

  $$TimeCapsulesTableProcessedTableManager? get capsuleId {
    final $_column = $_itemColumn<int>('capsule_id');
    if ($_column == null) return null;
    final manager = $$TimeCapsulesTableTableManager(
      $_db,
      $_db.timeCapsules,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_capsuleIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }
}

class $$JournalMediaTableFilterComposer
    extends Composer<_$AppDatabase, $JournalMediaTable> {
  $$JournalMediaTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get path => $composableBuilder(
    column: $table.path,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get durationMs => $composableBuilder(
    column: $table.durationMs,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  $$JournalEntriesTableFilterComposer get entryId {
    final $$JournalEntriesTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.entryId,
      referencedTable: $db.journalEntries,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$JournalEntriesTableFilterComposer(
            $db: $db,
            $table: $db.journalEntries,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$TimeCapsulesTableFilterComposer get capsuleId {
    final $$TimeCapsulesTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.capsuleId,
      referencedTable: $db.timeCapsules,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$TimeCapsulesTableFilterComposer(
            $db: $db,
            $table: $db.timeCapsules,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$JournalMediaTableOrderingComposer
    extends Composer<_$AppDatabase, $JournalMediaTable> {
  $$JournalMediaTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get path => $composableBuilder(
    column: $table.path,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get durationMs => $composableBuilder(
    column: $table.durationMs,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  $$JournalEntriesTableOrderingComposer get entryId {
    final $$JournalEntriesTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.entryId,
      referencedTable: $db.journalEntries,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$JournalEntriesTableOrderingComposer(
            $db: $db,
            $table: $db.journalEntries,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$TimeCapsulesTableOrderingComposer get capsuleId {
    final $$TimeCapsulesTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.capsuleId,
      referencedTable: $db.timeCapsules,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$TimeCapsulesTableOrderingComposer(
            $db: $db,
            $table: $db.timeCapsules,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$JournalMediaTableAnnotationComposer
    extends Composer<_$AppDatabase, $JournalMediaTable> {
  $$JournalMediaTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get kind =>
      $composableBuilder(column: $table.kind, builder: (column) => column);

  GeneratedColumn<String> get path =>
      $composableBuilder(column: $table.path, builder: (column) => column);

  GeneratedColumn<int> get durationMs => $composableBuilder(
    column: $table.durationMs,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  $$JournalEntriesTableAnnotationComposer get entryId {
    final $$JournalEntriesTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.entryId,
      referencedTable: $db.journalEntries,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$JournalEntriesTableAnnotationComposer(
            $db: $db,
            $table: $db.journalEntries,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$TimeCapsulesTableAnnotationComposer get capsuleId {
    final $$TimeCapsulesTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.capsuleId,
      referencedTable: $db.timeCapsules,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$TimeCapsulesTableAnnotationComposer(
            $db: $db,
            $table: $db.timeCapsules,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$JournalMediaTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $JournalMediaTable,
          JournalMediaItem,
          $$JournalMediaTableFilterComposer,
          $$JournalMediaTableOrderingComposer,
          $$JournalMediaTableAnnotationComposer,
          $$JournalMediaTableCreateCompanionBuilder,
          $$JournalMediaTableUpdateCompanionBuilder,
          (JournalMediaItem, $$JournalMediaTableReferences),
          JournalMediaItem,
          PrefetchHooks Function({bool entryId, bool capsuleId})
        > {
  $$JournalMediaTableTableManager(_$AppDatabase db, $JournalMediaTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$JournalMediaTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$JournalMediaTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$JournalMediaTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<int?> entryId = const Value.absent(),
                Value<int?> capsuleId = const Value.absent(),
                Value<String> kind = const Value.absent(),
                Value<String> path = const Value.absent(),
                Value<int?> durationMs = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => JournalMediaCompanion(
                id: id,
                entryId: entryId,
                capsuleId: capsuleId,
                kind: kind,
                path: path,
                durationMs: durationMs,
                createdAt: createdAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<int?> entryId = const Value.absent(),
                Value<int?> capsuleId = const Value.absent(),
                required String kind,
                required String path,
                Value<int?> durationMs = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => JournalMediaCompanion.insert(
                id: id,
                entryId: entryId,
                capsuleId: capsuleId,
                kind: kind,
                path: path,
                durationMs: durationMs,
                createdAt: createdAt,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$JournalMediaTable, JournalMediaItem>(table),
                  $$JournalMediaTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({entryId = false, capsuleId = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [],
              addJoins:
                  <
                    T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic
                    >
                  >(state) {
                    if (entryId) {
                      state =
                          state.withJoin(
                                currentTable: table,
                                currentColumn: table.entryId,
                                referencedTable: $$JournalMediaTableReferences
                                    ._entryIdTable(db),
                                referencedColumn: $$JournalMediaTableReferences
                                    ._entryIdTable(db)
                                    .id,
                              )
                              as T;
                    }
                    if (capsuleId) {
                      state =
                          state.withJoin(
                                currentTable: table,
                                currentColumn: table.capsuleId,
                                referencedTable: $$JournalMediaTableReferences
                                    ._capsuleIdTable(db),
                                referencedColumn: $$JournalMediaTableReferences
                                    ._capsuleIdTable(db)
                                    .id,
                              )
                              as T;
                    }

                    return state;
                  },
              getPrefetchedDataCallback: (items) async {
                return [];
              },
            );
          },
        ),
      );
}

typedef $$JournalMediaTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $JournalMediaTable,
      JournalMediaItem,
      $$JournalMediaTableFilterComposer,
      $$JournalMediaTableOrderingComposer,
      $$JournalMediaTableAnnotationComposer,
      $$JournalMediaTableCreateCompanionBuilder,
      $$JournalMediaTableUpdateCompanionBuilder,
      (JournalMediaItem, $$JournalMediaTableReferences),
      JournalMediaItem,
      PrefetchHooks Function({bool entryId, bool capsuleId})
    >;
typedef $$GardenTreesTableCreateCompanionBuilder =
    GardenTreesCompanion Function({
      Value<int> id,
      required int dreamId,
      required String plantType,
      Value<int> stage,
      required int slot,
      Value<DateTime> plantedAt,
      Value<DateTime?> lastGrowthAt,
    });
typedef $$GardenTreesTableUpdateCompanionBuilder =
    GardenTreesCompanion Function({
      Value<int> id,
      Value<int> dreamId,
      Value<String> plantType,
      Value<int> stage,
      Value<int> slot,
      Value<DateTime> plantedAt,
      Value<DateTime?> lastGrowthAt,
    });

final class $$GardenTreesTableReferences
    extends BaseReferences<_$AppDatabase, $GardenTreesTable, GardenTree> {
  $$GardenTreesTableReferences(super.$_db, super.$_table, super.$_typedResult);

  static $DreamsTable _dreamIdTable(_$AppDatabase db) =>
      db.dreams.createAlias('garden_trees__dream_id__dreams__id');

  $$DreamsTableProcessedTableManager get dreamId {
    final $_column = $_itemColumn<int>('dream_id')!;

    final manager = $$DreamsTableTableManager(
      $_db,
      $_db.dreams,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_dreamIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }
}

class $$GardenTreesTableFilterComposer
    extends Composer<_$AppDatabase, $GardenTreesTable> {
  $$GardenTreesTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get plantType => $composableBuilder(
    column: $table.plantType,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get stage => $composableBuilder(
    column: $table.stage,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get slot => $composableBuilder(
    column: $table.slot,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get plantedAt => $composableBuilder(
    column: $table.plantedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get lastGrowthAt => $composableBuilder(
    column: $table.lastGrowthAt,
    builder: (column) => ColumnFilters(column),
  );

  $$DreamsTableFilterComposer get dreamId {
    final $$DreamsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.dreamId,
      referencedTable: $db.dreams,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamsTableFilterComposer(
            $db: $db,
            $table: $db.dreams,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$GardenTreesTableOrderingComposer
    extends Composer<_$AppDatabase, $GardenTreesTable> {
  $$GardenTreesTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get plantType => $composableBuilder(
    column: $table.plantType,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get stage => $composableBuilder(
    column: $table.stage,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get slot => $composableBuilder(
    column: $table.slot,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get plantedAt => $composableBuilder(
    column: $table.plantedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get lastGrowthAt => $composableBuilder(
    column: $table.lastGrowthAt,
    builder: (column) => ColumnOrderings(column),
  );

  $$DreamsTableOrderingComposer get dreamId {
    final $$DreamsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.dreamId,
      referencedTable: $db.dreams,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamsTableOrderingComposer(
            $db: $db,
            $table: $db.dreams,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$GardenTreesTableAnnotationComposer
    extends Composer<_$AppDatabase, $GardenTreesTable> {
  $$GardenTreesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get plantType =>
      $composableBuilder(column: $table.plantType, builder: (column) => column);

  GeneratedColumn<int> get stage =>
      $composableBuilder(column: $table.stage, builder: (column) => column);

  GeneratedColumn<int> get slot =>
      $composableBuilder(column: $table.slot, builder: (column) => column);

  GeneratedColumn<DateTime> get plantedAt =>
      $composableBuilder(column: $table.plantedAt, builder: (column) => column);

  GeneratedColumn<DateTime> get lastGrowthAt => $composableBuilder(
    column: $table.lastGrowthAt,
    builder: (column) => column,
  );

  $$DreamsTableAnnotationComposer get dreamId {
    final $$DreamsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.dreamId,
      referencedTable: $db.dreams,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamsTableAnnotationComposer(
            $db: $db,
            $table: $db.dreams,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$GardenTreesTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $GardenTreesTable,
          GardenTree,
          $$GardenTreesTableFilterComposer,
          $$GardenTreesTableOrderingComposer,
          $$GardenTreesTableAnnotationComposer,
          $$GardenTreesTableCreateCompanionBuilder,
          $$GardenTreesTableUpdateCompanionBuilder,
          (GardenTree, $$GardenTreesTableReferences),
          GardenTree,
          PrefetchHooks Function({bool dreamId})
        > {
  $$GardenTreesTableTableManager(_$AppDatabase db, $GardenTreesTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$GardenTreesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$GardenTreesTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$GardenTreesTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<int> dreamId = const Value.absent(),
                Value<String> plantType = const Value.absent(),
                Value<int> stage = const Value.absent(),
                Value<int> slot = const Value.absent(),
                Value<DateTime> plantedAt = const Value.absent(),
                Value<DateTime?> lastGrowthAt = const Value.absent(),
              }) => GardenTreesCompanion(
                id: id,
                dreamId: dreamId,
                plantType: plantType,
                stage: stage,
                slot: slot,
                plantedAt: plantedAt,
                lastGrowthAt: lastGrowthAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required int dreamId,
                required String plantType,
                Value<int> stage = const Value.absent(),
                required int slot,
                Value<DateTime> plantedAt = const Value.absent(),
                Value<DateTime?> lastGrowthAt = const Value.absent(),
              }) => GardenTreesCompanion.insert(
                id: id,
                dreamId: dreamId,
                plantType: plantType,
                stage: stage,
                slot: slot,
                plantedAt: plantedAt,
                lastGrowthAt: lastGrowthAt,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$GardenTreesTable, GardenTree>(table),
                  $$GardenTreesTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({dreamId = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [],
              addJoins:
                  <
                    T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic
                    >
                  >(state) {
                    if (dreamId) {
                      state =
                          state.withJoin(
                                currentTable: table,
                                currentColumn: table.dreamId,
                                referencedTable: $$GardenTreesTableReferences
                                    ._dreamIdTable(db),
                                referencedColumn: $$GardenTreesTableReferences
                                    ._dreamIdTable(db)
                                    .id,
                              )
                              as T;
                    }

                    return state;
                  },
              getPrefetchedDataCallback: (items) async {
                return [];
              },
            );
          },
        ),
      );
}

typedef $$GardenTreesTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $GardenTreesTable,
      GardenTree,
      $$GardenTreesTableFilterComposer,
      $$GardenTreesTableOrderingComposer,
      $$GardenTreesTableAnnotationComposer,
      $$GardenTreesTableCreateCompanionBuilder,
      $$GardenTreesTableUpdateCompanionBuilder,
      (GardenTree, $$GardenTreesTableReferences),
      GardenTree,
      PrefetchHooks Function({bool dreamId})
    >;
typedef $$AchievementsTableCreateCompanionBuilder =
    AchievementsCompanion Function({
      Value<int> id,
      required String code,
      Value<DateTime> unlockedAt,
    });
typedef $$AchievementsTableUpdateCompanionBuilder =
    AchievementsCompanion Function({
      Value<int> id,
      Value<String> code,
      Value<DateTime> unlockedAt,
    });

class $$AchievementsTableFilterComposer
    extends Composer<_$AppDatabase, $AchievementsTable> {
  $$AchievementsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get code => $composableBuilder(
    column: $table.code,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get unlockedAt => $composableBuilder(
    column: $table.unlockedAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $$AchievementsTableOrderingComposer
    extends Composer<_$AppDatabase, $AchievementsTable> {
  $$AchievementsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get code => $composableBuilder(
    column: $table.code,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get unlockedAt => $composableBuilder(
    column: $table.unlockedAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$AchievementsTableAnnotationComposer
    extends Composer<_$AppDatabase, $AchievementsTable> {
  $$AchievementsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get code =>
      $composableBuilder(column: $table.code, builder: (column) => column);

  GeneratedColumn<DateTime> get unlockedAt => $composableBuilder(
    column: $table.unlockedAt,
    builder: (column) => column,
  );
}

class $$AchievementsTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $AchievementsTable,
          Achievement,
          $$AchievementsTableFilterComposer,
          $$AchievementsTableOrderingComposer,
          $$AchievementsTableAnnotationComposer,
          $$AchievementsTableCreateCompanionBuilder,
          $$AchievementsTableUpdateCompanionBuilder,
          (
            Achievement,
            BaseReferences<_$AppDatabase, $AchievementsTable, Achievement>,
          ),
          Achievement,
          PrefetchHooks Function()
        > {
  $$AchievementsTableTableManager(_$AppDatabase db, $AchievementsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$AchievementsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$AchievementsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$AchievementsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> code = const Value.absent(),
                Value<DateTime> unlockedAt = const Value.absent(),
              }) => AchievementsCompanion(
                id: id,
                code: code,
                unlockedAt: unlockedAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required String code,
                Value<DateTime> unlockedAt = const Value.absent(),
              }) => AchievementsCompanion.insert(
                id: id,
                code: code,
                unlockedAt: unlockedAt,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$AchievementsTable, Achievement>(table),
                  BaseReferences<
                    _$AppDatabase,
                    $AchievementsTable,
                    Achievement
                  >(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$AchievementsTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $AchievementsTable,
      Achievement,
      $$AchievementsTableFilterComposer,
      $$AchievementsTableOrderingComposer,
      $$AchievementsTableAnnotationComposer,
      $$AchievementsTableCreateCompanionBuilder,
      $$AchievementsTableUpdateCompanionBuilder,
      (
        Achievement,
        BaseReferences<_$AppDatabase, $AchievementsTable, Achievement>,
      ),
      Achievement,
      PrefetchHooks Function()
    >;
typedef $$FocusSessionsTableCreateCompanionBuilder =
    FocusSessionsCompanion Function({
      Value<int> id,
      required DateTime startedAt,
      required DateTime endedAt,
      required int plannedSeconds,
      required int actualSeconds,
      Value<String> kind,
      Value<bool> completed,
      Value<int?> goalId,
      Value<int?> dreamId,
    });
typedef $$FocusSessionsTableUpdateCompanionBuilder =
    FocusSessionsCompanion Function({
      Value<int> id,
      Value<DateTime> startedAt,
      Value<DateTime> endedAt,
      Value<int> plannedSeconds,
      Value<int> actualSeconds,
      Value<String> kind,
      Value<bool> completed,
      Value<int?> goalId,
      Value<int?> dreamId,
    });

final class $$FocusSessionsTableReferences
    extends BaseReferences<_$AppDatabase, $FocusSessionsTable, FocusSession> {
  $$FocusSessionsTableReferences(
    super.$_db,
    super.$_table,
    super.$_typedResult,
  );

  static $GoalsTable _goalIdTable(_$AppDatabase db) =>
      db.goals.createAlias('focus_sessions__goal_id__goals__id');

  $$GoalsTableProcessedTableManager? get goalId {
    final $_column = $_itemColumn<int>('goal_id');
    if ($_column == null) return null;
    final manager = $$GoalsTableTableManager(
      $_db,
      $_db.goals,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_goalIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }

  static $DreamsTable _dreamIdTable(_$AppDatabase db) =>
      db.dreams.createAlias('focus_sessions__dream_id__dreams__id');

  $$DreamsTableProcessedTableManager? get dreamId {
    final $_column = $_itemColumn<int>('dream_id');
    if ($_column == null) return null;
    final manager = $$DreamsTableTableManager(
      $_db,
      $_db.dreams,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_dreamIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }
}

class $$FocusSessionsTableFilterComposer
    extends Composer<_$AppDatabase, $FocusSessionsTable> {
  $$FocusSessionsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get startedAt => $composableBuilder(
    column: $table.startedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get endedAt => $composableBuilder(
    column: $table.endedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get plannedSeconds => $composableBuilder(
    column: $table.plannedSeconds,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get actualSeconds => $composableBuilder(
    column: $table.actualSeconds,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get completed => $composableBuilder(
    column: $table.completed,
    builder: (column) => ColumnFilters(column),
  );

  $$GoalsTableFilterComposer get goalId {
    final $$GoalsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.goalId,
      referencedTable: $db.goals,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$GoalsTableFilterComposer(
            $db: $db,
            $table: $db.goals,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$DreamsTableFilterComposer get dreamId {
    final $$DreamsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.dreamId,
      referencedTable: $db.dreams,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamsTableFilterComposer(
            $db: $db,
            $table: $db.dreams,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$FocusSessionsTableOrderingComposer
    extends Composer<_$AppDatabase, $FocusSessionsTable> {
  $$FocusSessionsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get startedAt => $composableBuilder(
    column: $table.startedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get endedAt => $composableBuilder(
    column: $table.endedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get plannedSeconds => $composableBuilder(
    column: $table.plannedSeconds,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get actualSeconds => $composableBuilder(
    column: $table.actualSeconds,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get completed => $composableBuilder(
    column: $table.completed,
    builder: (column) => ColumnOrderings(column),
  );

  $$GoalsTableOrderingComposer get goalId {
    final $$GoalsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.goalId,
      referencedTable: $db.goals,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$GoalsTableOrderingComposer(
            $db: $db,
            $table: $db.goals,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$DreamsTableOrderingComposer get dreamId {
    final $$DreamsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.dreamId,
      referencedTable: $db.dreams,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamsTableOrderingComposer(
            $db: $db,
            $table: $db.dreams,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$FocusSessionsTableAnnotationComposer
    extends Composer<_$AppDatabase, $FocusSessionsTable> {
  $$FocusSessionsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<DateTime> get startedAt =>
      $composableBuilder(column: $table.startedAt, builder: (column) => column);

  GeneratedColumn<DateTime> get endedAt =>
      $composableBuilder(column: $table.endedAt, builder: (column) => column);

  GeneratedColumn<int> get plannedSeconds => $composableBuilder(
    column: $table.plannedSeconds,
    builder: (column) => column,
  );

  GeneratedColumn<int> get actualSeconds => $composableBuilder(
    column: $table.actualSeconds,
    builder: (column) => column,
  );

  GeneratedColumn<String> get kind =>
      $composableBuilder(column: $table.kind, builder: (column) => column);

  GeneratedColumn<bool> get completed =>
      $composableBuilder(column: $table.completed, builder: (column) => column);

  $$GoalsTableAnnotationComposer get goalId {
    final $$GoalsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.goalId,
      referencedTable: $db.goals,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$GoalsTableAnnotationComposer(
            $db: $db,
            $table: $db.goals,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$DreamsTableAnnotationComposer get dreamId {
    final $$DreamsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.dreamId,
      referencedTable: $db.dreams,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DreamsTableAnnotationComposer(
            $db: $db,
            $table: $db.dreams,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$FocusSessionsTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $FocusSessionsTable,
          FocusSession,
          $$FocusSessionsTableFilterComposer,
          $$FocusSessionsTableOrderingComposer,
          $$FocusSessionsTableAnnotationComposer,
          $$FocusSessionsTableCreateCompanionBuilder,
          $$FocusSessionsTableUpdateCompanionBuilder,
          (FocusSession, $$FocusSessionsTableReferences),
          FocusSession,
          PrefetchHooks Function({bool goalId, bool dreamId})
        > {
  $$FocusSessionsTableTableManager(_$AppDatabase db, $FocusSessionsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$FocusSessionsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$FocusSessionsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$FocusSessionsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<DateTime> startedAt = const Value.absent(),
                Value<DateTime> endedAt = const Value.absent(),
                Value<int> plannedSeconds = const Value.absent(),
                Value<int> actualSeconds = const Value.absent(),
                Value<String> kind = const Value.absent(),
                Value<bool> completed = const Value.absent(),
                Value<int?> goalId = const Value.absent(),
                Value<int?> dreamId = const Value.absent(),
              }) => FocusSessionsCompanion(
                id: id,
                startedAt: startedAt,
                endedAt: endedAt,
                plannedSeconds: plannedSeconds,
                actualSeconds: actualSeconds,
                kind: kind,
                completed: completed,
                goalId: goalId,
                dreamId: dreamId,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required DateTime startedAt,
                required DateTime endedAt,
                required int plannedSeconds,
                required int actualSeconds,
                Value<String> kind = const Value.absent(),
                Value<bool> completed = const Value.absent(),
                Value<int?> goalId = const Value.absent(),
                Value<int?> dreamId = const Value.absent(),
              }) => FocusSessionsCompanion.insert(
                id: id,
                startedAt: startedAt,
                endedAt: endedAt,
                plannedSeconds: plannedSeconds,
                actualSeconds: actualSeconds,
                kind: kind,
                completed: completed,
                goalId: goalId,
                dreamId: dreamId,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$FocusSessionsTable, FocusSession>(table),
                  $$FocusSessionsTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({goalId = false, dreamId = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [],
              addJoins:
                  <
                    T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic
                    >
                  >(state) {
                    if (goalId) {
                      state =
                          state.withJoin(
                                currentTable: table,
                                currentColumn: table.goalId,
                                referencedTable: $$FocusSessionsTableReferences
                                    ._goalIdTable(db),
                                referencedColumn: $$FocusSessionsTableReferences
                                    ._goalIdTable(db)
                                    .id,
                              )
                              as T;
                    }
                    if (dreamId) {
                      state =
                          state.withJoin(
                                currentTable: table,
                                currentColumn: table.dreamId,
                                referencedTable: $$FocusSessionsTableReferences
                                    ._dreamIdTable(db),
                                referencedColumn: $$FocusSessionsTableReferences
                                    ._dreamIdTable(db)
                                    .id,
                              )
                              as T;
                    }

                    return state;
                  },
              getPrefetchedDataCallback: (items) async {
                return [];
              },
            );
          },
        ),
      );
}

typedef $$FocusSessionsTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $FocusSessionsTable,
      FocusSession,
      $$FocusSessionsTableFilterComposer,
      $$FocusSessionsTableOrderingComposer,
      $$FocusSessionsTableAnnotationComposer,
      $$FocusSessionsTableCreateCompanionBuilder,
      $$FocusSessionsTableUpdateCompanionBuilder,
      (FocusSession, $$FocusSessionsTableReferences),
      FocusSession,
      PrefetchHooks Function({bool goalId, bool dreamId})
    >;
typedef $$LifeBalanceEntriesTableCreateCompanionBuilder =
    LifeBalanceEntriesCompanion Function({
      Value<int> id,
      required int health,
      required int finance,
      required int career,
      required int education,
      required int family,
      required int social,
      required int growth,
      required int hobbies,
      Value<String> note,
      Value<DateTime> createdAt,
    });
typedef $$LifeBalanceEntriesTableUpdateCompanionBuilder =
    LifeBalanceEntriesCompanion Function({
      Value<int> id,
      Value<int> health,
      Value<int> finance,
      Value<int> career,
      Value<int> education,
      Value<int> family,
      Value<int> social,
      Value<int> growth,
      Value<int> hobbies,
      Value<String> note,
      Value<DateTime> createdAt,
    });

class $$LifeBalanceEntriesTableFilterComposer
    extends Composer<_$AppDatabase, $LifeBalanceEntriesTable> {
  $$LifeBalanceEntriesTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get health => $composableBuilder(
    column: $table.health,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get finance => $composableBuilder(
    column: $table.finance,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get career => $composableBuilder(
    column: $table.career,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get education => $composableBuilder(
    column: $table.education,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get family => $composableBuilder(
    column: $table.family,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get social => $composableBuilder(
    column: $table.social,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get growth => $composableBuilder(
    column: $table.growth,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get hobbies => $composableBuilder(
    column: $table.hobbies,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get note => $composableBuilder(
    column: $table.note,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $$LifeBalanceEntriesTableOrderingComposer
    extends Composer<_$AppDatabase, $LifeBalanceEntriesTable> {
  $$LifeBalanceEntriesTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get health => $composableBuilder(
    column: $table.health,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get finance => $composableBuilder(
    column: $table.finance,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get career => $composableBuilder(
    column: $table.career,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get education => $composableBuilder(
    column: $table.education,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get family => $composableBuilder(
    column: $table.family,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get social => $composableBuilder(
    column: $table.social,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get growth => $composableBuilder(
    column: $table.growth,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get hobbies => $composableBuilder(
    column: $table.hobbies,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get note => $composableBuilder(
    column: $table.note,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$LifeBalanceEntriesTableAnnotationComposer
    extends Composer<_$AppDatabase, $LifeBalanceEntriesTable> {
  $$LifeBalanceEntriesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<int> get health =>
      $composableBuilder(column: $table.health, builder: (column) => column);

  GeneratedColumn<int> get finance =>
      $composableBuilder(column: $table.finance, builder: (column) => column);

  GeneratedColumn<int> get career =>
      $composableBuilder(column: $table.career, builder: (column) => column);

  GeneratedColumn<int> get education =>
      $composableBuilder(column: $table.education, builder: (column) => column);

  GeneratedColumn<int> get family =>
      $composableBuilder(column: $table.family, builder: (column) => column);

  GeneratedColumn<int> get social =>
      $composableBuilder(column: $table.social, builder: (column) => column);

  GeneratedColumn<int> get growth =>
      $composableBuilder(column: $table.growth, builder: (column) => column);

  GeneratedColumn<int> get hobbies =>
      $composableBuilder(column: $table.hobbies, builder: (column) => column);

  GeneratedColumn<String> get note =>
      $composableBuilder(column: $table.note, builder: (column) => column);

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);
}

class $$LifeBalanceEntriesTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $LifeBalanceEntriesTable,
          LifeBalanceEntry,
          $$LifeBalanceEntriesTableFilterComposer,
          $$LifeBalanceEntriesTableOrderingComposer,
          $$LifeBalanceEntriesTableAnnotationComposer,
          $$LifeBalanceEntriesTableCreateCompanionBuilder,
          $$LifeBalanceEntriesTableUpdateCompanionBuilder,
          (
            LifeBalanceEntry,
            BaseReferences<
              _$AppDatabase,
              $LifeBalanceEntriesTable,
              LifeBalanceEntry
            >,
          ),
          LifeBalanceEntry,
          PrefetchHooks Function()
        > {
  $$LifeBalanceEntriesTableTableManager(
    _$AppDatabase db,
    $LifeBalanceEntriesTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$LifeBalanceEntriesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$LifeBalanceEntriesTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$LifeBalanceEntriesTableAnnotationComposer(
                $db: db,
                $table: table,
              ),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<int> health = const Value.absent(),
                Value<int> finance = const Value.absent(),
                Value<int> career = const Value.absent(),
                Value<int> education = const Value.absent(),
                Value<int> family = const Value.absent(),
                Value<int> social = const Value.absent(),
                Value<int> growth = const Value.absent(),
                Value<int> hobbies = const Value.absent(),
                Value<String> note = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => LifeBalanceEntriesCompanion(
                id: id,
                health: health,
                finance: finance,
                career: career,
                education: education,
                family: family,
                social: social,
                growth: growth,
                hobbies: hobbies,
                note: note,
                createdAt: createdAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required int health,
                required int finance,
                required int career,
                required int education,
                required int family,
                required int social,
                required int growth,
                required int hobbies,
                Value<String> note = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => LifeBalanceEntriesCompanion.insert(
                id: id,
                health: health,
                finance: finance,
                career: career,
                education: education,
                family: family,
                social: social,
                growth: growth,
                hobbies: hobbies,
                note: note,
                createdAt: createdAt,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$LifeBalanceEntriesTable, LifeBalanceEntry>(
                    table,
                  ),
                  BaseReferences<
                    _$AppDatabase,
                    $LifeBalanceEntriesTable,
                    LifeBalanceEntry
                  >(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$LifeBalanceEntriesTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $LifeBalanceEntriesTable,
      LifeBalanceEntry,
      $$LifeBalanceEntriesTableFilterComposer,
      $$LifeBalanceEntriesTableOrderingComposer,
      $$LifeBalanceEntriesTableAnnotationComposer,
      $$LifeBalanceEntriesTableCreateCompanionBuilder,
      $$LifeBalanceEntriesTableUpdateCompanionBuilder,
      (
        LifeBalanceEntry,
        BaseReferences<
          _$AppDatabase,
          $LifeBalanceEntriesTable,
          LifeBalanceEntry
        >,
      ),
      LifeBalanceEntry,
      PrefetchHooks Function()
    >;
typedef $$RemindersTableCreateCompanionBuilder =
    RemindersCompanion Function({
      Value<int> id,
      required String kind,
      Value<int?> refId,
      required String title,
      Value<String> body,
      required DateTime at,
      Value<String> repeat,
      Value<int?> weekdaysMask,
      Value<bool> enabled,
      Value<DateTime> createdAt,
    });
typedef $$RemindersTableUpdateCompanionBuilder =
    RemindersCompanion Function({
      Value<int> id,
      Value<String> kind,
      Value<int?> refId,
      Value<String> title,
      Value<String> body,
      Value<DateTime> at,
      Value<String> repeat,
      Value<int?> weekdaysMask,
      Value<bool> enabled,
      Value<DateTime> createdAt,
    });

class $$RemindersTableFilterComposer
    extends Composer<_$AppDatabase, $RemindersTable> {
  $$RemindersTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get refId => $composableBuilder(
    column: $table.refId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get body => $composableBuilder(
    column: $table.body,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get at => $composableBuilder(
    column: $table.at,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get repeat => $composableBuilder(
    column: $table.repeat,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get weekdaysMask => $composableBuilder(
    column: $table.weekdaysMask,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get enabled => $composableBuilder(
    column: $table.enabled,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $$RemindersTableOrderingComposer
    extends Composer<_$AppDatabase, $RemindersTable> {
  $$RemindersTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get refId => $composableBuilder(
    column: $table.refId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get body => $composableBuilder(
    column: $table.body,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get at => $composableBuilder(
    column: $table.at,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get repeat => $composableBuilder(
    column: $table.repeat,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get weekdaysMask => $composableBuilder(
    column: $table.weekdaysMask,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get enabled => $composableBuilder(
    column: $table.enabled,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$RemindersTableAnnotationComposer
    extends Composer<_$AppDatabase, $RemindersTable> {
  $$RemindersTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get kind =>
      $composableBuilder(column: $table.kind, builder: (column) => column);

  GeneratedColumn<int> get refId =>
      $composableBuilder(column: $table.refId, builder: (column) => column);

  GeneratedColumn<String> get title =>
      $composableBuilder(column: $table.title, builder: (column) => column);

  GeneratedColumn<String> get body =>
      $composableBuilder(column: $table.body, builder: (column) => column);

  GeneratedColumn<DateTime> get at =>
      $composableBuilder(column: $table.at, builder: (column) => column);

  GeneratedColumn<String> get repeat =>
      $composableBuilder(column: $table.repeat, builder: (column) => column);

  GeneratedColumn<int> get weekdaysMask => $composableBuilder(
    column: $table.weekdaysMask,
    builder: (column) => column,
  );

  GeneratedColumn<bool> get enabled =>
      $composableBuilder(column: $table.enabled, builder: (column) => column);

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);
}

class $$RemindersTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $RemindersTable,
          Reminder,
          $$RemindersTableFilterComposer,
          $$RemindersTableOrderingComposer,
          $$RemindersTableAnnotationComposer,
          $$RemindersTableCreateCompanionBuilder,
          $$RemindersTableUpdateCompanionBuilder,
          (Reminder, BaseReferences<_$AppDatabase, $RemindersTable, Reminder>),
          Reminder,
          PrefetchHooks Function()
        > {
  $$RemindersTableTableManager(_$AppDatabase db, $RemindersTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$RemindersTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$RemindersTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$RemindersTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> kind = const Value.absent(),
                Value<int?> refId = const Value.absent(),
                Value<String> title = const Value.absent(),
                Value<String> body = const Value.absent(),
                Value<DateTime> at = const Value.absent(),
                Value<String> repeat = const Value.absent(),
                Value<int?> weekdaysMask = const Value.absent(),
                Value<bool> enabled = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => RemindersCompanion(
                id: id,
                kind: kind,
                refId: refId,
                title: title,
                body: body,
                at: at,
                repeat: repeat,
                weekdaysMask: weekdaysMask,
                enabled: enabled,
                createdAt: createdAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required String kind,
                Value<int?> refId = const Value.absent(),
                required String title,
                Value<String> body = const Value.absent(),
                required DateTime at,
                Value<String> repeat = const Value.absent(),
                Value<int?> weekdaysMask = const Value.absent(),
                Value<bool> enabled = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => RemindersCompanion.insert(
                id: id,
                kind: kind,
                refId: refId,
                title: title,
                body: body,
                at: at,
                repeat: repeat,
                weekdaysMask: weekdaysMask,
                enabled: enabled,
                createdAt: createdAt,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$RemindersTable, Reminder>(table),
                  BaseReferences<_$AppDatabase, $RemindersTable, Reminder>(
                    db,
                    table,
                    e,
                  ),
                ),
              )
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$RemindersTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $RemindersTable,
      Reminder,
      $$RemindersTableFilterComposer,
      $$RemindersTableOrderingComposer,
      $$RemindersTableAnnotationComposer,
      $$RemindersTableCreateCompanionBuilder,
      $$RemindersTableUpdateCompanionBuilder,
      (Reminder, BaseReferences<_$AppDatabase, $RemindersTable, Reminder>),
      Reminder,
      PrefetchHooks Function()
    >;
typedef $$ActivityLogTableCreateCompanionBuilder =
    ActivityLogCompanion Function({
      Value<int> id,
      required String type,
      Value<int?> refId,
      required String title,
      Value<int> points,
      Value<DateTime> createdAt,
    });
typedef $$ActivityLogTableUpdateCompanionBuilder =
    ActivityLogCompanion Function({
      Value<int> id,
      Value<String> type,
      Value<int?> refId,
      Value<String> title,
      Value<int> points,
      Value<DateTime> createdAt,
    });

class $$ActivityLogTableFilterComposer
    extends Composer<_$AppDatabase, $ActivityLogTable> {
  $$ActivityLogTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get type => $composableBuilder(
    column: $table.type,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get refId => $composableBuilder(
    column: $table.refId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get points => $composableBuilder(
    column: $table.points,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $$ActivityLogTableOrderingComposer
    extends Composer<_$AppDatabase, $ActivityLogTable> {
  $$ActivityLogTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get type => $composableBuilder(
    column: $table.type,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get refId => $composableBuilder(
    column: $table.refId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get points => $composableBuilder(
    column: $table.points,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$ActivityLogTableAnnotationComposer
    extends Composer<_$AppDatabase, $ActivityLogTable> {
  $$ActivityLogTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get type =>
      $composableBuilder(column: $table.type, builder: (column) => column);

  GeneratedColumn<int> get refId =>
      $composableBuilder(column: $table.refId, builder: (column) => column);

  GeneratedColumn<String> get title =>
      $composableBuilder(column: $table.title, builder: (column) => column);

  GeneratedColumn<int> get points =>
      $composableBuilder(column: $table.points, builder: (column) => column);

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);
}

class $$ActivityLogTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $ActivityLogTable,
          ActivityLogEntry,
          $$ActivityLogTableFilterComposer,
          $$ActivityLogTableOrderingComposer,
          $$ActivityLogTableAnnotationComposer,
          $$ActivityLogTableCreateCompanionBuilder,
          $$ActivityLogTableUpdateCompanionBuilder,
          (
            ActivityLogEntry,
            BaseReferences<_$AppDatabase, $ActivityLogTable, ActivityLogEntry>,
          ),
          ActivityLogEntry,
          PrefetchHooks Function()
        > {
  $$ActivityLogTableTableManager(_$AppDatabase db, $ActivityLogTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$ActivityLogTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$ActivityLogTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$ActivityLogTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> type = const Value.absent(),
                Value<int?> refId = const Value.absent(),
                Value<String> title = const Value.absent(),
                Value<int> points = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => ActivityLogCompanion(
                id: id,
                type: type,
                refId: refId,
                title: title,
                points: points,
                createdAt: createdAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required String type,
                Value<int?> refId = const Value.absent(),
                required String title,
                Value<int> points = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => ActivityLogCompanion.insert(
                id: id,
                type: type,
                refId: refId,
                title: title,
                points: points,
                createdAt: createdAt,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$ActivityLogTable, ActivityLogEntry>(table),
                  BaseReferences<
                    _$AppDatabase,
                    $ActivityLogTable,
                    ActivityLogEntry
                  >(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$ActivityLogTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $ActivityLogTable,
      ActivityLogEntry,
      $$ActivityLogTableFilterComposer,
      $$ActivityLogTableOrderingComposer,
      $$ActivityLogTableAnnotationComposer,
      $$ActivityLogTableCreateCompanionBuilder,
      $$ActivityLogTableUpdateCompanionBuilder,
      (
        ActivityLogEntry,
        BaseReferences<_$AppDatabase, $ActivityLogTable, ActivityLogEntry>,
      ),
      ActivityLogEntry,
      PrefetchHooks Function()
    >;

class $AppDatabaseManager {
  final _$AppDatabase _db;
  $AppDatabaseManager(this._db);
  $$UserProfilesTableTableManager get userProfiles =>
      $$UserProfilesTableTableManager(_db, _db.userProfiles);
  $$AppSettingsTableTableManager get appSettings =>
      $$AppSettingsTableTableManager(_db, _db.appSettings);
  $$DreamCategoriesTableTableManager get dreamCategories =>
      $$DreamCategoriesTableTableManager(_db, _db.dreamCategories);
  $$DreamsTableTableManager get dreams =>
      $$DreamsTableTableManager(_db, _db.dreams);
  $$DreamMilestonesTableTableManager get dreamMilestones =>
      $$DreamMilestonesTableTableManager(_db, _db.dreamMilestones);
  $$DreamMediaTableTableManager get dreamMedia =>
      $$DreamMediaTableTableManager(_db, _db.dreamMedia);
  $$GoalsTableTableManager get goals =>
      $$GoalsTableTableManager(_db, _db.goals);
  $$TasksTableTableManager get tasks =>
      $$TasksTableTableManager(_db, _db.tasks);
  $$HabitsTableTableManager get habits =>
      $$HabitsTableTableManager(_db, _db.habits);
  $$HabitCompletionsTableTableManager get habitCompletions =>
      $$HabitCompletionsTableTableManager(_db, _db.habitCompletions);
  $$SavingsGoalsTableTableManager get savingsGoals =>
      $$SavingsGoalsTableTableManager(_db, _db.savingsGoals);
  $$SavingsTransactionsTableTableManager get savingsTransactions =>
      $$SavingsTransactionsTableTableManager(_db, _db.savingsTransactions);
  $$JournalEntriesTableTableManager get journalEntries =>
      $$JournalEntriesTableTableManager(_db, _db.journalEntries);
  $$TimeCapsulesTableTableManager get timeCapsules =>
      $$TimeCapsulesTableTableManager(_db, _db.timeCapsules);
  $$JournalMediaTableTableManager get journalMedia =>
      $$JournalMediaTableTableManager(_db, _db.journalMedia);
  $$GardenTreesTableTableManager get gardenTrees =>
      $$GardenTreesTableTableManager(_db, _db.gardenTrees);
  $$AchievementsTableTableManager get achievements =>
      $$AchievementsTableTableManager(_db, _db.achievements);
  $$FocusSessionsTableTableManager get focusSessions =>
      $$FocusSessionsTableTableManager(_db, _db.focusSessions);
  $$LifeBalanceEntriesTableTableManager get lifeBalanceEntries =>
      $$LifeBalanceEntriesTableTableManager(_db, _db.lifeBalanceEntries);
  $$RemindersTableTableManager get reminders =>
      $$RemindersTableTableManager(_db, _db.reminders);
  $$ActivityLogTableTableManager get activityLog =>
      $$ActivityLogTableTableManager(_db, _db.activityLog);
}
