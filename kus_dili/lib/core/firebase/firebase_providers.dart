import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'firebase_bootstrap.dart';

/// main() içinde `initFirebase()` sonucuyla geçersiz kılınır.
final firebaseStatusProvider = Provider<FirebaseStatus>(
  (ref) => throw UnimplementedError('firebaseStatusProvider override edilmeli'),
);

final firebaseAuthProvider = Provider<FirebaseAuth>(
  (ref) => FirebaseAuth.instance,
);

final firestoreProvider = Provider<FirebaseFirestore>(
  (ref) => FirebaseFirestore.instance,
);
