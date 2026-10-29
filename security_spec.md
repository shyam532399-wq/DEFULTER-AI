# Maya — Firestore Security Specification (Payload-First TDD)

## 1. Data Invariants
1. **Strict Tenant Isolation:** Every document in `/users/{userId}` and its subcollections (`documents`, `memories`, `skills`, `reminders`) belongs exclusively to `userId` (`request.auth.uid == userId` and `data.userId == userId`). Cross-user reads, queries, writes, and deletes are strictly denied.
2. **Schema & Bounds Enforcement:** Every `create` and `update` call invokes `isValid[Entity](incoming(), userId)` enforcing exact field sets (`hasAll` / `hasOnly`), field types, enums, and `.size()` limits on every string.
3. **Immutable Audit Fields:** `userId` and `createdAt` are immutable after document creation (`incoming().userId == existing().userId && incoming().createdAt == existing().createdAt`).
4. **Temporal Integrity:** `createdAt` and `updatedAt` must be valid server timestamps (`is timestamp && <= request.time`).
5. **Query Alignment:** `allow list` enforces `request.auth.uid == userId && resource.data.userId == request.auth.uid` without delegating filtering to clients.

## 2. The "Dirty Dozen" Adversarial Payloads
1. **Unauthenticated Scrape:** Unauthenticated `get`/`list` on `/users/alice_123/documents` -> `PERMISSION_DENIED`.
2. **Cross-Tenant Read (PII/Memory Leak):** `bob_456` reading `/users/alice_123` or `/users/alice_123/memories/m1` -> `PERMISSION_DENIED`.
3. **Identity Spoofing on Create:** `alice_123` writing `/users/alice_123/documents/d1` with `{"userId": "bob_456"}` -> `PERMISSION_DENIED`.
4. **Shadow / Ghost Field Injection:** Adding `"isAdmin": true` or `"isVerified": true` on `create` or `update` -> Rejected by `hasOnly` / `affectedKeys().hasOnly()`.
5. **Owner Mutation on Update:** Changing `userId` from `alice_123` to `bob_456` during `update` -> `PERMISSION_DENIED`.
6. **Creation Timestamp Tampering:** Modifying `createdAt` on `update` -> `PERMISSION_DENIED`.
7. **Future Timestamp Spoofing:** Setting `updatedAt` in year 2099 (`> request.time`) -> `PERMISSION_DENIED`.
8. **Denial-of-Wallet Oversized String:** Injecting a 50,000-char string into `title` or `bossName` -> Rejected by `.size()` bounds.
9. **ID Poisoning:** Creating a document with invalid characters or >128 chars in `{docId}` -> Rejected by `isValidId()`.
10. **Enum Poisoning:** Setting `persona` to `"jarvis"` instead of `'maya' | 'friday' | 'venom'` -> Rejected by `isValidUserProfile()`.
11. **Value Type Poisoning on Update:** Updating allowlisted field `pinned` with string `"yes"` instead of `bool` -> Rejected by `isValidBoardDocument()`.
12. **Unfiltered List Query:** Listing `/users/alice_123/documents` without `where("userId", "==", "alice_123")` when mismatched records exist or cross-user path -> Rejected.
