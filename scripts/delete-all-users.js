/**
 * Bulk-delete all Firebase Auth users AND their Firestore documents.
 * Run once to wipe all test accounts.
 *
 * Setup:
 *   1. Go to Firebase Console → Project Settings → Service Accounts → Generate new private key
 *   2. Save the downloaded JSON as  scripts/serviceAccountKey.json
 *   3. In this folder:  npm install firebase-admin
 *   4. node delete-all-users.js
 */

const admin = require("firebase-admin");
const serviceAccount = require("./serviceAccountKey.json");

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  projectId: "rewatt-5d719",
});

const auth = admin.auth();
const db = admin.firestore();

async function deleteAllUsers() {
  console.log("Fetching all users...");
  let pageToken;
  const uids = [];

  // Collect all UIDs (Firebase paginates at 1000)
  do {
    const result = await auth.listUsers(1000, pageToken);
    result.users.forEach((u) => uids.push(u.uid));
    pageToken = result.pageToken;
  } while (pageToken);

  if (uids.length === 0) {
    console.log("No users found. Nothing to delete.");
    return;
  }

  console.log(`Found ${uids.length} user(s). Deleting...`);

  // Delete Auth accounts in batches of 1000
  for (let i = 0; i < uids.length; i += 1000) {
    const batch = uids.slice(i, i + 1000);
    const result = await auth.deleteUsers(batch);
    console.log(`  Deleted ${result.successCount} Auth account(s).`);
    if (result.failureCount > 0) {
      result.errors.forEach((e) => console.error("  Error:", e.error.message));
    }
  }

  // Delete Firestore rewatt_users documents
  console.log("Cleaning up Firestore rewatt_users...");
  const usersSnap = await db.collection("rewatt_users").get();
  const firestoreBatch1 = db.batch();
  usersSnap.docs.forEach((doc) => firestoreBatch1.delete(doc.ref));
  await firestoreBatch1.commit();
  console.log(`  Deleted ${usersSnap.size} document(s).`);

  // Delete Firestore rewatt_usernames documents
  console.log("Cleaning up Firestore rewatt_usernames...");
  const usernamesSnap = await db.collection("rewatt_usernames").get();
  const firestoreBatch2 = db.batch();
  usernamesSnap.docs.forEach((doc) => firestoreBatch2.delete(doc.ref));
  await firestoreBatch2.commit();
  console.log(`  Deleted ${usernamesSnap.size} document(s).`);

  console.log("\nDone. All accounts and data wiped.");
}

deleteAllUsers().catch((err) => {
  console.error("Fatal error:", err);
  process.exit(1);
});
